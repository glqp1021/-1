package com.relayswitch.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class LlmApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun streamChat(
        provider: RelayProvider,
        history: List<ChatMessage>
    ): Flow<String> = flow {
        require(provider.apiKey.isNotBlank()) { "请先在中转站配置中填写 API Key" }
        require(provider.baseUrl.isNotBlank()) { "请先填写中转站 Base URL" }

        val request = when (provider.type) {
            ProviderType.OPENAI -> buildOpenAiRequest(provider, history)
            ProviderType.CLAUDE -> buildClaudeRequest(provider, history)
            ProviderType.GEMINI -> buildGeminiRequest(provider, history)
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty().take(500)
                throw IllegalStateException("请求失败 (${response.code}): $errBody")
            }

            val body = response.body ?: throw IllegalStateException("响应体为空")
            BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (!trimmed.startsWith("data:")) continue
                    val payload = trimmed.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue

                    val chunk = when (provider.type) {
                        ProviderType.OPENAI -> extractOpenAiDelta(payload)
                        ProviderType.CLAUDE -> extractClaudeDelta(payload)
                        ProviderType.GEMINI -> extractGeminiDelta(payload)
                    }
                    if (!chunk.isNullOrEmpty()) {
                        emit(chunk)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildOpenAiRequest(provider: RelayProvider, history: List<ChatMessage>): Request {
        val base = provider.baseUrl.trimEnd('/')
        val endpoint = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }

        val messagesJson = JSONArray()
        history.filter { !it.isError && it.content.isNotBlank() || it.image != null }.forEach { msg ->
            val obj = JSONObject().put("role", msg.role)
            if (msg.image != null && msg.role == "user") {
                val contentArr = JSONArray()
                if (msg.content.isNotBlank()) {
                    contentArr.put(JSONObject().put("type", "text").put("text", msg.content))
                }
                contentArr.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put(
                                "url",
                                "data:${msg.image.mimeType};base64,${msg.image.base64Data}"
                            )
                        )
                )
                obj.put("content", contentArr)
            } else {
                obj.put("content", msg.content)
            }
            messagesJson.put(obj)
        }

        val bodyJson = JSONObject()
            .put("model", provider.model)
            .put("stream", true)
            .put("messages", messagesJson)

        return Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${provider.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()
    }

    private fun buildClaudeRequest(provider: RelayProvider, history: List<ChatMessage>): Request {
        val base = provider.baseUrl.trimEnd('/')
        val endpoint = when {
            base.endsWith("/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }

        val messagesJson = JSONArray()
        history.filter { !it.isError && (it.content.isNotBlank() || it.image != null) }.forEach { msg ->
            val obj = JSONObject().put("role", msg.role)
            if (msg.image != null && msg.role == "user") {
                val contentArr = JSONArray()
                contentArr.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", msg.image.mimeType)
                                .put("data", msg.image.base64Data)
                        )
                )
                if (msg.content.isNotBlank()) {
                    contentArr.put(JSONObject().put("type", "text").put("text", msg.content))
                }
                obj.put("content", contentArr)
            } else {
                obj.put("content", msg.content)
            }
            messagesJson.put(obj)
        }

        val bodyJson = JSONObject()
            .put("model", provider.model)
            .put("max_tokens", 4096)
            .put("stream", true)
            .put("messages", messagesJson)

        return Request.Builder()
            .url(endpoint)
            .header("x-api-key", provider.apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()
    }

    private fun buildGeminiRequest(provider: RelayProvider, history: List<ChatMessage>): Request {
        val base = provider.baseUrl.trimEnd('/')
        val modelPath = if (provider.model.startsWith("models/")) provider.model else "models/${provider.model}"
        val endpoint = "$base/$modelPath:streamGenerateContent?alt=sse&key=${provider.apiKey.trim()}"

        val contentsJson = JSONArray()
        history.filter { !it.isError && (it.content.isNotBlank() || it.image != null) }.forEach { msg ->
            val geminiRole = if (msg.role == "assistant") "model" else "user"
            val parts = JSONArray()
            if (msg.image != null && msg.role == "user") {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", msg.image.mimeType)
                            .put("data", msg.image.base64Data)
                    )
                )
            }
            if (msg.content.isNotBlank()) {
                parts.put(JSONObject().put("text", msg.content))
            }
            contentsJson.put(
                JSONObject()
                    .put("role", geminiRole)
                    .put("parts", parts)
            )
        }

        val bodyJson = JSONObject().put("contents", contentsJson)

        return Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()
    }

    private fun extractOpenAiDelta(payload: String): String? = runCatching {
        val obj = JSONObject(payload)
        val choices = obj.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null
        val content = delta.optString("content", "")
        if (content.isNotEmpty()) return content
        val reasoning = delta.optString("reasoning_content", "")
        if (reasoning.isNotEmpty()) reasoning else null
    }.getOrNull()

    private fun extractClaudeDelta(payload: String): String? = runCatching {
        val obj = JSONObject(payload)
        if (obj.optString("type") != "content_block_delta") return null
        val delta = obj.optJSONObject("delta") ?: return null
        if (delta.optString("type") == "text_delta") delta.optString("text", "") else null
    }.getOrNull()

    private fun extractGeminiDelta(payload: String): String? = runCatching {
        val obj = JSONObject(payload)
        val candidates = obj.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        sb.toString().ifEmpty { null }
    }.getOrNull()
}
