package com.lippezin.dshagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.lippezin.dshagent.data.ShellApproval

private val AppBg = Color(0xFF101113)
private val SidebarBg = Color(0xFF191A1C)

@Composable
fun DshAgentApp(vm: AgentViewModel, onPickWorkspace: () -> Unit) {
    val compact = LocalConfiguration.current.screenWidthDp < 700
    Box(Modifier.fillMaxSize().background(AppBg)) {
        if (compact) {
            Scaffold(
                containerColor = AppBg,
                bottomBar = { BottomNavigation(vm) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    MainPane(vm, compact = true, onPickWorkspace = onPickWorkspace)
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                DesktopSidebar(vm, Modifier.width(250.dp).fillMaxHeight(), onPickWorkspace)
                HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color(0xFF2B2D31))
                MainPane(vm, compact = false, onPickWorkspace = onPickWorkspace, modifier = Modifier.weight(1f))
            }
        }
        vm.approval?.let { request -> ShellApprovalDialog(request, onConfirm = { vm.resolveShellApproval(true) }, onCancel = { vm.resolveShellApproval(false) }) }
        vm.notice?.let { message ->
            SnackbarHost(
                hostState = rememberNoticeHost(message, vm),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = if (compact) 74.dp else 18.dp)
            ) { data -> Snackbar(snackbarData = data, containerColor = Color(0xFF2B3038), contentColor = Color.White) }
        }
    }
}

@Composable
private fun MainPane(vm: AgentViewModel, compact: Boolean, onPickWorkspace: () -> Unit, modifier: Modifier = Modifier) {
    when (vm.tab) {
        AgentViewModel.Tab.CHAT -> ChatScreen(vm, compact, modifier)
        AgentViewModel.Tab.PROJECTS -> ProjectsScreen(vm, modifier)
        AgentViewModel.Tab.TOOLS -> ToolsScreen(vm, modifier)
        AgentViewModel.Tab.SETTINGS -> SettingsScreen(vm, onPickWorkspace, modifier)
    }
}

@Composable
private fun BottomNavigation(vm: AgentViewModel) {
    NavigationBar(containerColor = SidebarBg, tonalElevation = 0.dp) {
        NavigationBarItem(selected = vm.tab == AgentViewModel.Tab.CHAT, onClick = { vm.tab = AgentViewModel.Tab.CHAT }, icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) }, label = { Text("Chat") })
        NavigationBarItem(selected = vm.tab == AgentViewModel.Tab.PROJECTS, onClick = { vm.tab = AgentViewModel.Tab.PROJECTS }, icon = { Icon(Icons.Outlined.FolderOpen, null) }, label = { Text("Projetos") })
        NavigationBarItem(selected = vm.tab == AgentViewModel.Tab.TOOLS, onClick = { vm.tab = AgentViewModel.Tab.TOOLS }, icon = { Icon(Icons.Outlined.Terminal, null) }, label = { Text("Ferramentas") })
        NavigationBarItem(selected = vm.tab == AgentViewModel.Tab.SETTINGS, onClick = { vm.tab = AgentViewModel.Tab.SETTINGS }, icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Ajustes") })
    }
}

@Composable
private fun DesktopSidebar(vm: AgentViewModel, modifier: Modifier, onPickWorkspace: () -> Unit) {
    Column(modifier.background(SidebarBg).padding(horizontal = 14.dp, vertical = 24.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("◉", color = Color(0xFFDAE5F9), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Text("DSH Agent", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Into the Unknown", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9DA2AA))
            }
            IconButton(onClick = { vm.tab = AgentViewModel.Tab.CHAT }) { Icon(Icons.Outlined.ChatBubbleOutline, "Nova sessão") }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = vm::newSession, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34363B), contentColor = Color.White)) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Nova sessão")
        }
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Workspaces", color = Color(0xFFA7AAB1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onPickWorkspace, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.FolderOpen, "Escolher pasta", tint = Color(0xFFB6BBC5), modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(5.dp))
        vm.projects.forEach { project ->
            TextButton(
                onClick = { vm.selectProject(project) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = if (vm.activeProjectId == project.id) Color.White else Color(0xFFC7C9CE))
            ) {
                Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text(project.name, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        NavigationDrawerItem(label = { Text("Ferramentas") }, selected = vm.tab == AgentViewModel.Tab.TOOLS, onClick = { vm.tab = AgentViewModel.Tab.TOOLS }, icon = { Icon(Icons.Outlined.Extension, null) })
        NavigationDrawerItem(label = { Text("Configurações") }, selected = vm.tab == AgentViewModel.Tab.SETTINGS, onClick = { vm.tab = AgentViewModel.Tab.SETTINGS }, icon = { Icon(Icons.Outlined.Settings, null) })
    }
}

@Composable
private fun ShellApprovalDialog(request: ShellApproval, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Autorizar comando shell?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.description)
                Surface(color = Color(0xFF0C0D0F), shape = MaterialTheme.shapes.medium) {
                    Text(request.command, modifier = Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFFCEE0FF), style = MaterialTheme.typography.bodySmall)
                }
                Text("O comando será executado com as permissões concedidas pelo Shizuku. Leia-o antes de autorizar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Executar", color = Color(0xFFB9D0FF)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        containerColor = Color(0xFF202227)
    )
}

@Composable
private fun rememberNoticeHost(message: String, vm: AgentViewModel): SnackbarHostState {
    val state = androidx.compose.runtime.remember { SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(message) {
        state.showSnackbar(message, withDismissAction = true)
        vm.clearNotice()
    }
    return state
}
