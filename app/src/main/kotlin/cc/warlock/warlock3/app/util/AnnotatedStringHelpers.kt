package cc.warlock.warlock3.app.util

import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.app.model.ViewHighlight

fun AnnotatedString.highlight(highlights: List<ViewHighlight>): AnnotatedString {
    val text = text
    return with(AnnotatedString.Builder(this)) {
        highlights.forEach { highlight ->
            highlight.regex.find(text)?.let { result ->
                for ((index, group) in result.groups.withIndex()) {
                    if (group != null) {
                        highlight.styles.getOrNull(index)?.let { style ->
                            addStyle(style, group.range.first, group.range.last + 1)
                        }
                    }
                }
            }
        }
        toAnnotatedString()
    }
}