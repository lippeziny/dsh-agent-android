package com.lippezin.dshagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ChatScreen(vm: AgentViewModel) {
    // ...
    DropdownMenuItem(
        text = { Column { Text(level); Text(when (level) { "Off" -> "Resposta direta"; "Low" -> "Mais rápido"; "Medium" -> "Equilibrado"; "High" -> "Análise cuidadosa"; else -> "Esforço máximo quando compatível" }, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA2AD)) } },
        leadingIcon = { if (vm.thinkingLevel == level) Icon(Icons.Outlined.Check, null, tint = Color(0xFFB9D0FF)) },
        onClick = { vm.updateThinkingLevel(level); expanded = false }
    )
    // ...
}
