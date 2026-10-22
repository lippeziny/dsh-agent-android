package com.lippezin.dshagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lippezin.dshagent.data.McpServer
import com.lippezin.dshagent.data.isCleartextUrl
import com.lippezin.dshagent.data.isLocalOrPrivateHost
import com.lippezin.dshagent.data.isValidBaseUrl

/** Seção da aba Ferramentas: cofre Obsidian de memória e servidores MCP (ex.: ai-memory). */
@Composable
fun MemorySection(vm: AgentViewModel) {
    var dialog by remember { mutableStateOf<McpServer?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val vaultProject = vm.projects.firstOrNull { it.id == vm.vaultProjectId }

    SettingsSectionTitle(
        "Memória e integrações",
        "Cofre Obsidian como memória de longo prazo e servidores MCP. Arquivos AGENTS.md/CLAUDE.md e o grafo do graphify (graphify-out) dos projetos são lidos automaticamente."
    )

    ToolModuleCard(
        icon = { Icon(Icons.Outlined.Description, null) },
        title = "Cofre de memória (Obsidian)",
        subtitle = "Busca por palavras-chave, leitura por frontmatter, notas atômicas com wikilinks e roteamento por pasta, como no padrão CLAUDE.md/AGENTS.md.",
        checked = vm.modules.vaultMemory,
        onChecked = { vm.updateModules(vm.modules.copy(vaultMemory = it)) }
    )
    Surface(color = C.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, C.Border)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cofre atual", style = MaterialTheme.typography.titleSmall, color = C.Text, fontWeight = FontWeight.Medium)
            Text(vaultProject?.name ?: "Nenhum cofre definido", style = MaterialTheme.typography.bodyMedium, color = if (vaultProject != null) C.Accent2 else C.Dim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::createVault, enabled = !vm.memoryBusy) { Text("Criar cofre") }
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) { Text("Usar projeto") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        vm.projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.name) },
                                onClick = { vm.selectVaultProject(project); menuOpen = false }
                            )
                        }
                        if (vm.vaultProjectId != null) {
                            DropdownMenuItem(
                                text = { Text("Nenhum (desativar cofre)") },
                                onClick = { vm.selectVaultProject(null); menuOpen = false }
                            )
                        }
                    }
                }
            }
            if (vaultProject != null) {
                OutlinedButton(onClick = vm::scaffoldVault, enabled = !vm.memoryBusy) { Text("Completar estrutura do cofre") }
            }
            Text(
                "Para usar o cofre que você já tem no Obsidian, selecione o projeto-cofre e envie o ZIP dele no chat (menu +). O app acha a raiz pelo .obsidian, CLAUDE.md ou AGENTS.md. Pastas: 00-Inbox, 10-Trabalho, 20-Estudos, 30-Projetos, 50-Pessoal, 70-Daily, 99-Templates.",
                style = MaterialTheme.typography.bodySmall, color = C.Dim
            )
        }
    }

    ToolModuleCard(
        icon = { Icon(Icons.Outlined.Cloud, null) },
        title = "Servidores MCP (HTTP)",
        subtitle = "Usa as ferramentas de servidores MCP, como o ai-memory (http://IP-DO-PC:49374/mcp). Só HTTP: servidores que rodam por npx/stdio não funcionam no Android.",
        checked = vm.modules.mcp,
        onChecked = { vm.updateModules(vm.modules.copy(mcp = it)) }
    )
    vm.mcpServers.forEach { server ->
        McpServerCard(server, vm, onEdit = { dialog = server })
    }
    OutlinedButton(onClick = { dialog = McpServer(name = "", url = "http://") }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Adicionar servidor MCP")
    }

    dialog?.let { draft ->
        McpServerDialog(
            initial = draft,
            onDismiss = { dialog = null },
            onSave = { saved -> vm.saveMcpServer(saved); dialog = null }
        )
    }
}

@Composable
private fun McpServerCard(server: McpServer, vm: AgentViewModel, onEdit: () -> Unit) {
    Surface(
        color = C.Surface, shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (server.enabled) C.BorderStrong else C.Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleSmall, color = C.Text, fontWeight = FontWeight.Medium)
                    Text(server.url, style = MaterialTheme.typography.bodySmall, color = C.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (server.requireApproval) {
                        Text("Pede aprovação a cada chamada", style = MaterialTheme.typography.labelSmall, color = C.Accent)
                    }
                }
                Switch(checked = server.enabled, onCheckedChange = { vm.saveMcpServer(server.copy(enabled = it)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.testMcpServer(server) }, enabled = !vm.memoryBusy) {
                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Testar")
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar")
                }
                TextButton(onClick = { vm.deleteMcpServer(server.id) }) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp), tint = C.Danger)
                    Spacer(Modifier.width(4.dp))
                    Text("Excluir", color = C.Danger)
                }
            }
        }
    }
}

@Composable
private fun McpServerDialog(initial: McpServer, onDismiss: () -> Unit, onSave: (McpServer) -> Unit) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var url by remember(initial.id) { mutableStateOf(initial.url) }
    var token by remember(initial.id) { mutableStateOf(initial.token) }
    var approval by remember(initial.id) { mutableStateOf(initial.requireApproval) }
    val urlOk = isValidBaseUrl(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Novo servidor MCP" else "Editar servidor MCP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL do endpoint MCP") }, singleLine = true,
                    isError = url.isNotBlank() && !urlOk, modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    url = "http://127.0.0.1:49374/mcp"
                    if (name.isBlank()) name = "ai-memory"
                }) { Text("Usar modelo do ai-memory") }
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("Token Bearer (opcional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pedir aprovação a cada chamada", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = approval, onCheckedChange = { approval = it })
                }
                if (urlOk && isCleartextUrl(url) && !isLocalOrPrivateHost(url)) {
                    Text(
                        "Atenção: HTTP sem criptografia fora da rede local envia o token em texto puro.",
                        style = MaterialTheme.typography.bodySmall, color = C.Danger
                    )
                }
                Text(
                    "No celular, 127.0.0.1 é o próprio aparelho. Para um servidor no PC use o IP dele na rede (ex.: http://192.168.0.10:49374/mcp). O ai-memory exige token quando não é acessado por loopback.",
                    style = MaterialTheme.typography.bodySmall, color = C.Dim
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && urlOk,
                onClick = { onSave(initial.copy(name = name.trim(), url = url.trim(), token = token.trim(), requireApproval = approval)) }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
