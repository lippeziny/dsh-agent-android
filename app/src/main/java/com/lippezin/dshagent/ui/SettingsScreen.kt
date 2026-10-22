package com.lippezin.dshagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lippezin.dshagent.ai.ModelDiscovery
import com.lippezin.dshagent.data.ProviderConfig
import com.lippezin.dshagent.data.ProviderPresets
import com.lippezin.dshagent.data.isCleartextUrl
import com.lippezin.dshagent.data.isLocalOrPrivateHost
import com.lippezin.dshagent.data.isValidBaseUrl
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SettingsScreen(vm: AgentViewModel, onPickWorkspace: () -> Unit, modifier: Modifier = Modifier) {
    var editorOpen by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var deletingProvider by remember { mutableStateOf<ProviderConfig?>(null) }

    if (editorOpen) {
        ProviderEditor(
            editing = editingProvider,
            modifier = modifier,
            onClose = { editorOpen = false; editingProvider = null },
            onSave = { provider -> vm.saveProvider(provider); editorOpen = false; editingProvider = null }
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeading("Configurações", "Provedores, modelos, instruções, armazenamento e permissões.")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSectionTitle(
                "Provedores e modelos",
                "Cada provedor tem uma URL base, uma chave e vários modelos. Os demais provedores ativos são tentados automaticamente se o escolhido falhar."
            )
            vm.providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    selected = provider.id == vm.selectedProviderId,
                    activeModel = if (provider.id == vm.selectedProviderId) vm.activeModel else null,
                    onToggle = { vm.toggleProvider(provider.id, it) },
                    onEdit = { editingProvider = provider; editorOpen = true },
                    onDelete = { deletingProvider = provider }
                )
            }
            OutlinedButton(onClick = { editingProvider = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(7.dp))
                Text("Adicionar provedor")
            }
            Surface(color = C.Surface2, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF323646))) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFFB3C1F4), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fallback automático", color = Color(0xFFE1E2E7), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Se uma chave/provedor falhar (401, 402, 429, cota ou erro de servidor), o agente tenta o modelo padrão do próximo provedor ativo.",
                            color = Color(0xFF9A9EAF), style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            SettingsSectionTitle("Instruções personalizadas", "Enviadas ao modelo em toda conversa (estilo de resposta, linguagens preferidas, regras do seu fluxo).")
            OutlinedTextField(
                value = vm.customInstructions,
                onValueChange = vm::updateCustomInstructions,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                placeholder = { Text("Ex.: responda sempre em português, use Kotlin idiomático e explique mudanças antes de aplicá-las.") }
            )

            SettingsSectionTitle("Pasta de trabalho", "Os arquivos do agente ficam isolados por projeto.")
            Surface(color = C.Surface, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, C.Border)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, null, tint = Color(0xFFC0CBF3), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(vm.workspaceLabel, color = Color(0xFFE3E4E9), fontWeight = FontWeight.Medium)
                            Text("Cada projeto usa seu próprio diretório e MEMORY.md.", color = Color(0xFF8E93A7), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = onPickWorkspace, modifier = Modifier.fillMaxWidth()) { Text("Selecionar pasta do armazenamento") }
                    Text(
                        "Sem seleção, a pasta fica no armazenamento privado do app. Se escolher uma pasta, o app cria nela ‘DSH Projects’ e salva a permissão de acesso. Arquivos que já estavam no armazenamento interno não são migrados; use Exportar ZIP e Importar ZIP na aba Projetos.",
                        color = Color(0xFF8C91A5), style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SettingsSectionTitle("Ferramentas do agente", "Ative ou desative módulos (arquivos, memória, web, anexos, shell) na aba Ferramentas. O agente não tem limite de etapas por tarefa; use o botão Parar para interromper.")

            SettingsSectionTitle("Permissões e segurança")
            Surface(color = C.Surface, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, C.Border)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, null, tint = Color(0xFFC0CBF3), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Shizuku", color = Color(0xFFE5E6EA), fontWeight = FontWeight.Medium)
                            Text(vm.shellStatus, color = Color(0xFF9CA0B1), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::refreshShellStatus) { Text("Atualizar status") }
                        Button(onClick = vm::requestShizukuPermission) { Text("Solicitar permissão") }
                    }
                    Text(
                        "As chaves de API são cifradas com AES-GCM usando uma chave do Android Keystore. Não exporte logs que contenham chaves. O modelo recebe o texto e os anexos enviados para o provedor escolhido. Exclusões de arquivos e comandos shell pedidos pelo agente sempre exigem sua aprovação.",
                        color = Color(0xFF8C91A5), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            SettingsSectionTitle("Sobre")
            Text(
                "DSH Agent · 0.4.0\nAndroid nativo • Kotlin • Jetpack Compose\nMódulos externos carregáveis não estão ativados nesta versão.",
                color = Color(0xFF8C91A5), style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(22.dp))
        }
    }

    deletingProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text("Excluir provedor?") },
            text = { Text("A configuração ‘${provider.label}’, seus modelos e a chave salva no dispositivo serão removidos.") },
            confirmButton = { TextButton(onClick = { vm.deleteProvider(provider.id); deletingProvider = null }) { Text("Excluir", color = C.Danger) } },
            dismissButton = { TextButton(onClick = { deletingProvider = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    selected: Boolean,
    activeModel: String?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF191E2F) else C.Surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFF3B4875) else C.Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF21232E), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Key, null, tint = Color(0xFFC4CFF7), modifier = Modifier.size(19.dp)) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.label, color = Color(0xFFE9EAED), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (provider.allModels.isEmpty()) "modelo padrão do roteador" else "${provider.allModels.size} modelo(s) · padrão: ${provider.defaultModel}",
                        color = Color(0xFFB1B4C1), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        provider.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/') + " · " + if (provider.apiKey.isBlank()) "sem chave" else "chave configurada",
                        color = Color(0xFF82879C), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = provider.enabled, onCheckedChange = onToggle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected && activeModel != null) {
                    Text("Em uso: $activeModel", color = Color(0xFFBFCDFF), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(37.dp)) { Icon(Icons.Outlined.Edit, "Editar", tint = Color(0xFFB5C1ED), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(37.dp)) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = Color(0xFFD3A6A3), modifier = Modifier.size(19.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditor(editing: ProviderConfig?, modifier: Modifier, onClose: () -> Unit, onSave: (ProviderConfig) -> Unit) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()

    var label by remember(editing?.id) { mutableStateOf(editing?.label ?: "") }
    var baseUrl by remember(editing?.id) { mutableStateOf(editing?.baseUrl ?: "https://") }
    var apiKey by remember(editing?.id) { mutableStateOf(editing?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var enabled by remember(editing?.id) { mutableStateOf(editing?.enabled ?: true) }
    val selected: SnapshotStateList<String> = remember(editing?.id) {
        mutableStateListOf<String>().also { it.addAll(editing?.allModels ?: emptyList()) }
    }
    var defaultModel by remember(editing?.id) { mutableStateOf(editing?.defaultModel ?: "") }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchMessage by remember { mutableStateOf<String?>(null) }
    var fetchFailed by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var presetMenu by remember { mutableStateOf(false) }
    var defaultMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fetchModels() {
        fetching = true
        fetchMessage = null
        scope.launch {
            val result = ModelDiscovery.fetch(baseUrl, apiKey)
            fetching = false
            if (result.models.isEmpty()) {
                fetchFailed = true
                fetchMessage = "Não foi possível listar os modelos automaticamente: ${result.error ?: "sem resultado"}. Adicione os IDs manualmente abaixo."
            } else {
                fetchFailed = false
                fetched = result.models
                fetchMessage = "${result.models.size} modelos encontrados. Marque os que quer usar."
            }
        }
    }

    fun addManual() {
        manual.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { id ->
            if (id !in selected) selected.add(id)
        }
        manual = ""
    }

    val filtered = fetched.filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Fechar", tint = C.Text2) }
            Text(if (editing == null) "Novo provedor" else "Editar provedor", color = C.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = {
                when {
                    label.isBlank() -> error = "Informe o nome do provedor."
                    !isValidBaseUrl(baseUrl) -> error = "URL base inválida. Use http://host:porta/v1 (roteador local) ou https://… — em roteador local não esqueça a porta."
                    else -> {
                        val models = selected.toList()
                        onSave(
                            ProviderConfig(
                                id = editing?.id ?: UUID.randomUUID().toString(),
                                label = label.trim(),
                                baseUrl = baseUrl.trim().trimEnd('/'),
                                apiKey = apiKey.trim(),
                                model = defaultModel.takeIf { it in models } ?: models.firstOrNull().orEmpty(),
                                models = models,
                                enabled = enabled
                            )
                        )
                    }
                }
            }) { Text("Salvar") }
            Spacer(Modifier.width(8.dp))
        }
        HorizontalDivider(color = C.Border)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box {
                OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Começar de um modelo pronto")
                }
                DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                    ProviderPresets.all.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                label = if (preset.baseUrl == "https://") "" else preset.label
                                baseUrl = preset.baseUrl
                                if (preset.suggestedModel.isNotBlank() && preset.suggestedModel !in selected) selected.add(preset.suggestedModel)
                                presetMenu = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nome do provedor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("URL base da API") },
                supportingText = {
                    val risky = isCleartextUrl(baseUrl) && !isLocalOrPrivateHost(baseUrl)
                    Text(
                        if (risky) "⚠ HTTP fora da rede local: a chave seria enviada sem criptografia. Prefira HTTPS."
                        else "Ex.: https://codecraftapi.com/v1 ou http://localhost:PORTA/v1 (roteador local). O app usa {URL}/chat/completions e {URL}/models",
                        color = if (risky) C.Danger else Color.Unspecified
                    )
                },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it }, label = { Text("API key (opcional em roteador local)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showKey) "Ocultar" else "Mostrar")
                    }
                }
            )

            SettingsSectionTitle("Modelos", "Busque automaticamente na URL base ou digite os IDs manualmente. O modelo é opcional: sem nenhum modelo o app não envia o campo model e o roteador usa o padrão dele.")
            Button(
                onClick = { fetchModels() },
                enabled = !fetching && isValidBaseUrl(baseUrl),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (fetching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Outlined.Sync, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (fetching) "Buscando…" else "Buscar modelos automaticamente")
            }
            fetchMessage?.let {
                Text(it, color = if (fetchFailed) C.Danger else C.Ok, style = MaterialTheme.typography.bodySmall)
            }

            if (fetched.isNotEmpty()) {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Filtrar ${fetched.size} modelos") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { filtered.forEach { if (it !in selected) selected.add(it) } }) { Text("Marcar ${filtered.size}") }
                    OutlinedButton(onClick = { selected.clear() }) { Text("Limpar seleção") }
                }
                Surface(color = C.Surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, C.Border)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        filtered.take(60).forEach { model ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = model in selected,
                                    onCheckedChange = { checked ->
                                        if (checked) { if (model !in selected) selected.add(model) } else selected.remove(model)
                                    }
                                )
                                Text(model, color = C.Text2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (filtered.size > 60) {
                            Text("Mostrando 60 de ${filtered.size}. Refine o filtro para ver os demais.", color = C.Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = manual, onValueChange = { manual = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Adicionar modelo manualmente") },
                supportingText = { Text("Você pode colar vários IDs separados por vírgula ou espaço") },
                trailingIcon = { IconButton(onClick = { addManual() }, enabled = manual.isNotBlank()) { Icon(Icons.Outlined.Add, "Adicionar") } }
            )

            if (selected.isNotEmpty()) {
                Text("${selected.size} modelo(s) selecionado(s)", color = C.Text2, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selected.toList().forEach { model ->
                        Surface(
                            onClick = { selected.remove(model) },
                            shape = RoundedCornerShape(30.dp), color = C.AccentBg,
                            border = BorderStroke(1.dp, Color(0xFF47558A))
                        ) {
                            Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(model, color = C.Text2, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Outlined.Close, "Remover", tint = C.Dim, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { defaultMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Modelo padrão: ${defaultModel.takeIf { it in selected } ?: selected.first()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = defaultMenu, onDismissRequest = { defaultMenu = false }) {
                        selected.toList().forEach { model ->
                            DropdownMenuItem(text = { Text(model) }, onClick = { defaultModel = model; defaultMenu = false })
                        }
                    }
                }
                Text("O modelo padrão é o usado no fallback e na primeira seleção deste provedor.", color = C.Dim, style = MaterialTheme.typography.labelSmall)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Provedor ativo", color = C.Text2)
                    Text("Provedores ativos aparecem no seletor e entram no fallback", style = MaterialTheme.typography.labelSmall, color = C.Dim)
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            error?.let { Text(it, color = C.Danger, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
