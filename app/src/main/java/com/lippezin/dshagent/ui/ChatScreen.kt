package com.lippezin.dshagent.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lippezin.dshagent.ai.Attachments
import com.lippezin.dshagent.data.ChatEntry
import com.lippezin.dshagent.data.ChatSession

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream")

@Composable
fun ChatScreen(vm: AgentViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.addAttachment(uris) }
    var showModelSheet by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().imePadding()) {
        ChatTopBar(vm, compact)
        if (vm.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                WelcomePanel(vm, compact, onPickZip = { filePicker.launch(ZIP_TYPES) }, onModelClick = { showModelSheet = true })
            }
        } else {
            MessageList(vm, compact, Modifier.weight(1f))
        }
        Composer(
            vm = vm,
            compact = compact,
            onPickFiles = { filePicker.launch(arrayOf("*/*")) },
            onPickZip = { filePicker.launch(ZIP_TYPES) },
            onModelClick = { showModelSheet = true }
        )
    }
    if (showModelSheet) ModelPickerSheet(vm, onDismiss = { showModelSheet = false })
}

// ---------- Barra superior ----------

@Composable
private fun ChatTopBar(vm: AgentViewModel, compact: Boolean) {
    var projectMenu by remember { mutableStateOf(false) }
    var sessionsMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatSession?>(null) }
    var deleting by remember { mutableStateOf<ChatSession?>(null) }
    val activeSession = vm.sessions.firstOrNull { it.id == vm.activeSessionId }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 26.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable { projectMenu = true }.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMark(36.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(vm.activeProject.name, style = MaterialTheme.typography.titleMedium, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(activeSession?.title ?: "Nova conversa", style = MaterialTheme.typography.labelSmall, color = C.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ExpandMore, null, tint = C.Dim, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                vm.projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { if (project.id == vm.activeProjectId) Icon(Icons.Outlined.Check, null, tint = C.Accent) },
                        onClick = { vm.selectProject(project); projectMenu = false }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Gerenciar projetos…") },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                    onClick = { vm.tab = AgentViewModel.Tab.PROJECTS; projectMenu = false }
                )
            }
        }
        IconButton(onClick = { vm.newSession() }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Add, "Nova sessão", tint = C.Text2)
        }
        Box {
            IconButton(onClick = { sessionsMenu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.MoreHoriz, "Sessões", tint = C.Text2)
            }
            DropdownMenu(expanded = sessionsMenu, onDismissRequest = { sessionsMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Nova sessão") },
                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    onClick = { vm.newSession(); sessionsMenu = false }
                )
                if (vm.sessions.isNotEmpty()) HorizontalDivider()
                vm.sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { if (vm.activeSessionId == session.id) Icon(Icons.Outlined.Check, null, tint = C.Accent) },
                        onClick = { vm.selectSession(session); sessionsMenu = false }
                    )
                }
                if (activeSession != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Renomear sessão atual") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { renaming = activeSession; sessionsMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir sessão atual", color = C.Danger) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = C.Danger) },
                        onClick = { deleting = activeSession; sessionsMenu = false }
                    )
                }
            }
        }
    }
    HorizontalDivider(color = C.Border.copy(alpha = 0.7f), thickness = 1.dp)

    renaming?.let { session ->
        var title by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renomear sessão") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Título") }) },
            confirmButton = { TextButton(onClick = { vm.renameSession(session, title); renaming = null }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }
        )
    }
    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir sessão?") },
            text = { Text("A conversa ‘${session.title}’ será apagada. Os arquivos do projeto não são afetados.") },
            confirmButton = { TextButton(onClick = { vm.deleteSession(session); deleting = null }) { Text("Excluir", color = C.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

// ---------- Boas-vindas ----------

@Composable
private fun WelcomePanel(vm: AgentViewModel, compact: Boolean, onPickZip: () -> Unit, onModelClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 20.dp else 50.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(132.dp).background(Brush.radialGradient(listOf(Color(0x336B7FF0), Color.Transparent)), CircleShape))
            BrandMark(62.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "O que vamos construir hoje?",
            style = MaterialTheme.typography.headlineSmall.copy(brush = C.Brand),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Agente com ferramentas, arquivos e memória no projeto ${vm.activeProject.name}.",
            color = C.Dim, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Pill(Icons.Outlined.Memory, vm.activeModelLabel, onClick = onModelClick)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuggestionCard(Icons.Outlined.Archive, "Analisar um ZIP", "Extrai e estuda o código do projeto", Modifier.weight(1f).fillMaxHeight(), onPickZip)
            SuggestionCard(Icons.Outlined.Code, "Revisar o projeto", "Encontra bugs e propõe melhorias", Modifier.weight(1f).fillMaxHeight()) {
                vm.inputText = "Leia os arquivos do projeto, encontre bugs e problemas e proponha melhorias priorizadas."
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuggestionCard(Icons.Outlined.AutoAwesome, "Criar do zero", "Descreva a ideia e ele cria os arquivos", Modifier.weight(1f).fillMaxHeight()) {
                vm.inputText = "Crie um projeto novo do zero com a seguinte ideia: "
            }
            SuggestionCard(Icons.Outlined.Search, "Pesquisar na web", "Busca fontes e resume com links", Modifier.weight(1f).fillMaxHeight()) {
                vm.inputText = "Pesquise na web e faça um resumo com fontes sobre: "
            }
        }
        if (vm.providers.none { it.enabled && it.baseUrl.isNotBlank() }) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.tab = AgentViewModel.Tab.SETTINGS }) {
                Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Configurar o primeiro provedor")
            }
        }
    }
}

@Composable
private fun SuggestionCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = C.Surface, border = BorderStroke(1.dp, C.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(C.AccentBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = C.Accent, modifier = Modifier.size(19.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = C.Text, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = C.Dim, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
        }
    }
}

// ---------- Mensagens ----------

@Composable
private fun MessageList(vm: AgentViewModel, compact: Boolean, modifier: Modifier) {
    val listState = rememberLazyListState()
    val lastAssistantId = vm.messages.lastOrNull { it.role == "assistant" }?.id
    val total = vm.messages.size + if (vm.isSending) 1 else 0
    LaunchedEffect(total) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 32.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        items(vm.messages, key = { it.id }) { message ->
            MessageItem(
                message = message,
                isLastAssistant = message.id == lastAssistantId && !vm.isSending,
                onRetry = { vm.retryLast() },
                onDelete = { vm.deleteMessage(message.id) }
            )
        }
        if (vm.isSending) item(key = "status") { StatusRow(vm) }
    }
}

@Composable
private fun StatusRow(vm: AgentViewModel) {
    var showActivity by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = C.Surface,
        border = BorderStroke(1.dp, C.AccentStrong.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(28.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DSH Agent", color = C.Text, style = MaterialTheme.typography.labelLarge)
                        PulseDots()
                    }
                    Text(
                        vm.statusText.ifBlank { "Trabalhando em segundo plano…" },
                        color = C.Dim, style = MaterialTheme.typography.labelSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { showActivity = !showActivity }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(if (showActivity) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(if (showActivity) "Ocultar" else "Atividade")
                }
                FilledTonalIconButton(
                    onClick = { vm.stopGeneration() },
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF3A1E24), contentColor = C.Danger)
                ) {
                    Icon(Icons.Outlined.Stop, "Parar", modifier = Modifier.size(18.dp))
                }
            }
            if (showActivity) {
                HorizontalDivider(color = C.Border, modifier = Modifier.padding(vertical = 9.dp))
                Text("Etapas e ações do agente", color = C.Text, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    "O painel mostra progresso, ferramentas e troca de provedor. Ele não revela a cadeia de pensamento privada do modelo; quando disponível, aparecem apenas estados e resumos operacionais.",
                    color = C.Dim, style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(7.dp))
                val events = vm.agentActivity.takeLast(8)
                if (events.isEmpty()) {
                    Text("Aguardando a primeira atualização…", color = C.Dim, style = MaterialTheme.typography.labelSmall)
                } else {
                    events.forEachIndexed { index, event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("${index + 1}.", color = C.Accent, style = MaterialTheme.typography.labelSmall)
                            Text(event, color = C.Text2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: ChatEntry, isLastAssistant: Boolean, onRetry: () -> Unit, onDelete: () -> Unit) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 1f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 9.dp)
                ) {
                    BrandMark(24.dp)
                    Text("DSH Agent", style = MaterialTheme.typography.labelLarge, color = C.Text)
                    if (message.model.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(50), color = C.Surface3) {
                            Text(
                                message.model, style = MaterialTheme.typography.labelSmall, color = C.Dim,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(max = 180.dp)
                            )
                        }
                    }
                }
                if (message.tools.isNotEmpty()) ToolActivity(message.tools)
            }
            if (isUser) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp),
                    color = C.AccentBg,
                    border = BorderStroke(1.dp, C.AccentStrong.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                        if (message.attachmentNames.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                message.attachmentNames.forEach { name -> AttachmentTag(name) }
                            }
                        }
                        if (message.content.isNotBlank()) {
                            SelectionContainer {
                                Text(message.content, color = C.Text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            } else {
                if (message.content.isNotBlank()) {
                    SelectionContainer { MarkdownText(message.content) }
                }
                if (message.attachmentNames.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) {
                        message.attachmentNames.forEach { name -> AttachmentTag(name) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                SmallAction(Icons.Outlined.ContentCopy, "Copiar") { clipboard.setText(AnnotatedString(message.content)) }
                if (isLastAssistant) SmallAction(Icons.Outlined.Replay, "Tentar novamente", onRetry)
                SmallAction(Icons.Outlined.DeleteOutline, "Excluir", onDelete)
            }
        }
    }
}

@Composable
private fun AttachmentTag(name: String) {
    Surface(color = C.Surface2, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, C.Border)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(fileIconFor(name), null, tint = C.Accent2, modifier = Modifier.size(15.dp))
            Text(name, color = C.Text2, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, description, tint = C.Dim.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ToolActivity(tools: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(14.dp), color = C.Surface,
        border = BorderStroke(1.dp, C.Border),
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Build, null, tint = C.Accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("${tools.size} ação(ões) do agente", color = C.Text2, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = C.Dim, modifier = Modifier.size(16.dp))
            }
            if (expanded) {
                tools.forEach { line ->
                    val name = line.substringBefore(':')
                    val detail = line.substringAfter(':', "").trim()
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(name, color = C.Accent, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        if (detail.isNotEmpty()) {
                            Text(detail, color = C.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Compositor ----------

@Composable
private fun Composer(vm: AgentViewModel, compact: Boolean, onPickFiles: () -> Unit, onPickZip: () -> Unit, onModelClick: () -> Unit) {
    var attachMenu by remember { mutableStateOf(false) }
    val canSend = vm.inputText.isNotBlank() || vm.pendingAttachments.isNotEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 10.dp else 26.dp).padding(bottom = 8.dp, top = 6.dp),
        shape = RoundedCornerShape(26.dp),
        color = C.Surface2,
        border = if (canSend) BorderStroke(1.dp, C.Brand) else BorderStroke(1.dp, C.Border),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            BasicTextField(
                value = vm.inputText,
                onValueChange = { vm.inputText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 150.dp).padding(top = 6.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.Text),
                cursorBrush = SolidColor(C.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box {
                        if (vm.inputText.isEmpty()) {
                            Text("Peça algo ao agente ou anexe um ZIP…", color = C.Dim, style = MaterialTheme.typography.bodyLarge)
                        }
                        inner()
                    }
                }
            )
            if (vm.pendingAttachments.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vm.pendingAttachments.forEach { uri -> AttachmentChip(uri) { vm.removeAttachment(uri) } }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(C.Surface3).clickable { attachMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Add, "Adicionar anexos", tint = C.Text2, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Arquivos e mídia") },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                            onClick = { attachMenu = false; onPickFiles() }
                        )
                        DropdownMenuItem(
                            text = { Text("Projeto ZIP") },
                            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                            onClick = { attachMenu = false; onPickZip() }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Pill(Icons.Outlined.Memory, vm.activeModelLabel, onClick = onModelClick, maxTextWidth = 150.dp)
                    ThinkingPicker(vm)
                }
                Spacer(Modifier.width(8.dp))
                if (vm.isSending) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF3A1E24)).clickable { vm.stopGeneration() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Stop, "Parar", tint = C.Danger, modifier = Modifier.size(21.dp))
                    }
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (canSend) C.Brand else SolidColor(C.Surface3))
                            .clickable(enabled = canSend) { vm.sendMessage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ArrowUpward, "Enviar", tint = if (canSend) Color.White else C.Dim, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val label = remember(uri) { Attachments.displayName(context, uri) }
    val isZip = Attachments.kindOfName(label) == Attachments.Kind.ZIP
    Surface(
        color = if (isZip) C.AccentBg else C.Surface3,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (isZip) C.AccentStrong.copy(alpha = 0.5f) else C.BorderStrong.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(fileIconFor(label), null, tint = C.Accent2, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = C.Text2, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            if (isZip) {
                Spacer(Modifier.width(5.dp))
                Text("extrai no projeto", color = C.Accent, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Outlined.Close, "Remover anexo", tint = C.Dim, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ThinkingPicker(vm: AgentViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Pill(Icons.Outlined.Tune, vm.thinkingLevel, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Off", "Low", "Medium", "High", "Max").forEach { level ->
                val hint = when (level) {
                    "Off" -> "Resposta direta"
                    "Low" -> "Mais rápido"
                    "Medium" -> "Equilibrado"
                    "High" -> "Análise cuidadosa"
                    else -> "Esforço máximo quando compatível"
                }
                DropdownMenuItem(
                    text = { Column { Text(level); Text(hint, style = MaterialTheme.typography.labelSmall, color = C.Dim) } },
                    leadingIcon = { if (vm.thinkingLevel == level) Icon(Icons.Outlined.Check, null, tint = C.Accent) },
                    onClick = { vm.updateThinkingLevel(level); expanded = false }
                )
            }
        }
    }
}
