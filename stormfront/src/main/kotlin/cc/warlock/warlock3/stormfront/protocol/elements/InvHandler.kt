package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class InvHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["id"]?.let { id ->
            StormfrontStreamEvent(id = id)
        }
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontStreamEvent(null)
    }
}