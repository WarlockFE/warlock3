package cc.warlock.warlock3.app.ui.game

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import cc.warlock.warlock3.app.components.CompassState
import cc.warlock.warlock3.app.components.CompassTheme
import cc.warlock.warlock3.app.macros.macroCommands
import cc.warlock.warlock3.core.client.ClientCompassEvent
import cc.warlock.warlock3.core.client.ClientProgressBarEvent
import cc.warlock.warlock3.core.client.ProgressBarData
import cc.warlock.warlock3.core.parser.MacroLexer
import cc.warlock.warlock3.core.prefs.MacroRepository
import cc.warlock.warlock3.core.prefs.VariableRepository
import cc.warlock.warlock3.core.prefs.WindowRepository
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import kotlin.math.max

class GameViewModel(
    windowRepository: WindowRepository,
    val client: StormfrontClient,
    val macroRepository: MacroRepository,
    val variableRepository: VariableRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry,
    val compassTheme: CompassTheme,
) : AutoCloseable {
    private val viewModelScope = CoroutineScope(Dispatchers.IO)

    private val _entryText = mutableStateOf(TextFieldValue())
    val entryText: State<TextFieldValue> = _entryText

    private val _compassState = mutableStateOf(CompassState(emptySet()))
    val compassState: State<CompassState> = _compassState

    private val _vitalBars = mutableStateMapOf<String, ProgressBarData>()
    val vitalBars: SnapshotStateMap<String, ProgressBarData> = _vitalBars

    // Saved by macros
    private val storedText = mutableStateOf<String?>(null)

    val properties: StateFlow<Map<String, String>> = client.properties

    @OptIn(ExperimentalCoroutinesApi::class)
    private val macros = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            macroRepository.observeCharacterMacros(characterId)
        } else {
            macroRepository.observeGlobalMacros()
        }
            .map { it.toMap() }
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val variables: StateFlow<Map<String, String>> = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            variableRepository.observeCharacterVariables(characterId).map { list ->
                list.associate { it.name to it.value } as Map<String, String>
            }
        } else {
            flow { emit(emptyMap()) }
        }
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    private var scriptsPaused = false

    private val currentTime: Flow<Int> = flow {
        while (true) {
            val time = client.time
            emit((time / 1000L).toInt())
            val nextSecond = 1000L - (time % 1000)
            delay(max(10L, nextSecond))
        }
    }

    val roundTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["roundtime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = 0)

    val castTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["casttime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = 0)

    private var historyPosition = -1
    private val _sendHistory = mutableStateOf<List<String>>(emptyList())
    val sendHistory: State<List<String>> = _sendHistory

    val windows = windowRepository.windows

    @OptIn(ExperimentalCoroutinesApi::class)
    val openWindows = client.characterId.flatMapLatest {
        if (it != null) {
            windowRepository.observeOpenWindows(it)
        } else {
            flow { emit(emptySet()) }
        }
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptySet())

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientProgressBarEvent -> {
                        _vitalBars += event.progressBarData.id to event.progressBarData
                    }
                    is ClientCompassEvent -> {
                        _compassState.value = CompassState(directions = event.directions.toSet())
                    }
                    else -> {
                        // don't care
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun submit() {
        val line = _entryText.value.text
        _entryText.value = TextFieldValue()
        _sendHistory.value = listOf(line) + _sendHistory.value
        historyPosition = -1
        viewModelScope.launch {
            if (line.startsWith(".")) {
                val scriptCommand = line.drop(1)
                scriptEngineRegistry.startScript(client, scriptCommand)
            } else {
                client.sendCommand(line)
            }
        }
    }

    suspend fun stopScripts() {
        val scriptInstances = scriptEngineRegistry.runningScripts.value
        val count = scriptInstances.size
        if (count > 0) {
            scriptInstances.forEach { scriptInstance ->
                scriptInstance.stop()
            }
            client.print(StyledString("Stopped $count script(s)"))
        }
    }

    suspend fun pauseScripts() {
        val paused = this.scriptsPaused
        this.scriptsPaused = !paused
        val scriptInstances = scriptEngineRegistry.runningScripts.value
        if (scriptInstances.isNotEmpty()) {
            if (paused) {
                client.print(StyledString("Resumed script(s)"))
            } else {
                client.print(StyledString("Paused script(s)"))
            }
            for (instance in scriptInstances) {
                if (paused) {
                    instance.resume()
                } else {
                    instance.suspend()
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun handleKeyPress(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }
        if (event.key.keyCode == Key.Enter.keyCode) {
            submit()
            return true
        }

        val keyString = translateKeyPress(event)
        val macroString = macros.value[keyString] ?: return false

        val tokens = try {
            tokenizeMacro(macroString)
        } catch (e: Exception) {
            return false
        }
        executeMacro(tokens)

        return true
    }

    private fun executeMacro(tokens: List<Token>) {
        viewModelScope.launch {
            tokens.forEach { token ->
                when (token.type) {
                    MacroLexer.Entity -> {
                        val entity = token.text
                        assert(entity.length == 2)
                        assert(entity[0] == '\\')
                        handleEntity(entity[1])
                    }
                    MacroLexer.At -> {
                        _entryText.value =
                            _entryText.value.copy(selection = TextRange(_entryText.value.text.length))
                    }
                    MacroLexer.Question -> {
                        storedText.value?.let { entryAppend(it) }
                    }
                    MacroLexer.Character -> {
                        entryAppend(token.text)
                    }
                    MacroLexer.VariableName -> {
                        token.text?.let { if (it.endsWith("%")) it.drop(1) else it }
                            ?.let { name ->
                                entryAppend(variables.value[name] ?: "")
                            }
                    }
                    MacroLexer.CommandText -> {
                        val command = macroCommands[token.text.lowercase()]
                        command?.invoke(this@GameViewModel)
                    }
                }
            }
        }
    }

    private suspend fun handleEntity(entity: Char) {
        when (entity) {
            'x' -> {
                storedText.value = _entryText.value.text
                entryClear()
            }
            'r' -> {
                submit()
            }
            'p' -> {
                delay(1_000L)
            }
        }
    }

    private fun entryClear() {
        _entryText.value = TextFieldValue()
    }

    private fun entryAppend(text: String) {
        _entryText.value = _entryText.value.copy(text = _entryText.value.text + text)
    }

    fun historyPrev() {
        val history = sendHistory.value
        if (historyPosition < history.size - 1) {
            historyPosition++
            val text = history[historyPosition]
            _entryText.value = TextFieldValue(text = text, selection = TextRange(text.length))
        }
    }

    fun historyNext() {
        if (historyPosition >= 0) {
            historyPosition--
            if (historyPosition < 0) {
                entryClear()
            } else {
                val text = sendHistory.value[historyPosition]
                _entryText.value = TextFieldValue(text = text, selection = TextRange(text.length))
            }
        }
    }

    private fun translateKeyPress(event: KeyEvent): String {
        val keyString = StringBuilder()
        if (event.isCtrlPressed) {
            keyString.append("ctrl+")
        }
        if (event.isAltPressed) {
            keyString.append("alt+")
        }
        if (event.isShiftPressed) {
            keyString.append("shift+")
        }
        if (event.isMetaPressed) {
            keyString.append("meta+")
        }
        keyString.append(event.key.keyCode)
        return keyString.toString()
    }

    private fun tokenizeMacro(input: String): List<Token> {
        val charStream = CharStreams.fromString(input)
        val lexer = MacroLexer(charStream)
        return lexer.allTokens
    }

    fun setEntryText(value: TextFieldValue) {
        _entryText.value = value
    }

    override fun close() {
        viewModelScope.cancel()
    }
}