package com.lippezin.dshagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lippezin.dshagent.data.ProviderConfig
import com.lippezin.dshagent.data.ProviderPresets
import com.lippezin.dshagent.data.ToolModules
import java.util.UUID

@Composable
fun SettingsScreen(vm: AgentViewModel, onPickWorkspace: () -> Unit, modifier: Modifier = Modifier) {
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var deletingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    Column(modifier.fillMaxSize()) {
        ScreenHeading("Configurações", "Provedores, chaves, armazenamento, raciocínio e permissões do agente.")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSectionTitle("Provedores e chaves", "Cada entrada pode usar uma chave e um modelo diferentes. A ordem segue a seleção atual; os demais são tentados automaticamente em caso de erro/cota.")
            vm.providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    selected = provider.id == vm.selectedProviderId,
                    onToggle = { vm.toggleProvider(provider.id, it) },
                    onEdit = { editingProvider = provider; showProviderDialog = true },
                    onDelete = { deletingProvider = provider }
                )
            }
            OutlinedButton(onClick = { editingProvider = null; showProviderDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(7.dp)); Text("Adicionar provedor ou outra API key")
            }
            Surface(color = Color(0xFF202329), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF353943))) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFFB4C9EB), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fallback automático ativo", color = Color(0xFFE0E3E8), style = MaterialTheme.typography.titleSmall)
                        Text("Quando uma chave/provedor falhar (ex.: 401, 402, 429, limite de cota ou erro de servidor), o agente tenta o próximo provedor habilitado.", color = Color(0xFF9AA2AF), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SettingsSectionTitle("Pasta de trabalho", "Os arquivos do agente ficam isolados por projeto.")
            Surface(color = Color(0xFF191B1F), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFF303239))) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, null, tint = Color(0xFFC0D0EB), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(vm.workspaceLabel, color = Color(0xFFE3E5E9), fontWeight = FontWeight.Medium)
                            Text("Cada projeto usa seu próprio diretório e MEMORY.md.", color = Color(0xFF9198A4), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = onPickWorkspace, modifier = Modifier.fillMaxWidth()) { Text("Selecionar pasta do armazenamento") }
                    Text("Sem seleção, a pasta fica no armazenamento privado do app. Se escolher uma pasta, o app cria nela ‘DSH Projects’ e salva a permissão de acesso.", color = Color(0xFF8F96A2), style = MaterialTheme.typography.bodySmall)
                }
            }

            SettingsSectionTitle("Módulos de ferramentas", "As ferramentas desligadas não são oferecidas ao modelo.")
            ToolModuleCard(icon = { Icon(Icons.Outlined.Storage, null) }, title = "Arquivos do projeto", subtitle = "Ler, listar e gravar arquivos.", checked = vm.modules.projectFiles, onChecked = { vm.updateModules(vm.modules.copy(projectFiles = it)) })
            ToolModuleCard(icon = { Icon(Icons.Outlined.Key, null) }, title = "Memória do projeto", subtitle = "Carrega e atualiza MEMORY.md.", checked = vm.modules.projectMemory, onChecked = { vm.updateModules(vm.modules.copy(projectMemory = it)) })
            ToolModuleCard(icon = { Icon(Icons.Outlined.Security, null) }, title = "Pesquisa na web", subtitle = "Pesquisa pública via DuckDuckGo HTML.", checked = vm.modules.webSearch, onChecked = { vm.updateModules(vm.modules.copy(webSearch = it)) })
            ToolModuleCard(icon = { Icon(Icons.Outlined.Lock, null) }, title = "Shell via Shizuku", subtitle = "Sempre exige confirmação antes de executar.", checked = vm.modules.shellShizuku, onChecked = { vm.updateModules(vm.modules.copy(shellShizuku = it)) })
            ToolModuleCard(icon = { Icon(Icons.Outlined.Add, null) }, title = "Anexos multimídia", subtitle = "Imagens, áudio, vídeo e arquivos de texto.", checked = vm.modules.mediaAttachments, onChecked = { vm.updateModules(vm.modules.copy(mediaAttachments = it)) })

            SettingsSectionTitle("Permissões e segurança")
            Surface(color = Color(0xFF191B1F), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFF303239))) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, null, tint = Color(0xFFC0D0EB), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Shizuku", color = Color(0xFFE4E6EB), fontWeight = FontWeight.Medium)
                            Text(vm.shellStatus, color = Color(0xFF9DA4B0), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::refreshShellStatus) { Text("Atualizar status") }
                        Button(onClick = vm::requestShizukuPermission) { Text("Solicitar permissão") }
                    }
                    Text("As chaves de API são cifradas com AES-GCM usando uma chave do Android Keystore. Não exporte logs que contenham chaves. O modelo recebe o texto e os anexos enviados para o provedor escolhido.", color = Color(0xFF8F96A2), style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsSectionTitle("Sobre")
            Text("DSH Agent · 0.1.0 Preview\nAndroid nativo • Kotlin • Jetpack Compose\nMódulos externos carregáveis não estão ativados nesta versão.", color = Color(0xFF8F96A2), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(22.dp))
        }
    }

    if (showProviderDialog) {
        ProviderDialog(
            editing = editingProvider,
            onDismiss = { showProviderDialog = false; editingProvider = null },
            onSave = { provider -> vm.saveProvider(provider); showProviderDialog = false; editingProvider = null }
        )
    }
    deletingProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text("Excluir provedor?") },
            text = { Text("A configuração ‘${provider.label}’ e a chave salva no dispositivo serão removidas.") },
            confirmButton = { TextButton(onClick = { vm.deleteProvider(provider.id); deletingProvider = null }) { Text("Excluir", color = Color(0xFFFFB4AB)) } },
            dismissButton = { TextButton(onClick = { deletingProvider = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF222833) else Color(0xFF191B1F),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFF43516A) else Color(0xFF303239))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF2A303B), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Key, null, tint = Color(0xFFC3D2EF), modifier = Modifier.size(19.dp)) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.label, color = Color(0xFFE8EAEE), fontWeight = FontWeight.Medium)
                    Text(provider.model.ifBlank { "Modelo não definido" }, color = Color(0xFFADB6C5), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(maskUrl(provider.baseUrl) + " · " + if (provider.apiKey.isBlank()) "sem chave" else "chave configurada", color = Color(0xFF858D99), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = provider.enabled, onCheckedChange = onToggle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Text("Selecionado no compositor", color = Color(0xFFB9D0FC), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(37.dp)) { Icon(Icons.Outlined.Edit, "Editar", tint = Color(0xFFB6C6E4), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(37.dp)) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = Color(0xFFD3A6A3), modifier = Modifier.size(19.dp)) }
            }
        }
    }
}

private fun maskUrl(url: String): String = url.removePrefix("https://").removePrefix("http://").trimEnd('/')

@Composable
private fun ProviderDialog(editing: ProviderConfig?, onDismiss: () -> Unit, onSave: (ProviderConfig) -> Unit) {
    var label by remember(editing?.id) { mutableStateOf(editing?.label ?: "DeepSeek") }
    var baseUrl by remember(editing?.id) { mutableStateOf(editing?.baseUrl ?: ProviderPresets.all[0].baseUrl) }
    var apiKey by remember(editing?.id) { mutableStateOf(editing?.apiKey ?: "") }
    var model by remember(editing?.id) { mutableStateOf(editing?.model ?: ProviderPresets.all[0].suggestedModel) }
    var enabled by remember(editing?.id) { mutableStateOf(editing?.enabled ?: true) }
    var presetMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Adicionar provedor" else "Editar provedor") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 530.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Use uma API compatível com /chat/completions. Para adicionar várias chaves, crie uma entrada por chave/modelo.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AE))
                Box {
                    OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Modelo de provedor: $label", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        ProviderPresets.all.forEach { preset ->
                            DropdownMenuItem(text = { Text(preset.label) }, onClick = {
                                label = preset.label; baseUrl = preset.baseUrl; model = preset.suggestedModel; presetMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nome / provedor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("URL base da API") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("ID exato do modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ativo para fallback", color = Color(0xFFE0E3E9))
                        Text("Provedores ativos podem receber a próxima tentativa", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9299A5))
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                error?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    label.isBlank() -> error = "Informe o nome do provedor."
                    !baseUrl.trim().startsWith("https://") -> error = "Use uma URL HTTPS válida."
                    apiKey.isBlank() -> error = "Informe a API key."
                    model.isBlank() -> error = "Informe o ID do modelo."
                    else -> onSave(ProviderConfig(id = editing?.id ?: UUID.randomUUID().toString(), label = label.trim(), baseUrl = baseUrl.trim().trimEnd('/'), apiKey = apiKey.trim(), model = model.trim(), enabled = enabled))
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Color(0xFF202227)
    )
}
