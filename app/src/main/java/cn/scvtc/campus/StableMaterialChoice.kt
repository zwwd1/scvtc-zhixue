package cn.scvtc.campus

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/** Apply choices after the Material popup leaves composition, so replacing the UI owner is safe. */
@Composable
fun StableMaterialChoice(
    items: List<String>, selected: Int, modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit, anchor: @Composable (() -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Int?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val latestSelect by rememberUpdatedState(onSelect)
    val latestSelected by rememberUpdatedState(selected)
    val latestItems by rememberUpdatedState(items)
    Box(modifier) {
        anchor { pending = null; generation++; expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { pending = null; expanded = false }) {
            DisposableEffect(Unit) {
                onDispose {
                    val choice = pending
                    val closingGeneration = generation
                    pending = null
                    if (choice != null) scope.launch {
                        withFrameNanos { }
                        if (!expanded && generation == closingGeneration &&
                            choice in latestItems.indices && choice != latestSelected) latestSelect(choice)
                    }
                }
            }
            items.forEachIndexed { index, label ->
                DropdownMenuItem(text = { Text(if (index == selected) "✓ $label" else label) },
                    onClick = { pending = index; expanded = false })
            }
        }
    }
}
