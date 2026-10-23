package org.ntust.app.tigerduck.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A search field's leading icon, for its `leadingIcon`: the magnifier, which turns into a spinner
 * while [searching], so a search that waits on the network shows it is under way. Every search
 * field takes it, so all of them look alike, and one whose search becomes slow shows it.
 */
@Composable
fun SearchFieldIcon(searching: Boolean = false) {
    if (searching) {
        // The icon's own size and colour, so the field does not shift as it turns.
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
    } else {
        Icon(Icons.Filled.Search, contentDescription = null)
    }
}
