package com.chris.whisperloom.ui.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** Zeile + Switch; die ganze Zeile schaltet (Touch-Ziel, TalkBack liest Zustand ueber die Rolle). */
@Composable
fun SwitchRow(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    LoomRow(
        headline = headline,
        supporting = supporting,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}
