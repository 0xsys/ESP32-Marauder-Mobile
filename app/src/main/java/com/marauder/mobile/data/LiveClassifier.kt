package com.marauder.mobile.data

/**
 * Describes the live view a firmware command should open:
 *  - [list]: the structured list this command fills (polled live while it runs), or null.
 *  - [continuous]: true for scans/sniffs/attacks/streams (show a Stop control and stop the
 *    scan when leaving the screen); false for one‑shot commands that just print a reply.
 */
data class LiveSpec(val list: ListType?, val continuous: Boolean) {
    val isList: Boolean get() = list != null
}

object LiveClassifier {

    private val continuousHeads = setOf(
        "sniffprobe", "sniffbeacon", "sniffdeauth", "sniffpwn", "sniffpinescan",
        "sniffmultissid", "sniffpmkid", "sniffsae", "sniffraw", "sniffbt", "sniffskim",
        "scanall", "scansta", "sigmon", "mactrack", "packetcount",
        "attack", "blespam", "karma", "evilportal", "spoofat",
        "pingscan", "arpscan", "portscan",
        "gpsdata", "nmea", "gpstracker", "wardrive", "analyzer",
    )

    fun of(command: String): LiveSpec {
        val c = command.trim()
        val head = c.substringBefore(' ')
        val list = when {
            head == "scanall" || head == "sniffbeacon" -> ListType.ACCESS_POINTS
            head == "scansta" -> ListType.STATIONS
            head == "sniffprobe" -> ListType.PROBES
            head == "sniffbt" && c.contains("airtag") -> ListType.AIRTAGS
            head == "pingscan" || head == "arpscan" -> ListType.IPS
            else -> null
        }
        return LiveSpec(list = list, continuous = head in continuousHeads)
    }
}
