package cc.warlock.warlock3.app.config

import com.uchuhimo.konf.ConfigSpec

object SgeSpec : ConfigSpec("sge") {
    val host by optional("eaccess.play.net")
    val port by optional<Int>(7900)

    val username by optional<String?>(null)
    val password by optional<String?>(null)
}