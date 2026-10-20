package com.lippezin.dshagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ProjectsScreen(vm: AgentViewModel, modifier: Modifier = Modifier) {
    var createDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        ScreenHeading(
            title = "Projetos",
            subtitle = "Cada projeto tem seus próprios arquivos, conversa e memória persistente.",
            trailing = {
                FilledTonalButton(onClick = { newName = ""; createDialog = true }) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Novo projeto")
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(vm.projects, key = { it.id }) { project ->
                val selected = project.id == vm.activeProjectId
                Surface(
                    onClick = { vm.selectProject(project) },
                    shape = RoundedCornerShape(17.dp),
                    color = if (selected) Color(0xFF242A34) else Color(0xFF191B1F),
                    border = BorderStroke(1.dp, if (selected) Color(0xFF50617D) else Color(0xFF303239)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF2D323C), modifier = Modifier.size(43.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.FolderOpen, null, tint = Color(0xFFC4D3EF), modifier = Modifier.size(22.dp)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(project.name, color = Color(0xFFF0F1F4), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(if (selected) "Projeto ativo" else "Toque para abrir", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.labelSmall)
                        }
                        if (selected) Icon(Icons.Outlined.TaskAlt, null, tint = Color(0xFFAFC6F3))
                        else Icon(Icons.Outlined.ArrowForward, null, tint = Color(0xFF8C929C))
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Arquivos do projeto ativo", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE2E4E9), modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::refreshProjectFiles) { Icon(Icons.Outlined.Refresh, "Atualizar lista", tint = Color(0xFFAEB7C7)) }
                }
                Text("Pasta: ${vm.workspaceLabel} / DSH Projects / ${vm.activeProjectId.take(8)}…", color = Color(0xFF9298A3), style = MaterialTheme.typography.bodySmall)
            }
            if (vm.projectFiles.isEmpty()) {
                item {
                    Surface(color = Color(0xFF191B1F), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF2D3036))) {
                        Column(Modifier.fillMaxWidth().padding(19.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Description, null, tint = Color(0xFF79869D), modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(7.dp))
                            Text("Nenhum arquivo ainda", color = Color(0xFFB0B5BE))
                            Text("Peça ao agente para criar arquivos ou salvar uma memória.", color = Color(0xFF858B95), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(vm.projectFiles, key = { "file-$it" }) { path ->
                    Surface(
                        onClick = { vm.openProjectFile(path) },
                        color = Color(0xFF191B1F), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2D3036)), modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (path.endsWith(".kt") || path.endsWith(".sh")) Icons.Outlined.Code else Icons.Outlined.Description, null, tint = Color(0xFFAEBFDD), modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(path, color = Color(0xFFD4D7DD), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ArrowForward, null, tint = Color(0xFF858B95), modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(15.dp)) }
        }
    }
    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Criar projeto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("O agente usará uma pasta isolada e um arquivo MEMORY.md para cada projeto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nome do projeto") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.createProject(newName); createDialog = false }) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancelar") } }
        )
    }
    vm.openedFileName?.let { filename ->
        AlertDialog(
            onDismissRequest = vm::closeProjectFile,
            title = { Text(filename, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Surface(color = Color(0xFF111316), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(vm.openedFileContent, modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp), color = Color(0xFFD5DFEE), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.closeProjectFile() }) { Text("Fechar") } }
        )
    }
}
