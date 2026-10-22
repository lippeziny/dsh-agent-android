package com.lippezin.dshagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lippezin.dshagent.data.ShellApproval

@Composable
fun DshAgentApp(vm: AgentViewModel, onPickWorkspace: () -> Unit) {
    val compact = LocalConfiguration.current.screenWidthDp < 700
    BackHandler(enabled = vm.tab != AgentViewModel.Tab.CHAT) { vm.tab = AgentViewModel.Tab.CHAT }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (compact) {
            Scaffold(containerColor = C.Bg, bottomBar = { BottomNavigation(vm) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    MainPane(vm, compact = true, onPickWorkspace = onPickWorkspace)
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                DesktopSidebar(vm, Modifier.width(260.dp).fillMaxHeight(), onPickWorkspace)
                VerticalDivider(modifier = Modifier.fillMaxHeight(), color = Color(0xFF1E202A))
                MainPane(vm, compact = false, onPickWorkspace = onPickWorkspace, modifier = Modifier.weight(1f))
            }
        }
        vm.approval?.let { request ->
            ApprovalDialog(request, onConfirm = { vm.resolveShellApproval(true) }, onCancel = { vm.resolveShellApproval(false) })
        }
        vm.notice?.let { message ->
            val host = remember { SnackbarHostState() }
            LaunchedEffect(message) {
                host.showSnackbar(message, withDismissAction = true)
                vm.clearNotice()
            }
            SnackbarHost(
                hostState = host,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 92.dp else 18.dp)
            ) { data -> Snackbar(snackbarData = data, containerColor = Color(0xFF20232D), contentColor = Color.White) }
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
    val items = listOf(
        Triple(AgentViewModel.Tab.CHAT, Icons.Outlined.ChatBubbleOutline, "Chat"),
        Triple(AgentViewModel.Tab.PROJECTS, Icons.Outlined.FolderOpen, "Projetos"),
        Triple(AgentViewModel.Tab.TOOLS, Icons.Outlined.Terminal, "Ferramentas"),
        Triple(AgentViewModel.Tab.SETTINGS, Icons.Outlined.Settings, "Ajustes")
    )
    Box(Modifier.fillMaxWidth().background(C.Bg).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(26.dp), color = C.Surface2, border = BorderStroke(1.dp, C.Border), shadowElevation = 6.dp) {
            Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { (tab, icon, label) ->
                    val selected = vm.tab == tab
                    Row(
                        Modifier.clip(RoundedCornerShape(21.dp))
                            .background(if (selected) C.AccentBg else Color.Transparent)
                            .clickable { vm.tab = tab }
                            .padding(horizontal = if (selected) 15.dp else 13.dp, vertical = 10.dp)
                            .animateContentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, label, tint = if (selected) C.Accent2 else C.Dim, modifier = Modifier.size(21.dp))
                        if (selected) {
                            Spacer(Modifier.width(7.dp))
                            Text(label, color = C.Accent2, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebar(vm: AgentViewModel, modifier: Modifier, onPickWorkspace: () -> Unit) {
    Column(modifier.background(C.Sidebar).padding(horizontal = 14.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrandMark(38.dp)
            Column(Modifier.weight(1f)) {
                Text("DSH Agent", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(vm.activeModelLabel, style = MaterialTheme.typography.labelSmall, color = Color(0xFF999DAE), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = vm::newSession, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242733), contentColor = Color.White)
        ) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nova sessão")
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Projetos", color = Color(0xFFA2A6B6), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onPickWorkspace, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.FolderOpen, "Escolher pasta de trabalho", tint = Color(0xFFB6B9C5), modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            vm.projects.forEach { project ->
                TextButton(
                    onClick = { vm.selectProject(project) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (vm.activeProjectId == project.id) Color.White else Color(0xFFC4C7D1))
                ) {
                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                if (vm.activeProjectId == project.id) {
                    vm.sessions.forEach { session ->
                        TextButton(
                            onClick = { vm.selectSession(session) }, modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = if (vm.activeSessionId == session.id) Color(0xFFC2CFFF) else Color(0xFF999DAE))
                        ) {
                            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        NavigationDrawerItem(label = { Text("Chat") }, selected = vm.tab == AgentViewModel.Tab.CHAT, onClick = { vm.tab = AgentViewModel.Tab.CHAT }, icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) })
        NavigationDrawerItem(label = { Text("Projetos") }, selected = vm.tab == AgentViewModel.Tab.PROJECTS, onClick = { vm.tab = AgentViewModel.Tab.PROJECTS }, icon = { Icon(Icons.Outlined.Folder, null) })
        NavigationDrawerItem(label = { Text("Ferramentas") }, selected = vm.tab == AgentViewModel.Tab.TOOLS, onClick = { vm.tab = AgentViewModel.Tab.TOOLS }, icon = { Icon(Icons.Outlined.Extension, null) })
        NavigationDrawerItem(label = { Text("Configurações") }, selected = vm.tab == AgentViewModel.Tab.SETTINGS, onClick = { vm.tab = AgentViewModel.Tab.SETTINGS }, icon = { Icon(Icons.Outlined.Settings, null) })
    }
}

@Composable
private fun ApprovalDialog(request: ShellApproval, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(request.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.description)
                Surface(color = Color(0xFF09090C), shape = MaterialTheme.shapes.medium) {
                    Text(request.command, modifier = Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFFD7E0FF), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(request.confirmLabel, color = Color(0xFFC2CFFF)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        containerColor = Color(0xFF171920)
    )
}
