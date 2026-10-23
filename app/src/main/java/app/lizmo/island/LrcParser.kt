package app.lizmo.island

/** Parses LRC text: handles repeated timestamps ("[01:00.00][02:30.00]chorus"), word tags, credit lines. */
object LrcParser {
    private val stamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
    private val stampPrefix = Regex("""^(?:\[\d+:\d+(?:\.\d+)?])+""")
    private val wordTag = Regex("""<\d+:\d+(?:\.\d+)?>""")
    private val credit = Regex(
        """^(\u4F5C\u8BCD|\u4F5C\u66F2|\u7F16\u66F2|\u5236\u4F5C\u4EBA|lyrics?|music|composer|arranger|producer|written)\s*(by)?\s*[:\uFF1A].*""",
        RegexOption.IGNORE_CASE
    )

    fun parse(lrc: String): List<Line> {
        val out = ArrayList<Line>()
        for (row in lrc.lineSequence()) {
            val r = row.trim()
            if (!r.startsWith("[")) continue
            val pre = stampPrefix.find(r) ?: continue
            val text = r.substring(pre.value.length).replace(wordTag, "").trim()
            if (credit.matches(text)) continue
            for (m in stamp.findAll(pre.value)) {
                val secs = m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()
                out.add(Line((secs * 1000).toLong(), text))
            }
        }
        return out.sortedBy { it.t }
    }
}
