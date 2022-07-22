package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.prefs.HighlightRepository
import cc.warlock.warlock3.core.prefs.VariableRepository
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.ScriptStatus
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.util.parseArguments
import cc.warlock.warlock3.core.util.toCaseInsensitiveMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.*

class WslScriptInstance(
    override val name: String,
    private val script: WslScript,
    private val variableRepository: VariableRepository,
    private val highlightRepository: HighlightRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry,
) : ScriptInstance {

    override val id: UUID = UUID.randomUUID()

    override var status: ScriptStatus = ScriptStatus.NotStarted
        private set(newStatus) {
            field = newStatus
            scriptEngineRegistry.scriptStateChanged(this)
        }

    private lateinit var lines: List<WslLine>
    private val scope = CoroutineScope(Dispatchers.Default)

    private val suspendedChannel = Channel<Unit>(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit) {
        val arguments = parseArguments(argumentString)
        status = ScriptStatus.Running
        scope.launch {
            try {
                client.sendCommand("_state scripting on", echo = false)
                lines = script.parse()
                val globalVariables = client.characterId.flatMapLatest { id ->
                    if (id != null) {
                        variableRepository.observeCharacterVariables(id).map { variables ->
                            variables.associate { it.name to it.value }
                                .toCaseInsensitiveMap()
                        }
                    } else {
                        flow {
                            emptyMap<String, String>()
                        }
                    }
                }
                    .stateIn(scope = scope)
                val context = WslContext(
                    client = client,
                    lines = lines,
                    scriptInstance = this@WslScriptInstance,
                    scope = scope,
                    globalVariables = globalVariables,
                    variableRepository = variableRepository,
                    highlightRepository = highlightRepository,
                )
                context.setScriptVariable("0", WslString(argumentString))
                arguments.forEachIndexed { index, arg ->
                    context.setScriptVariable((index + 1).toString(), WslString(arg))
                }
                while (status == ScriptStatus.Running || status == ScriptStatus.Suspended) {
                    val line = context.getNextLine()
                    if (line == null) {
                        status = ScriptStatus.Stopped
                        client.print(StyledString("Script \"$name\" ended"))
                        break
                    }
                    waitWhenSuspended()
                    line.statement.execute(context)
                }
            } catch (e: WslParseException) {
                status = ScriptStatus.Stopped
                client.print(StyledString(text = e.reason, styles = listOf(WarlockStyle.Error)))
            } catch (e: WslRuntimeException) {
                status = ScriptStatus.Stopped
                client.print(StyledString(text = "Script error: ${e.reason}", styles = listOf(WarlockStyle.Error)))
            } finally {
                client.sendCommand("_state scripting off", echo = false)
                onStop()
            }
        }
    }

    override fun stop() {
        status = ScriptStatus.Stopped
        scope.cancel()
    }

    override fun suspend() {
        status = ScriptStatus.Suspended
    }

    override fun resume() {
        status = ScriptStatus.Running
        suspendedChannel.trySend(Unit)
    }

    suspend fun waitWhenSuspended() {
        while (status == ScriptStatus.Suspended) {
            suspendedChannel.receive()
        }
    }
}