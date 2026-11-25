package com.relayswitch.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relayswitch.app.data.ChatMessage
import com.relayswitch.app.data.ImageAttachment
import com.relayswitch.app.data.LlmApiClient
import com.relayswitch.app.data.ProviderRepository
import com.relayswitch.app.data.RelayProvider
import com.relayswitch.app.data.SyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class ChatUiState(
    val providers: List<RelayProvider> = emptyList(),
    val activeProviderId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val pendingImage: ImageAttachment? = null,
    val isGenerating: Boolean = false,
    val syncConfig: SyncConfig = SyncConfig(),
    val statusNotice: String? = null
) {
    val activeProvider: RelayProvider?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProviderRepository(application)
    private val apiClient = LlmApiClient()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        val providers = repository.loadProviders()
        val savedActive = repository.loadActiveProviderId()
        val activeId = providers.firstOrNull { it.id == savedActive }?.id
            ?: providers.firstOrNull()?.id.orEmpty()
        _uiState.value = ChatUiState(
            providers = providers,
            activeProviderId = activeId,
            syncConfig = repository.loadSyncConfig()
        )
    }

    fun selectProvider(providerId: String) {
        repository.saveActiveProviderId(providerId)
        _uiState.update { it.copy(activeProviderId = providerId) }
    }

    fun switchModelForActiveProvider(model: String) {
        val active = _uiState.value.activeProvider ?: return
        val updated = active.copy(
            model = model,
            availableModels = (active.availableModels + model).distinct()
        )
        upsertProvider(updated)
    }

    fun upsertProvider(provider: RelayProvider) {
        val current = _uiState.value.providers.toMutableList()
        val idx = current.indexOfFirst { it.id == provider.id }
        if (idx >= 0) current[idx] = provider else current.add(provider)
        repository.saveProviders(current)
        repository.saveActiveProviderId(provider.id)
        _uiState.update {
            it.copy(providers = current, activeProviderId = provider.id)
        }
    }

    fun deleteProvider(providerId: String) {
        val remaining = _uiState.value.providers.filterNot { it.id == providerId }
        if (remaining.isEmpty()) return
        repository.saveProviders(remaining)
        val newActive = if (_uiState.value.activeProviderId == providerId) {
            remaining.first().id
        } else {
            _uiState.value.activeProviderId
        }
        repository.saveActiveProviderId(newActive)
        _uiState.update { it.copy(providers = remaining, activeProviderId = newActive) }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            val attachment = encodeImageUri(uri)
            if (attachment != null) {
                _uiState.update { it.copy(pendingImage = attachment) }
            } else {
                _uiState.update { it.copy(statusNotice = "图片读取失败，请重试") }
            }
        }
    }

    fun clearPendingImage() {
        _uiState.update { it.copy(pendingImage = null) }
    }

    fun clearChat() {
        streamJob?.cancel()
        _uiState.update { it.copy(messages = emptyList(), isGenerating = false) }
    }

    fun stopGeneration() {
        streamJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                messages = state.messages.map { it.copy(isStreaming = false) }
            )
        }
    }

    fun sendMessage(text: String) {
        val provider = _uiState.value.activeProvider ?: return
        val trimmed = text.trim()
        val image = _uiState.value.pendingImage
        if (trimmed.isEmpty() && image == null) return

        val userMsg = ChatMessage(
            role = "user",
            content = trimmed.ifEmpty { "请描述这张图片" },
            image = image
        )
        val assistantMsg = ChatMessage(
            role = "assistant",
            content = "",
            providerName = provider.name,
            modelName = provider.model,
            isStreaming = true
        )

        val historyToSend = _uiState.value.messages + userMsg
        _uiState.update {
            it.copy(
                messages = historyToSend + assistantMsg,
                pendingImage = null,
                isGenerating = true
            )
        }

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val buffer = StringBuilder()
            try {
                apiClient.streamChat(provider, historyToSend).collect { chunk ->
                    buffer.append(chunk)
                    val snapshot = buffer.toString()
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMsg.id) msg.copy(content = snapshot) else msg
                            }
                        )
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsg.id) {
                                msg.copy(
                                    content = buffer.toString().ifBlank { "（模型未返回内容）" },
                                    isStreaming = false
                                )
                            } else msg
                        }
                    )
                }
            } catch (e: Exception) {
                val errText = e.message ?: "请求异常"
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsg.id) {
                                msg.copy(
                                    content = if (buffer.isNotEmpty()) "$buffer\n\n[中断: $errText]" else errText,
                                    isStreaming = false,
                                    isError = true
                                )
                            } else msg
                        }
                    )
                }
            }
        }
    }

    fun updateSyncConfig(workerUrl: String, syncToken: String) {
        val cfg = SyncConfig(workerUrl.trim(), syncToken.trim())
        repository.saveSyncConfig(cfg)
        _uiState.update { it.copy(syncConfig = cfg) }
    }

    fun pullProvidersFromCloud() {
        val cfg = _uiState.value.syncConfig
        if (cfg.workerUrl.isBlank() || cfg.syncToken.isBlank()) {
            _uiState.update { it.copy(statusNotice = "请先填写 Cloudflare Worker 地址和同步密钥") }
            return
        }
        viewModelScope.launch {
            repository.pullFromCloud(cfg).fold(
                onSuccess = { list ->
                    repository.saveProviders(list)
                    val activeId = list.first().id
                    repository.saveActiveProviderId(activeId)
                    _uiState.update {
                        it.copy(
                            providers = list,
                            activeProviderId = activeId,
                            statusNotice = "已从云端拉取 ${list.size} 个中转站配置"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusNotice = err.message ?: "拉取失败") }
                }
            )
        }
    }

    fun pushProvidersToCloud() {
        val cfg = _uiState.value.syncConfig
        if (cfg.workerUrl.isBlank() || cfg.syncToken.isBlank()) {
            _uiState.update { it.copy(statusNotice = "请先填写 Cloudflare Worker 地址和同步密钥") }
            return
        }
        viewModelScope.launch {
            repository.pushToCloud(cfg, _uiState.value.providers).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusNotice = "已将当前中转站配置推送到云端") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusNotice = err.message ?: "推送失败") }
                }
            )
        }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(statusNotice = null) }
    }

    private suspend fun encodeImageUri(uri: Uri): ImageAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return@runCatching null

            val maxDim = 1280
            val largest = max(original.width, original.height)
            val scaled = if (largest > maxDim) {
                val ratio = maxDim.toFloat() / largest.toFloat()
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).toInt().coerceAtLeast(1),
                    (original.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            ImageAttachment(
                uriString = uri.toString(),
                base64Data = b64,
                mimeType = "image/jpeg"
            )
        }.getOrNull()
    }
}
