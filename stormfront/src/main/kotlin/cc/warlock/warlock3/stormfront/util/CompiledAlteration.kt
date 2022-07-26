package cc.warlock.warlock3.stormfront.util

import cc.warlock.warlock3.core.prefs.models.Alteration

class CompiledAlteration(private val alteration: Alteration) {
    private val regex =
        Regex(alteration.pattern, setOfNotNull(if (alteration.ignoreCase) RegexOption.IGNORE_CASE else null))

    fun match(line: String, streamName: String): AlterationResult? {
        if (alteration.sourceStream != null && streamName != alteration.sourceStream)
            return null
        return if (regex.containsMatchIn(line)) {
            AlterationResult(
                text = alteration.result?.let { regex.replace(line, it) },
                alteration = alteration
            )
        } else {
            null
        }
    }
}

data class AlterationResult(
    val text: String?, // null means keep original text
    val alteration: Alteration
)