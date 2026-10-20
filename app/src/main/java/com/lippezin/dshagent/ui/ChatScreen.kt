package com.lippezin.dshagent.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lippezin.dshagent.data.ChatEntry
import com.lippezin.dshagent.data.ProviderConfig

private val ChatSurface = Color(0xFF191B1F)
private val ComposeSurface = Color(0xFF27292E)

@Composable
fun ChatScreen(vm: AgentViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.addAttachment(uris) }
    Column(modifier.fillMaxSize().imePadding()) {
        ChatTopBar(vm, compact)
        if (vm.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                WelcomePanel(vm, compact)
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(vm.messages.size, vm.isSending, vm.statusText) {
                if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 32.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(vm.messages, key = { it.id }) { message -> MessageBubble(message) }
                if (vm.isSending) item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(start = 5.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Color(0xFF9DB7E8))
                        Text(vm.statusText.ifBlank { "Trabalhando…" }, color = Color(0xFFAEB5C1), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Composer(
            vm = vm,
            compact = compact,
            onChooseFiles = { filePicker.launch(arrayOf("image/*", "audio/*", "video/*", "text/*", "application/pdf", "application/json")) }
        )
    }
}

@Composable
private fun ChatTopBar(vm: AgentViewModel, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 13.dp else 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF272A30), border = BorderStroke(1.dp, Color(0xFF373A41))) {
            Text("◉", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color(0xFFD7E4FF), fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("DSH Agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFF0F1F4))
            Text(vm.activeProject.name, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9FA5B0), maxLines = 1)
        }
        var projectMenu by remember { mutableStateOf(false) }
        var sessionsMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { projectMenu = true }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(17.dp), tint = Color(0xFFB6C5E0))
                if (!compact) Spacer(Modifier.width(5.dp))
                if (!compact) Text(vm.activeProject.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFD8DADE))
                Icon(Icons.Outlined.ExpandMore, null, modifier = Modifier.size(16.dp), tint = Color(0xFF9FA5B0))
            }
            DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                vm.projects.forEach { project ->
                    DropdownMenuItem(text = { Text(project.name) }, onClick = { vm.selectProject(project); projectMenu = false })
                }
            }
        }
        Box {
            IconButton(onClick = { sessionsMenu = true }, modifier = Modifier.size(37.dp)) {
                Icon(Icons.Outlined.MoreHoriz, "Sessões", tint = Color(0xFFB7BBC4))
            }
            DropdownMenu(expanded = sessionsMenu, onDismissRequest = { sessionsMenu = false }) {
                DropdownMenuItem(text = { Text("Nova sessão") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { vm.newSession(); sessionsMenu = false })
                if (vm.sessions.isNotEmpty()) HorizontalDivider()
                vm.sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { if (vm.activeSessionId == session.id) Icon(Icons.Outlined.Check, null, tint = Color(0xFFB9D0FF)) },
                        onClick = { vm.selectSession(session); sessionsMenu = false }
                    )
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFF24262A), thickness = 1.dp)
}

@Composable
private fun WelcomePanel(vm: AgentViewModel, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 25.dp else 50.dp).offset(y = (-12).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = Color(0xFF202329), border = BorderStroke(1.dp, Color(0xFF383C45)), modifier = Modifier.size(68.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SmartToy, null, tint = Color(0xFFE3E9F5), modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("Into the Unknown", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFF0F1F4), fontSize = if (compact) 23.sp else 29.sp)
            Spacer(Modifier.width(9.dp))
            Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF283444)) {
                Text("Preview", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = Color(0xFFC5D7F4), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("Um agente para transformar ideias em tarefas.", color = Color(0xFF9DA2AC), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            FeaturePill("Arquivos")
            FeaturePill("Pesquisa")
            FeaturePill("Ferramentas")
        }
        if (vm.providers.none { it.enabled && it.apiKey.isNotBlank() }) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { vm.tab = AgentViewModel.Tab.SETTINGS }) {
                Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Configure sua primeira chave de API")
            }
        }
    }
}

@Composable
private fun FeaturePill(label: String) {
    Surface(color = Color(0xFF1D2025), shape = RoundedCornerShape(30.dp), border = BorderStroke(1.dp, Color(0xFF30343C))) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = Color(0xFFAEB6C4), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MessageBubble(message: ChatEntry) {
    val isUser = message.role == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(modifier = Modifier.fillMaxWidth(if (isUser) .91f else .98f)) {
            if (!isUser) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)) {
                    Icon(Icons.Outlined.SmartToy, null, tint = Color(0xFFB7C9E8), modifier = Modifier.size(17.dp))
                    Text("DSH Agent", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB5B9C2))
                }
            }
            Surface(
                shape = RoundedCornerShape(if (isUser) 20.dp else 16.dp),
                color = if (isUser) Color(0xFF30333A) else Color.Transparent,
                border = if (isUser) BorderStroke(1.dp, Color(0xFF3D4048)) else null
            ) {
                Column(Modifier.padding(horizontal = if (isUser) 14.dp else 3.dp, vertical = 11.dp)) {
                    if (message.content.isNotBlank()) {
                        SelectionContainerCompat {
                            Text(message.content, color = if (isUser) Color(0xFFF0F1F3) else Color(0xFFE1E3E8), style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
                        }
                    }
                    if (message.attachmentNames.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        message.attachmentNames.forEach { name ->
                            Surface(color = Color(0xFF202329), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Color(0xFF3A3D45))) {
                                Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.AttachFile, null, tint = Color(0xFFB5C5E2), modifier = Modifier.size(15.dp))
                                    Text(name, color = Color(0xFFCFD5E0), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionContainerCompat(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer { content() }
}

@Composable
private fun Composer(vm: AgentViewModel, compact: Boolean, onChooseFiles: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 10.dp else 26.dp).padding(bottom = 10.dp, top = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = ComposeSurface,
        border = BorderStroke(1.dp, Color(0xFF373940)),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            BasicTextField(
                value = vm.inputText,
                onValueChange = { vm.inputText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 57.dp, max = 145.dp).padding(horizontal = 4.dp, vertical = 5.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF0F1F4), lineHeight = 23.sp),
                cursorBrush = SolidColor(Color(0xFFABC4F2)),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(),
                decorationBox = { inner ->
                    Box {
                        if (vm.inputText.isEmpty()) Text("Descreva o que você quer construir", color = Color(0xFF9297A2), style = MaterialTheme.typography.bodyLarge)
                        inner()
                    }
                }
            )
            if (vm.pendingAttachments.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.pendingAttachments.forEach { uri -> AttachmentChip(uri) { vm.removeAttachment(uri) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onChooseFiles, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Add, "Adicionar anexos", tint = Color(0xFFDFE2E8), modifier = Modifier.size(23.dp))
                }
                TextButton(onClick = { vm.tab = AgentViewModel.Tab.PROJECTS }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp)) {
                    Icon(Icons.Outlined.FolderOpen, null, tint = Color(0xFFBBC8DE), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Workspace Write", color = Color(0xFFC9CDD5), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                if (!compact) {
                    Text("${vm.pendingAttachments.size}/8", color = Color(0xFF777D88), style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider(color = Color(0xFF383A41), modifier = Modifier.padding(vertical = 5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ModelPicker(vm)
                    ThinkingPicker(vm)
                }
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = vm::sendMessage,
                    enabled = !vm.isSending && (vm.inputText.isNotBlank() || vm.pendingAttachments.isNotEmpty()),
                    modifier = Modifier.size(39.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF7895C5), contentColor = Color(0xFF10151E), disabledContainerColor = Color(0xFF41454E), disabledContentColor = Color(0xFF8A909B))
                ) {
                    Icon(Icons.Outlined.ArrowUpward, "Enviar", modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(uri: Uri, onRemove: () -> Unit) {
    val label = uri.lastPathSegment?.substringAfterLast('/') ?: "Anexo"
    Surface(color = Color(0xFF34373E), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Color(0xFF454851))) {
        Row(Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AttachFile, null, tint = Color(0xFFC9D7F0), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color(0xFFE0E3E9), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 125.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(25.dp)) { Icon(Icons.Outlined.Close, "Remover anexo", tint = Color(0xFFB6BBC4), modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun ModelPicker(vm: AgentViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
            modifier = Modifier.widthIn(max = 150.dp)
        ) {
            Icon(Icons.Outlined.Memory, null, tint = Color(0xFFB8C6DF), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(vm.selectedProvider?.model ?: "Escolher modelo", color = Color(0xFFD9DCE2), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, tint = Color(0xFF9298A4), modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vm.providers.filter { it.enabled && it.apiKey.isNotBlank() }.forEach { p ->
                DropdownMenuItem(
                    text = { Column { Text(p.model, style = MaterialTheme.typography.bodyMedium); Text(p.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BA4B3)) } },
                    leadingIcon = { if (p.id == vm.selectedProviderId) Icon(Icons.Outlined.Check, null, tint = Color(0xFFB9D0FF)) },
                    onClick = { vm.setSelectedProvider(p.id); expanded = false }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Gerenciar provedores…") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { vm.tab = AgentViewModel.Tab.SETTINGS; expanded = false })
        }
    }
}

@Composable
private fun ThinkingPicker(vm: AgentViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp)) {
            Icon(Icons.Outlined.Tune, null, tint = Color(0xFFB8C6DF), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(3.dp))
            Text(vm.thinkingLevel, color = Color(0xFFD9DCE2), style = MaterialTheme.typography.labelSmall)
            Icon(Icons.Outlined.ExpandMore, null, tint = Color(0xFF9298A4), modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Off", "Low", "Medium", "High", "Max").forEach { level ->
                DropdownMenuItem(
                    text = { Column { Text(level); Text(when (level) { "Off" -> "Resposta direta"; "Low" -> "Mais rápido"; "Medium" -> "Equilibrado"; "High" -> "Análise cuidadosa"; else -> "Esforço máximo quando compatível" }, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA2AD)) } },
                    leadingIcon = { if (vm.thinkingLevel == level) Icon(Icons.Outlined.Check, null, tint = Color(0xFFB9D0FF)) },
                    onClick = { vm.setThinkingLevel(level); expanded = false }
                )
            }
        }
    }
}
