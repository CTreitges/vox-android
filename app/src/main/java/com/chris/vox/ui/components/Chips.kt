package com.chris.vox.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chris.vox.R
import com.chris.vox.ui.nav.StepState
import com.chris.vox.ui.theme.vox

/**
 * Nicht-interaktiver Status-Chip: Farbe + Icon + Text (Spec §5.6: Status nie nur ueber Farbe).
 * [live] macht ihn zur Live-Region (Test-Ergebnis, Hero-Hinweis).
 */
@Composable
fun StatusChip(
    text: String,
    @DrawableRes icon: Int,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    Row(
        modifier
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            .background(container, CircleShape)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoxIcon(icon, null, Modifier.size(18.dp), onContainer)
        Text(text, style = MaterialTheme.typography.labelLarge, color = onContainer)
    }
}

/** Chip zum Schritt-Zustand (Spec §2.2): Erledigt / Fehlt noch / Optional / Uebersprungen. */
@Composable
fun StepStateChip(state: StepState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val vox = MaterialTheme.vox
    when (state) {
        StepState.DONE -> StatusChip(
            androidx.compose.ui.res.stringResource(R.string.setup_chip_done),
            R.drawable.ic_check_circle, vox.successContainer, vox.onSuccessContainer, modifier,
        )
        StepState.OPEN -> StatusChip(
            androidx.compose.ui.res.stringResource(R.string.setup_chip_open),
            R.drawable.ic_warning, vox.warningContainer, vox.onWarningContainer, modifier,
        )
        StepState.OPTIONAL -> StatusChip(
            androidx.compose.ui.res.stringResource(R.string.setup_chip_optional),
            R.drawable.ic_info, cs.secondaryContainer, cs.onSecondaryContainer, modifier,
        )
        StepState.SKIPPED -> StatusChip(
            androidx.compose.ui.res.stringResource(R.string.setup_chip_skipped),
            R.drawable.ic_remove_circle_outline, cs.surfaceContainerHigh, cs.onSurfaceVariant, modifier,
        )
    }
}
