package com.relayswitch.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ProviderRepository(context: Context) {

    private val prefs = context.getSharedPreferences("relay_switch_prefs", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun loadProviders(): List<RelayProvider> {
        val raw = prefs.getString(KEY_PROVIDERS, null)
        if (raw.isNullOrBlank()) {
            return defaultProviders()
        }
        return parseProvidersJson(raw).ifEmpty { defaultProviders() }
    }

    fun saveProviders(providers: List<RelayProvider>) {
        prefs.edit().putString(KEY_PROVIDERS, serializeProvidersJson(providers)).apply()
    }

    fun loadActiveProviderId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun saveActiveProviderId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun loadSyncConfig(): SyncConfig {
        return SyncConfig(
            workerUrl = prefs.getString(KEY_SYNC_URL, "") ?: "",
            syncToken = prefs.getString(KEY_SYNC_TOKEN, "") ?: ""
        )
    }

    fun saveSyncConfig(config: SyncConfig) {
        prefs.edit()
            .putString(KEY_SYNC_URL, config.workerUrl.trim())
            .putString(KEY_SYNC_TOKEN, config.syncToken.trim())
            .apply()
    }

    suspend fun pullFromCloud(config: SyncConfig): Result<List<RelayProvider>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${config.workerUrl.trimEnd('/')}/api/providers"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${config.syncToken}")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("云端拉取失败 (${resp.code}): $body")
                }
                val list = parseProvidersJson(body)
                if (list.isEmpty()) error("云端配置为空")
                list
            }
        }
    }

    suspend fun pushToCloud(config: SyncConfig, providers: List<RelayProvider>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${config.workerUrl.trimEnd('/')}/api/providers"
                val json = serializeProvidersJson(providers)
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${config.syncToken}")
                    .put(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        error("云端推送失败 (${resp.code}): $body")
                    }
                }
            }
        }

    private fun serializeProvidersJson(providers: List<RelayProvider>): String {
        val arr = JSONArray()
        providers.forEach { p ->
            val modelsArr = JSONArray()
            p.availableModels.forEach { modelsArr.put(it) }
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("type", p.type.name)
                    put("baseUrl", p.baseUrl)
                    put("apiKey", p.apiKey)
                    put("model", p.model)
                    put("availableModels", modelsArr)
                }
            )
        }
        return arr.toString()
    }

    private fun parseProvidersJson(raw: String): List<RelayProvider> {
        val result = mutableListOf<RelayProvider>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = runCatching { ProviderType.valueOf(obj.optString("type", "OPENAI")) }
                .getOrDefault(ProviderType.OPENAI)
            val model = obj.optString("model", type.defaultModel)
            val models = mutableListOf<String>()
            val modelsArr = obj.optJSONArray("availableModels")
            if (modelsArr != null) {
                for (j in 0 until modelsArr.length()) {
                    val m = modelsArr.optString(j)
                    if (m.isNotBlank()) models.add(m)
                }
            }
            if (models.isEmpty()) models.add(model)
            result.add(
                RelayProvider(
                    id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    name = obj.optString("name", "未命名中转站"),
                    type = type,
                    baseUrl = obj.optString("baseUrl", type.defaultBaseUrl),
                    apiKey = obj.optString("apiKey", ""),
                    model = model,
                    availableModels = models
                )
            )
        }
        return result
    }

    private fun defaultProviders(): List<RelayProvider> = listOf(
        RelayProvider(
            id = "preset-openai-relay",
            name = "主力 GPT 中转站（示例）",
            type = ProviderType.OPENAI,
            baseUrl = "https://your-relay.example.com/v1",
            apiKey = "",
            model = "gpt-4o",
            availableModels = listOf("gpt-4o", "gpt-4o-mini", "o3-mini")
        ),
        RelayProvider(
            id = "preset-claude-relay",
            name = "Claude 中转站（示例）",
            type = ProviderType.CLAUDE,
            baseUrl = "https://your-claude-relay.example.com/v1",
            apiKey = "",
            model = "claude-sonnet-4-5",
            availableModels = listOf("claude-sonnet-4-5", "claude-3-7-sonnet-20250219")
        ),
        RelayProvider(
            id = "preset-gemini-relay",
            name = "Gemini 中转站（示例）",
            type = ProviderType.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiKey = "",
            model = "gemini-2.5-flash",
            availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro")
        )
    )

    companion object {
        private const val KEY_PROVIDERS = "providers_json"
        private const val KEY_ACTIVE_ID = "active_provider_id"
        private const val KEY_SYNC_URL = "cf_sync_url"
        private const val KEY_SYNC_TOKEN = "cf_sync_token"
    }
}
