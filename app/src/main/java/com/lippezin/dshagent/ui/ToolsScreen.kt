package com.lippezin.dshagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lippezin.dshagent.data.ToolModules

@Composable
fun ToolsScreen(vm: AgentViewModel, modifier: Modifier = Modifier) {
    var terminalCommand by remember { mutableStateOf("id") }
    Column(modifier.fillMaxSize()) {
        ScreenHeading("Ferramentas", "Ative os recursos que o agente pode chamar durante uma tarefa.")
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolModuleCard(
                icon = { Icon(Icons.Outlined.FolderOpen, null) }, title = "Arquivos do projeto", subtitle = "Listar, ler e gravar arquivos na pasta isolada do projeto.", checked = vm.modules.projectFiles,
                onChecked = { vm.updateModules(vm.modules.copy(projectFiles = it)) }
            )
            ToolModuleCard(
                icon = { Icon(Icons.Outlined.Memory, null) }, title = "Memória persistente", subtitle = "Ler MEMORY.md no início da tarefa e salvar decisões duráveis.", checked = vm.modules.projectMemory,
                onChecked = { vm.updateModules(vm.modules.copy(projectMemory = it)) }
            )
            ToolModuleCard(
                icon = { Icon(Icons.Outlined.Search, null) }, title = "Pesquisa na web", subtitle = "Consultar resultados públicos e passar títulos, resumos e links para o modelo.", checked = vm.modules.webSearch,
                onChecked = { vm.updateModules(vm.modules.copy(webSearch = it)) }
            )
            ToolModuleCard(
                icon = { Icon(Icons.Outlined.Mic, null) }, title = "Anexos multimídia", subtitle = "Imagens, quadros extraídos de vídeo, transcrição de áudio compatível e arquivos de texto.", checked = vm.modules.mediaAttachments,
                onChecked = { vm.updateModules(vm.modules.copy(mediaAttachments = it)) }
            )
            ToolModuleCard(
                icon = { Icon(Icons.Outlined.Terminal, null) }, title = "Shell Android via Shizuku", subtitle = "Permite executar comandos do shell do dispositivo. Cada comando exige confirmação.", checked = vm.modules.shellShizuku,
                onChecked = { vm.updateModules(vm.modules.copy(shellShizuku = it)) }
            )
            Surface(color = Color(0xFF191B1F), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF30333A)) ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Outlined.Security, null, tint = Color(0xFFC1D3F2), modifier = Modifier.size(23.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Estado do Shizuku", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFFE7E9ED))
                            Text(vm.shellStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA1AE))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::refreshShellStatus) { Text("Atualizar") }
                        Button(onClick = vm::requestShizukuPermission) { Text("Solicitar permissão") }
                    }
                    Text("O Shizuku precisa estar instalado e iniciado. A identidade ADB não equivale a root e certas operações continuam bloqueadas pelo Android.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E95A1))
                }
            }
            Surface(color = Color(0xFF191B1F), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF30333A))) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Terminal, null, tint = Color(0xFFC1D3F2), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Terminal manual", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE7E9ED), fontWeight = FontWeight.Medium)
                    }
                    Text("Exemplos: id, pm list packages, getprop ro.build.version.release", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9198A4))
                    OutlinedTextField(
                        value = terminalCommand,
                        onValueChange = { terminalCommand = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Comando shell") },
                        singleLine = true,
                        enabled = !vm.isSending
                    )
                    Button(onClick = { vm.runManualShell(terminalCommand) }, enabled = !vm.isSending && terminalCommand.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Outlined.Terminal, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Executar com confirmação")
                    }
                }
            }
            Surface(color = Color(0xFF191B1F), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF30333A))) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Cloud, null, tint = Color(0xFFBBCBEA), modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Adicionar novos módulos", color = Color(0xFFE7E9ED), fontWeight = FontWeight.Medium)
                        Text("Esta versão usa módulos nativos ativáveis. O carregamento de plugins de terceiros ainda não está habilitado.", color = Color(0xFF8F96A2), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ToolModuleCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Surface(color = Color(0xFF191B1F), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, if (checked) Color(0xFF3A4558) else Color(0xFF303239))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF272C34), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { CompositionLocalProvider(LocalContentColor provides Color(0xFFC1D3F2)) { icon() } }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE5E7EC), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF969DA8))
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
