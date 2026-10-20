package com.lippezin.dshagent

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lippezin.dshagent.ui.AgentViewModel
import com.lippezin.dshagent.ui.DshAgentApp
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var agentViewModel: AgentViewModel? = null
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 7421) runOnUiThread {
            agentViewModel?.refreshShellStatus()
            agentViewModel?.showNotice(
                if (grantResult == PackageManager.PERMISSION_GRANTED) "Permissão Shizuku concedida."
                else "Permissão Shizuku negada."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        window.statusBarColor = android.graphics.Color.rgb(17, 18, 20)
        window.navigationBarColor = android.graphics.Color.rgb(17, 18, 20)
        setContent {
            val vm: AgentViewModel = viewModel()
            SideEffect { agentViewModel = vm }
            val workspacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) vm.setWorkspace(uri)
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9DB7E8),
                    onPrimary = Color(0xFF11151D),
                    secondary = Color(0xFFB7C2D6),
                    background = Color(0xFF101113),
                    surface = Color(0xFF191B1F),
                    surfaceVariant = Color(0xFF27292E),
                    onSurface = Color(0xFFE8E9EC),
                    onSurfaceVariant = Color(0xFFB0B4BD),
                    outline = Color(0xFF3C3F46)
                )
            ) {
                DshAgentApp(vm = vm, onPickWorkspace = { workspacePicker.launch(null) })
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        agentViewModel = null
        super.onDestroy()
    }
}
