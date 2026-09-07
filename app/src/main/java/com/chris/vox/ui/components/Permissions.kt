package com.chris.vox.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chris.vox.ui.state.LocalAppEnv

/**
 * Laufzeit-Berechtigung anfragen. Nach einer Ablehnung ohne Rationale (= "nicht mehr fragen")
 * fuehrt [request] in die App-Einstellungen (Spec §2.2 Schritt 3/6); [deniedPermanently]
 * schaltet Text und Button-Beschriftung um.
 */
class PermissionRequest(val deniedPermanently: Boolean, val request: () -> Unit)

@Composable
fun rememberPermissionRequest(permission: String): PermissionRequest {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    var deniedPermanently by rememberSaveable(permission) { mutableStateOf(false) }
    val activity = ctx.findActivity()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && activity != null && !activity.shouldShowRequestPermissionRationale(permission)) {
            deniedPermanently = true
        }
        env.refreshStatus()
    }
    return PermissionRequest(deniedPermanently) {
        if (deniedPermanently) SystemIntents.open(ctx, SystemIntents.appDetails(ctx)) else launcher.launch(permission)
    }
}
