package com.lippezin.dshagent.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lippezin.dshagent.data.Project
import com.lippezin.dshagent.data.formatBytes
import com.lippezin.dshagent.storage.FileEntry

private val ZIP_OPEN_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream")

private data class TreeRow(val entry: FileEntry, val depth: Int)

private fun visibleRows(entries: List<FileEntry>, expanded: Set<String>, query: String): List<TreeRow> {
    if (query.isNotBlank()) {
        return entries.filter { !it.isDir && it.path.contains(query.trim(), ignoreCase = true) }.take(300).map { TreeRow(it, 0) }
    }
    val children = entries.groupBy { it.path.substringBeforeLast('/', "") }
    val out = ArrayList<TreeRow>()
    fun walk(parent: String, depth: Int) {
        val list = children[parent].orEmpty().sortedWith(compareBy<FileEntry>({ !it.isDir }, { it.path.lowercase() }))
        for (entry in list) {
            out += TreeRow(entry, depth)
            if (entry.isDir && entry.path in expanded) walk(entry.path, depth + 1)
        }
    }
    walk("", 0)
    return out
}

@Composable
fun ProjectsScreen(vm: AgentViewModel, modifier: Modifier = Modifier) {
    var createProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFilePath by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importZipIntoProject(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportProjectZip(uri)
    }

    val rows = remember(vm.fileEntries, expanded, query) { visibleRows(vm.fileEntries, expanded, query) }
    val fileCount = vm.fileEntries.count { !it.isDir }
    val folderCount = vm.fileEntries.count { it.isDir }
    val totalBytes = vm.fileEntries.filter { !it.isDir }.sumOf { it.size }

    Column(modifier.fillMaxSize()) {
        ScreenHeading(
            title = "Projetos",
            subtitle = "Cada projeto tem seus próprios arquivos, conversas e memória.",
            trailing = {
                FilledTonalButton(onClick = { newProjectName = ""; createProjectDialog = true }) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Novo")
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    selected = project.id == vm.activeProjectId,
                    onOpen = { vm.selectProject(project) },
                    onRename = { renaming = project },
                    onDelete = { deleting = project }
                )
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text("Arquivos de ${vm.activeProject.name}", style = MaterialTheme.typography.titleMedium, color = C.Text2)
                Spacer(Modifier.height(3.dp))
                Text(
                    "$fileCount arquivo(s) · $folderCount pasta(s) · ${formatBytes(totalBytes)}",
                    color = C.Dim, style = MaterialTheme.typography.bodySmall
                )
                Text("Local: ${vm.workspaceLabel} / DSH Projects / ${vm.activeProjectId.take(8)}…", color = Color(0xFF7A7F96), style = MaterialTheme.typography.labelSmall)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(Icons.Outlined.FileUpload, "Importar ZIP", Modifier.weight(1f), enabled = !vm.filesBusy) { zipPicker.launch(ZIP_OPEN_TYPES) }
                    ActionButton(Icons.Outlined.FileDownload, "Exportar ZIP", Modifier.weight(1f), enabled = !vm.filesBusy && fileCount > 0) {
                        exportPicker.launch(vm.activeProject.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "projeto" } + ".zip")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(Icons.Outlined.NoteAdd, "Novo arquivo", Modifier.weight(1f)) { newFilePath = ""; newFileDialog = true }
                    ActionButton(Icons.Outlined.Refresh, "Atualizar", Modifier.weight(1f)) { vm.refreshProjectFiles() }
                }
                if (vm.filesBusy) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = C.Accent)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Buscar arquivo pelo nome") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Limpar") } }
                )
            }
            if (vm.fileEntries.isEmpty()) {
                item {
                    Surface(color = C.Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF20232D))) {
                        Column(Modifier.fillMaxWidth().padding(19.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Description, null, tint = Color(0xFF7D8399), modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(7.dp))
                            Text("Nenhum arquivo ainda", color = Color(0xFFAEB2C0))
                            Text(
                                "Importe um ZIP, crie um arquivo ou peça ao agente para trabalhar neste projeto.",
                                color = Color(0xFF7F859B), style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                items(rows, key = { "row-${it.entry.path}" }) { row ->
                    TreeRowItem(
                        row = row,
                        expanded = row.entry.path in expanded,
                        flat = query.isNotBlank(),
                        onClick = {
                            if (row.entry.isDir) {
                                expanded = if (row.entry.path in expanded) expanded - row.entry.path else expanded + row.entry.path
                            } else vm.openProjectFile(row.entry.path)
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (createProjectDialog) {
        AlertDialog(
            onDismissRequest = { createProjectDialog = false },
            title = { Text("Criar projeto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("O agente usará uma pasta isolada e um arquivo MEMORY.md para cada projeto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = newProjectName, onValueChange = { newProjectName = it }, label = { Text("Nome do projeto") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.createProject(newProjectName); createProjectDialog = false }) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { createProjectDialog = false }) { Text("Cancelar") } }
        )
    }
    renaming?.let { project ->
        var name by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renomear projeto") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nome") }) },
            confirmButton = { TextButton(onClick = { vm.renameProject(project, name); renaming = null }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }
        )
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir projeto?") },
            text = { Text("‘${project.name}’, todos os seus arquivos e conversas serão apagados. Exporte um ZIP antes se quiser guardar uma cópia. Essa ação não pode ser desfeita.") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(project); deleting = null }) { Text("Excluir", color = C.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
    if (newFileDialog) {
        AlertDialog(
            onDismissRequest = { newFileDialog = false },
            title = { Text("Novo arquivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Use barras para criar pastas, por exemplo src/Main.kt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = newFilePath, onValueChange = { newFilePath = it }, label = { Text("Caminho do arquivo") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.createProjectFile(newFilePath); newFileDialog = false }) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { newFileDialog = false }) { Text("Cancelar") } }
        )
    }
    vm.openedFileName?.let { name -> FileViewer(vm, name) }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ProjectCard(project: Project, selected: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(17.dp),
        color = if (selected) C.AccentBg else C.Surface,
        border = BorderStroke(1.dp, if (selected) Color(0xFF47558A) else C.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 15.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF222530), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.FolderOpen, null, tint = Color(0xFFC5D0F7), modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, color = C.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (selected) "Projeto ativo" else "Toque para abrir", color = C.Dim, style = MaterialTheme.typography.labelSmall)
            }
            if (selected) Icon(Icons.Outlined.TaskAlt, null, tint = Color(0xFFAEBFFD))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "Opções", tint = Color(0xFF878CA1)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renomear") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Excluir", color = C.Danger) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = C.Danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun TreeRowItem(row: TreeRow, expanded: Boolean, flat: Boolean, onClick: () -> Unit) {
    val entry = row.entry
    val name = entry.path.substringAfterLast('/')
    val parent = entry.path.substringBeforeLast('/', "")
    Surface(
        onClick = onClick,
        color = C.Surface, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF20232D)),
        modifier = Modifier.fillMaxWidth().padding(start = (row.depth * 14).dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.isDir) {
                Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, tint = C.Dim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.Folder, null, tint = Color(0xFFD9C48A), modifier = Modifier.size(19.dp))
            } else {
                Spacer(Modifier.width(22.dp))
                Icon(fileIconFor(name), null, tint = Color(0xFFACB9E7), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color(0xFFD4D6DD), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (flat && parent.isNotEmpty()) {
                    Text(parent, color = Color(0xFF7A7F96), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!entry.isDir) Text(formatBytes(entry.size), color = Color(0xFF7A7F96), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FileViewer(vm: AgentViewModel, name: String) {
    val clipboard = LocalClipboardManager.current
    var editing by remember(name) { mutableStateOf(false) }
    var draft by remember(name, vm.openedFileContent) { mutableStateOf(vm.openedFileContent) }
    var confirmDelete by remember(name) { mutableStateOf(false) }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp)

    Dialog(onDismissRequest = { vm.closeProjectFile() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = C.Bg) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.closeProjectFile() }) { Icon(Icons.Outlined.Close, "Fechar", tint = C.Text2) }
                    Column(Modifier.weight(1f)) {
                        Text(name.substringAfterLast('/'), color = C.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (name.contains('/')) Text(name.substringBeforeLast('/'), color = C.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(if (editing) draft else vm.openedFileContent)) }) {
                        Icon(Icons.Outlined.ContentCopy, "Copiar", tint = C.Dim)
                    }
                    if (!vm.openedFileReadOnly) {
                        if (editing) {
                            TextButton(onClick = { vm.saveOpenedFile(draft); editing = false }) { Text("Salvar") }
                        } else {
                            IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Editar", tint = C.Dim) }
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = C.Danger) }
                }
                HorizontalDivider(color = C.Border)
                if (editing) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = mono.copy(color = Color(0xFFE7EBF7)),
                        cursorBrush = SolidColor(C.Accent),
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)
                    )
                } else {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            vm.openedFileContent.ifEmpty { "(arquivo vazio)" },
                            style = mono.copy(color = Color(0xFFD8DEF4)),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir arquivo?") },
            text = { Text("‘$name’ será apagado do projeto. Essa ação não pode ser desfeita.") },
            confirmButton = { TextButton(onClick = { vm.deleteProjectPath(name); confirmDelete = false }) { Text("Excluir", color = C.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}
