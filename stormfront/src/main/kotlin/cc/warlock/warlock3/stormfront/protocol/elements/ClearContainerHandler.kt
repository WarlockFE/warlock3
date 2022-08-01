package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class ClearContainerHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["id"]?.let { id ->
            StormfrontClearStreamEvent(id)
        } ?: StormfrontHandledEvent
    }
}