package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.components.ColorPicker
import cc.warlock.warlock3.app.model.GameCharacter
import cc.warlock.warlock3.app.util.*
import cc.warlock.warlock3.core.text.*
import cc.warlock.warlock3.core.util.toWarlockColor
import cc.warlock.warlock3.stormfront.StreamLine

@Composable
fun AppearanceView(
    styleRepository: StyleRepository,
    currentId: String?,
    characters: List<GameCharacter>,
    saveStyle: (characterId: String, name: String, StyleDefinition) -> Unit
) {
    val characterIdState = remember { mutableStateOf(currentId ?: characters.firstOrNull()?.characterName) }
    val characterId = characterIdState.value ?: return
    val styleMap by styleRepository.getStyleMap(characterId).collectAsState(emptyMap())
    val previewLines = listOf(
        StreamLine(
            text = StyledString("[Riverhaven, Crescent Way]", style = WarlockStyle.RoomName),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString(
                "This is the room description for some room in Riverhaven. It didn't exist in our old preview, so we're putting arbitrary text here.",
                style = WarlockStyle("roomdescription")
            ),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("You also see a ") + StyledString(
                "Sir Robyn",
                style = WarlockStyle.Bold
            ) + StyledString("."),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("say Hello", style = WarlockStyle.Command),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("You say", style = WarlockStyle.Speech) + StyledString(", \"Hello.\""),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("Someone whispers", style = WarlockStyle.Whisper) + StyledString(", \"Hi\""),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("Your mind hears Someone thinking, \"hello everyone\"", style = WarlockStyle.Thought),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString(
                " __      __              .__                 __    \n" +
                        "/  \\    /  \\_____ _______|  |   ____   ____ |  | __\n" +
                        "\\   \\/\\/   /\\__  \\\\_  __ \\  |  /  _ \\_/ ___\\|  |/ /\n" +
                        " \\        /  / __ \\|  | \\/  |_(  <_> )  \\___|    < \n" +
                        "  \\__/\\  /  (____  /__|  |____/\\____/ \\___  >__|_ \\\n" +
                        "       \\/        \\/                       \\/     \\/",
                style = WarlockStyle.Mono
            ),
            ignoreWhenBlank = false,
        )
    )

    Column {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = styleMap["default"]?.textColor?.toColor() ?: Color.Unspecified
            )
        ) {
            Column(
                modifier = Modifier
                    .background(styleMap["default"]?.backgroundColor?.toColor() ?: Color.Unspecified)
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                previewLines.forEach { line ->
                    val lineStyle = flattenStyles(
                        line.text.getEntireLineStyles(
                            variables = emptyMap(),
                            styleMap = styleMap,
                        )
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(lineStyle?.backgroundColor?.toColor() ?: Color.Unspecified)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(text = line.text.toAnnotatedString(variables = emptyMap(), styleMap = styleMap))
                    }
                }
            }
        }
        PresetSettings(
            styleMap = styleMap,
            saveStyle = { name, styleDefinition -> saveStyle(characterId, name, styleDefinition) })
    }
}

@Composable
fun PresetSettings(
    styleMap: Map<String, StyleDefinition>,
    saveStyle: (name: String, StyleDefinition) -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }

    Row {
        Column(Modifier.weight(1f)) {
            if (editColor != null) {
                val currentColor = editColor!!.first
                var currentText by remember(currentColor) {
                    mutableStateOf(if (currentColor.isUnspecified()) "" else currentColor.argb.toHexString())
                }
                ColorPicker {
                    editColor!!.second(it.toWarlockColor())
                    editColor = editColor!!.copy(first = it.toWarlockColor())
                }
                OutlinedTextField(
                    value = currentText,
                    onValueChange = {
                        currentText = it
                        editColor!!.second("#$it".toWarlockColor() ?: WarlockColor.Unspecified)
                    }
                )
            } else {
                Text("No style selected")
            }
        }
        Column {
            val presets = listOf("bold", "command", "roomName", "speech", "thought", "watching", "whisper", "echo")
            presets.forEach { preset ->
                val style = styleMap[preset]
                if (style != null) {
                    Row {
                        Text(
                            modifier = Modifier.width(160.dp).align(Alignment.CenterVertically),
                            text = preset.replaceFirstChar { it.uppercase() },
                        )
                        OutlinedButton(
                            onClick = {
                                editColor = Pair(style.textColor) { color ->
                                    saveStyle(
                                        preset,
                                        style.copy(textColor = color)
                                    )
                                }
                            }
                        ) {
                            Row {
                                Text("Content: ")
                                Box(
                                    Modifier.size(16.dp).background(style.textColor.toColor()).border(1.dp, Color.Black)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                editColor = Pair(style.backgroundColor) { color ->
                                    saveStyle(
                                        preset,
                                        style.copy(backgroundColor = color)
                                    )
                                }
                            }
                        ) {
                            Row {
                                Text("Background: ")
                                Box(
                                    Modifier.size(16.dp).background(style.backgroundColor.toColor())
                                        .border(1.dp, Color.Black)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}