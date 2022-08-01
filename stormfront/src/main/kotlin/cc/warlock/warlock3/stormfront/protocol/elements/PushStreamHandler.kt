package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontStreamEvent

class PushStreamHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["id"]?.let { id ->
            StormfrontStreamEvent(id = id)
        }
    }
}