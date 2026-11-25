package com.relayswitch.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.relayswitch.app.data.ProviderType
import com.relayswitch.app.data.RelayProvider
import com.relayswitch.app.data.SyncConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSheet(
    providers: List<RelayProvider>,
    activeProviderId: String,
    syncConfig: SyncConfig,
    onDismiss: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSaveProvider: (RelayProvider) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSaveSyncConfig: (String, String) -> Unit,
    onPullCloud: () -> Unit,
    onPushCloud: () -> Unit
) {
    var editingProvider by remember { mutableStateOf<RelayProvider?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var workerUrl by remember(syncConfig) { mutableStateOf(syncConfig.workerUrl) }
    var syncToken by remember(syncConfig) { mutableStateOf(syncConfig.syncToken) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "中转站切换（CC Switch 风格）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        isCreating = true
                        editingProvider = RelayProvider(
                            name = "",
                            type = ProviderType.OPENAI,
                            baseUrl = ProviderType.OPENAI.defaultBaseUrl,
                            apiKey = "",
                            model = ProviderType.OPENAI.defaultModel
                        )
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新增")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            providers.forEach { provider ->
                val isActive = provider.id == activeProviderId
                Surface(
                    tonalElevation = if (isActive) 4.dp else 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectProvider(provider.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "当前启用",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${provider.type.label} · ${provider.model}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = provider.baseUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            isCreating = false
                            editingProvider = provider
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        if (providers.size > 1) {
                            IconButton(onClick = { onDeleteProvider(provider.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Cloudflare Workers 云同步（可选）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = workerUrl,
                onValueChange = { workerUrl = it },
                label = { Text("Worker 地址（例如 https://relay.xxx.workers.dev）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = syncToken,
                onValueChange = { syncToken = it },
                label = { Text("同步密钥（SYNC_TOKEN）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSaveSyncConfig(workerUrl, syncToken)
                        onPullCloud()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("从云端拉取")
                }
                Button(
                    onClick = {
                        onSaveSyncConfig(workerUrl, syncToken)
                        onPushCloud()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("推送到云端")
                }
            }
        }
    }

    editingProvider?.let { target ->
        ProviderEditDialog(
            initial = target,
            isNew = isCreating,
            onDismiss = { editingProvider = null },
            onConfirm = { saved ->
                onSaveProvider(saved)
                editingProvider = null
            }
        )
    }
}

@Composable
private fun ProviderEditDialog(
    initial: RelayProvider,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RelayProvider) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新增中转站" else "编辑中转站") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderType.entries.forEach { pt ->
                        FilterChip(
                            selected = type == pt,
                            onClick = {
                                type = pt
                                if (isNew) {
                                    baseUrl = pt.defaultBaseUrl
                                    model = pt.defaultModel
                                }
                            },
                            label = { Text(pt.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("中转站名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("默认模型名（如 gpt-4o / claude-sonnet-4-5）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim().ifBlank { "${type.label} 中转站" }
                    val finalModel = model.trim().ifBlank { type.defaultModel }
                    onConfirm(
                        initial.copy(
                            name = finalName,
                            type = type,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = finalModel,
                            availableModels = (initial.availableModels + finalModel).distinct()
                        )
                    )
                }
            ) {
                Text("保存并启用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
