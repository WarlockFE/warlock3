package cc.warlock.warlock3.core

import cc.warlock.warlock3.core.compass.DirectionType

sealed class ClientEvent
data class ClientProgressBarEvent(val progressBarData: ProgressBarData) : ClientEvent()
data class ClientCompassEvent(val directions: List<DirectionType>) : ClientEvent()
