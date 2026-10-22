package com.lippezin.dshagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(vm: AgentViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val usable = vm.providers.filter { it.enabled }

    fun close(after: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            after()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = C.Surface2) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text("Escolher modelo", style = MaterialTheme.typography.titleLarge, color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Buscar modelo ou provedor") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }
            )
            Spacer(Modifier.height(8.dp))
            if (usable.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhum provedor ativo. Cadastre um em Configurações.", color = C.Dim, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    usable.forEach { provider ->
                        val models = provider.allModels.filter {
                            query.isBlank() || it.contains(query, ignoreCase = true) || provider.label.contains(query, ignoreCase = true)
                        }
                        val showDefault = provider.allModels.isEmpty() &&
                            (query.isBlank() || provider.label.contains(query, ignoreCase = true))
                        if (models.isNotEmpty() || showDefault) {
                            item(key = "h-${provider.id}") {
                                Column(Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)) {
                                    Text(provider.label, color = C.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        provider.baseUrl.removePrefix("https://").removePrefix("http://") + " · " +
                                            if (provider.allModels.isEmpty()) "modelo padrão do roteador" else "${provider.allModels.size} modelo(s)",
                                        color = C.Dim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (showDefault) item(key = "d-${provider.id}") {
                                val selectedDefault = provider.id == vm.selectedProviderId && vm.activeModel == null
                                Surface(
                                    onClick = { vm.selectModel(provider.id, ""); close() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selectedDefault) C.AccentBg else Color.Transparent,
                                    border = BorderStroke(1.dp, if (selectedDefault) Color(0xFF47558A) else Color.Transparent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Modelo padrão do roteador", color = C.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        if (selectedDefault) Icon(Icons.Outlined.Check, null, tint = C.Accent, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                            items(models, key = { "m-${provider.id}-$it" }) { model ->
                                val selected = provider.id == vm.selectedProviderId && model == vm.activeModel
                                Surface(
                                    onClick = { vm.selectModel(provider.id, model); close() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) C.AccentBg else Color.Transparent,
                                    border = BorderStroke(1.dp, if (selected) Color(0xFF47558A) else Color.Transparent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(model, color = C.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (selected) Icon(Icons.Outlined.Check, null, tint = C.Accent, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = C.Border, modifier = Modifier.padding(vertical = 6.dp))
            TextButton(onClick = { close { vm.tab = AgentViewModel.Tab.SETTINGS } }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gerenciar provedores e modelos")
            }
        }
    }
}
