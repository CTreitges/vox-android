package com.chris.vox.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.vox.R
import com.chris.vox.ui.components.DetailScaffold
import com.chris.vox.ui.components.ScrollColumn
import com.chris.vox.ui.components.fileSize
import com.chris.vox.ui.components.rememberSnack
import com.chris.vox.ui.models.ModelListSection
import com.chris.vox.ui.nav.NavState
import com.chris.vox.ui.state.LocalAppEnv
import com.chris.vox.whisper.ModelDownloads
import com.chris.vox.whisper.ModelStore

/** E4 — Offline-Modelle (UX-Spec §2.7): Intro, Speicher, Engine-Umschalter, Modell-Liste, Quelle. */
@Composable
fun ModelsScreen(nav: NavState) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val snack = rememberSnack()
    val store = remember { ModelStore(ctx) }
    val states by ModelDownloads.states.collectAsStateWithLifecycle()
    // Nach Download/Loeschen aendert sich der Systemstatus (installedModels) -> Speicherzeile neu lesen.
    val used = remember(states, env.status) { store.usedBytes() }
    val free = remember(states, env.status) { store.freeBytes() }

    DetailScaffold(title = stringResource(R.string.models_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            Text(
                stringResource(R.string.models_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.models_storage, fileSize(used), fileSize(free)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EngineSwitch(snack, requireModelForOffline = true)
            ModelListSection(snack)
            Text(
                stringResource(R.string.models_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
