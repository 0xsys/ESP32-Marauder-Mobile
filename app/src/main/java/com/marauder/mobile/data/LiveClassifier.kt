package com.marauder.mobile.data

/**
 * Describes the live view a firmware command should open:
 *  - [list]: the structured list this command fills (polled live while it runs), or null.
 *  - [continuous]: true for scans/sniffs/attacks/streams (show a Stop control and stop the
 *    scan when leaving the screen); false for one‑shot commands that just print a reply.
 */
data class LiveSpec(val list: ListType?, val continuous: Boolean, val capture: Boolean = false) {
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
        // v1.20.0 continuous features (stop with stopscan on exit)
        "foxhunt", "csimotion", "remoteid", "recon", "findmy",
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
            // `remoteid scan`/`track` populate the Remote ID (drone) list
            head == "remoteid" && (c.contains("scan") || c.contains("track")) -> ListType.DRONES
            else -> null
        }
        // Commands that write a pcap/log the firmware would normally save to SD.
        // With proto ≥ 2 these are streamed to the phone and saved there instead.
        val capture = head.startsWith("sniff") || head == "wardrive"
        // Sub-command-aware continuity: the state-changing verbs of the new
        // multi-verb commands are one-shot, only their scanning verbs stream.
        val continuous = when (head) {
            "remoteid" -> c.contains("scan") || c.contains("track")
            "recon" -> !c.contains("stop") && !c.contains("status")
            "csimotion" -> c.contains("start")
            else -> head in continuousHeads
        }
        return LiveSpec(list = list, continuous = continuous, capture = capture)
    }
}
