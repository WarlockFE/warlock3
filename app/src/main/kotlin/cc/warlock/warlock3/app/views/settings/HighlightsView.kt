package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.app.WarlockIcons
import cc.warlock.warlock3.app.components.ColorPicker
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.app.util.toWarlockColor
import cc.warlock.warlock3.core.highlights.Highlight
import cc.warlock.warlock3.core.text.WarlockStyle

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HighlightsView(
    currentCharacter: String?,
    globalHighlights: List<Highlight>,
    characterHighlights: Map<String, List<Highlight>>,
    saveHighlight: (String?, Highlight) -> Unit,
    deleteHighlight: (String?, String) -> Unit,
) {
    val highlights =
        if (currentCharacter == null) globalHighlights else characterHighlights[currentCharacter.lowercase()]
            ?: emptyList()
    var editingHighlight by remember { mutableStateOf<Highlight?>(null) }
    Column {
        Text("Showing highlights for $currentCharacter")
        Column(Modifier.fillMaxWidth().weight(1f)) {
            highlights.forEach { highlight ->
                ListItem(
                    modifier = Modifier.clickable { },
                    text = { Text(highlight.pattern) },
                )
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Button(onClick = { editingHighlight = Highlight("", emptyList(), false) }) {
                Icon(imageVector = WarlockIcons.Add, contentDescription = null)
            }
        }
    }
    editingHighlight?.let { highlight ->
        EditHighlightDialog(
            highlight = highlight,
            saveHighlight = { newHighlight ->
                if (highlight.pattern.isNotBlank()) {
                    deleteHighlight(currentCharacter?.lowercase(), highlight.pattern)
                }
                saveHighlight(currentCharacter?.lowercase(), newHighlight)
                editingHighlight = null
            },
            onClose = { editingHighlight = null }
        )
    }
}

@Composable
fun EditHighlightDialog(
    highlight: Highlight,
    saveHighlight: (Highlight) -> Unit,
    onClose: () -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var pattern by remember { mutableStateOf(highlight.pattern) }
    val styles = remember { mutableListOf<WarlockStyle>().apply { addAll(highlight.styles) } }
    var isRegex by remember { mutableStateOf(highlight.isRegex) }
    Dialog(
        onCloseRequest = onClose,
        state = rememberDialogState(size = DpSize(width = 1200.dp, height = 500.dp))
    ) {
        Column(
            modifier = Modifier
                .scrollable(
                    state = rememberScrollState(),
                    orientation = Orientation.Horizontal
                )
        ) {
            TextField(value = pattern, label = { Text("Pattern") }, onValueChange = { pattern = it })
            // Add | to match empty string, then match and see how many groups there are
            val groupCount = if (isRegex) try { Regex("$pattern|") } catch (e: Throwable) { null }?.find("")?.groups?.size ?: 1 else 1
            Column {
                for (i in 0 until groupCount) {
                    val style = styles.getOrNull(i)
                    Button({ editColor = i to true }) {
                        Row {
                            Text("Content: ")
                            style?.textColor?.let {
                                Box(Modifier.background(it.toColor()).border(1.dp, Color.Black))
                            }
                        }
                    }
                    Button({ editColor = i to false }) {
                        Row {
                            Text("Background: ")
                            style?.backgroundColor?.let {
                                Box(Modifier.background(it.toColor()).border(1.dp, Color.Black))
                            }
                        }
                    }
                }
            }
            Row {
                Button(
                    onClick = {
                        saveHighlight(Highlight(pattern, styles, false))
                    }
                ) {
                    Text("OK")
                }
                Button(onClick = onClose) {
                    Text("CANCEL")
                }
            }
        }
    }
    editColor?.let { (group, content) ->
        Dialog(
            onCloseRequest = { editColor = null }
        ) {
            ColorPicker { color ->
                val currentStyle = styles.getOrNull(group) ?: WarlockStyle()
                val newStyle =
                    if (content)
                        currentStyle.copy(textColor = color.toWarlockColor())
                    else
                        currentStyle.copy(backgroundColor = color.toWarlockColor())
                if (styles.size < group + 1) {
                    for (i in styles.size .. group) {
                        styles.add(WarlockStyle())
                    }
                }
                styles[group] = newStyle
            }
        }
    }
}
