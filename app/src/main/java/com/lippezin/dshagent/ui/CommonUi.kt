package com.lippezin.dshagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ScreenHeading(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFF1F2F5))
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA2AC))
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(15.dp))
        HorizontalDivider(color = Color(0xFF292B30))
    }
}

@Composable
fun SettingsSectionTitle(title: String, supporting: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE0E3E9), fontWeight = FontWeight.Medium)
        if (supporting != null) {
            Spacer(Modifier.height(3.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9299A5))
        }
    }
}
