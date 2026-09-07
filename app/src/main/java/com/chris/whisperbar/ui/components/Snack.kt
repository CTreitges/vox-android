package com.chris.whisperbar.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Snackbar statt Toast in allen Compose-Screens (Spec §1.3); optionale Aktion. */
class SnackController(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            val result = host.showSnackbar(
                message = message,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

@Composable
fun rememberSnack(): SnackController {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(host, scope) { SnackController(host, scope) }
}
