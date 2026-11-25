package com.relayswitch.app.data

import java.util.UUID

enum class ProviderType(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    OPENAI("OpenAI 兼容", "https://api.openai.com/v1", "gpt-4o-mini"),
    CLAUDE("Claude", "https://api.anthropic.com/v1", "claude-sonnet-4-5"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash")
}

data class RelayProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProviderType = ProviderType.OPENAI,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val availableModels: List<String> = listOf(model)
)

data class ImageAttachment(
    val uriString: String,
    val base64Data: String,
    val mimeType: String = "image/jpeg"
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val image: ImageAttachment? = null,
    val providerName: String? = null,
    val modelName: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

data class SyncConfig(
    val workerUrl: String = "",
    val syncToken: String = ""
)
