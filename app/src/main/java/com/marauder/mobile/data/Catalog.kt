package com.marauder.mobile.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.Dvr
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermScanWifi
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector

/** The six structured lists the firmware can stream over `jsonlist`. */
enum class ListType(val code: String, val title: String) {
    ACCESS_POINTS("a", "Access Points"),
    SSIDS("s", "SSIDs"),
    STATIONS("c", "Stations"),
    IPS("i", "IP List"),
    PROBES("p", "Probe Requests"),
    AIRTAGS("t", "AirTags"),
}

enum class AnalyzerKind { WIFI, BT, CHANNEL }

/** Colour role for a menu entry, mirroring the firmware's on-screen TFT palette. */
enum class Accent { WIFI, BLUETOOTH, GPS, DEVICE, NEUTRAL, SNIFFER, SCANNER, ATTACK, GENERAL, PRIMARY }

sealed interface MenuAction {
    /** Fire a raw firmware CLI command. */
    data class Send(val command: String) : MenuAction

    /** Descend into another catalog screen. */
    data class Submenu(val id: String) : MenuAction

    /** Open one of the live structured-list views. */
    data class OpenList(val type: ListType) : MenuAction

    /** Start a streaming analyzer and open the live chart. */
    data class OpenAnalyzer(val command: String, val kind: AnalyzerKind) : MenuAction

    /** Open the raw serial console. */
    data object OpenConsole : MenuAction
}

data class MenuItem(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val accent: Accent,
    val action: MenuAction,
    val dangerous: Boolean = false,
)

data class MenuScreen(
    val id: String,
    val title: String,
    val items: List<MenuItem>,
)

/**
 * The whole navigable menu tree. Titles and ordering deliberately mirror the
 * ESP32 Marauder on-device menus (MenuFunctions.cpp) so the app feels like the
 * firmware; each leaf maps to a real firmware CLI command (CommandLine.cpp).
 */
object Catalog {

    const val ROOT = "main"

    val screens: Map<String, MenuScreen> = buildList {
        add(
            MenuScreen(
                id = "main",
                title = "Marauder",
                items = listOf(
                    MenuItem("WiFi", "Sniffers · Scanners · Attacks", Icons.Filled.Wifi, Accent.WIFI, MenuAction.Submenu("wifi")),
                    MenuItem("Bluetooth", "Sniffers · Attacks", Icons.Filled.Bluetooth, Accent.BLUETOOTH, MenuAction.Submenu("bluetooth")),
                    MenuItem("GPS", "Data · NMEA · Tracker · POI", Icons.Filled.MyLocation, Accent.GPS, MenuAction.Submenu("gps")),
                    MenuItem("Device", "Info · Files · Firmware", Icons.Filled.DeveloperBoard, Accent.DEVICE, MenuAction.Submenu("device")),
                    MenuItem("Reboot", "Restart the ESP32", Icons.Filled.RestartAlt, Accent.NEUTRAL, MenuAction.Send("reboot")),
                ),
            ),
        )

        // ---------------- WiFi ----------------
        add(
            MenuScreen(
                "wifi", "WiFi",
                listOf(
                    MenuItem("Sniffers", "Passive capture & detect", Icons.Filled.Sensors, Accent.SNIFFER, MenuAction.Submenu("wifi_sniffers")),
                    MenuItem("Scanners", "Network / port scans", Icons.Filled.Radar, Accent.SCANNER, MenuAction.Submenu("wifi_scanners")),
                    MenuItem("Attacks", "Deauth · Beacon · Portal", Icons.Filled.Bolt, Accent.ATTACK, MenuAction.Submenu("wifi_attacks")),
                    MenuItem("General", "Lists · MACs · Join", Icons.Filled.Tune, Accent.GENERAL, MenuAction.Submenu("wifi_general")),
                ),
            ),
        )
        add(
            MenuScreen(
                "wifi_sniffers", "Sniffers",
                listOf(
                    MenuItem("Probe Request Sniff", "sniffprobe", Icons.Filled.Podcasts, Accent.SNIFFER, MenuAction.Send("sniffprobe")),
                    MenuItem("Beacon Sniff", "sniffbeacon", Icons.Filled.CellTower, Accent.SNIFFER, MenuAction.Send("sniffbeacon")),
                    MenuItem("Deauth Sniff", "sniffdeauth", Icons.Filled.WifiOff, Accent.SNIFFER, MenuAction.Send("sniffdeauth")),
                    MenuItem("Packet Count", "packetcount", Icons.Filled.Numbers, Accent.SNIFFER, MenuAction.Send("packetcount")),
                    MenuItem("EAPOL/PMKID Scan", "sniffpmkid", Icons.Filled.Key, Accent.SNIFFER, MenuAction.Send("sniffpmkid")),
                    MenuItem("Raw Capture", "sniffraw", Icons.Filled.GraphicEq, Accent.SNIFFER, MenuAction.Send("sniffraw")),
                    MenuItem("Channel Analyzer", "Live channel graph", Icons.Filled.Insights, Accent.SNIFFER, MenuAction.OpenAnalyzer("analyzer -t wifi", AnalyzerKind.WIFI)),
                    MenuItem("Channel Summary", "Live channel bars", Icons.Filled.BarChart, Accent.SNIFFER, MenuAction.OpenAnalyzer("analyzer -t chan", AnalyzerKind.CHANNEL)),
                    MenuItem("Detect Pwnagotchi", "sniffpwn", Icons.Filled.Pets, Accent.SNIFFER, MenuAction.Send("sniffpwn")),
                    MenuItem("Detect Pineapple", "sniffpinescan", Icons.Filled.Router, Accent.SNIFFER, MenuAction.Send("sniffpinescan")),
                    MenuItem("Detect MultiSSID", "sniffmultissid", Icons.Filled.Dns, Accent.SNIFFER, MenuAction.Send("sniffmultissid")),
                    MenuItem("Scan AP/STA", "scanall", Icons.Filled.PermScanWifi, Accent.SNIFFER, MenuAction.Send("scanall")),
                    MenuItem("Fox Hunt", "sigmon", Icons.Filled.SettingsInputAntenna, Accent.SNIFFER, MenuAction.Send("sigmon")),
                    MenuItem("MAC Monitor", "mactrack", Icons.Filled.TrackChanges, Accent.SNIFFER, MenuAction.Send("mactrack")),
                    MenuItem("SAE Commit", "sniffsae", Icons.Filled.Lock, Accent.SNIFFER, MenuAction.Send("sniffsae")),
                ),
            ),
        )
        add(
            MenuScreen(
                "wifi_scanners", "Scanners",
                listOf(
                    MenuItem("Ping Scan", "pingscan", Icons.Filled.NetworkCheck, Accent.SCANNER, MenuAction.Send("pingscan")),
                    MenuItem("ARP Scan", "arpscan -f", Icons.Filled.Lan, Accent.SCANNER, MenuAction.Send("arpscan -f")),
                    MenuItem("Port Scan All", "portscan -a -t 0", Icons.Filled.SettingsEthernet, Accent.SCANNER, MenuAction.Send("portscan -a -t 0")),
                    MenuItem("SSH Scan", "portscan -s ssh", Icons.Filled.Terminal, Accent.SCANNER, MenuAction.Send("portscan -s ssh")),
                    MenuItem("Telnet Scan", "portscan -s telnet", Icons.AutoMirrored.Filled.Dvr, Accent.SCANNER, MenuAction.Send("portscan -s telnet")),
                    MenuItem("SMTP Scan", "portscan -s smtp", Icons.Filled.Email, Accent.SCANNER, MenuAction.Send("portscan -s smtp")),
                    MenuItem("DNS Scan", "portscan -s dns", Icons.Filled.Dns, Accent.SCANNER, MenuAction.Send("portscan -s dns")),
                    MenuItem("HTTP Scan", "portscan -s http", Icons.Filled.Http, Accent.SCANNER, MenuAction.Send("portscan -s http")),
                    MenuItem("HTTPS Scan", "portscan -s https", Icons.Filled.Https, Accent.SCANNER, MenuAction.Send("portscan -s https")),
                    MenuItem("RDP Scan", "portscan -s rdp", Icons.Filled.DesktopWindows, Accent.SCANNER, MenuAction.Send("portscan -s rdp")),
                ),
            ),
        )
        add(
            MenuScreen(
                "wifi_attacks", "Attacks",
                listOf(
                    MenuItem("Beacon Spam List", "attack -t beacon -l", Icons.Filled.Campaign, Accent.ATTACK, MenuAction.Send("attack -t beacon -l"), dangerous = true),
                    MenuItem("Beacon Spam Random", "attack -t beacon -r", Icons.Filled.Shuffle, Accent.ATTACK, MenuAction.Send("attack -t beacon -r"), dangerous = true),
                    MenuItem("Funny SSID Beacon", "attack -t funny", Icons.Filled.Mood, Accent.ATTACK, MenuAction.Send("attack -t funny"), dangerous = true),
                    MenuItem("Rick Roll Beacon", "attack -t rickroll", Icons.Filled.MusicNote, Accent.ATTACK, MenuAction.Send("attack -t rickroll"), dangerous = true),
                    MenuItem("Probe Req Flood", "attack -t probe", Icons.Filled.Podcasts, Accent.ATTACK, MenuAction.Send("attack -t probe"), dangerous = true),
                    MenuItem("Evil Portal", "Captive portal", Icons.Filled.Language, Accent.ATTACK, MenuAction.Submenu("evil_portal"), dangerous = true),
                    MenuItem("Deauth Flood", "attack -t deauth", Icons.Filled.WifiOff, Accent.ATTACK, MenuAction.Send("attack -t deauth"), dangerous = true),
                    MenuItem("AP Clone Spam", "attack -t beacon -a", Icons.Filled.ContentCopy, Accent.ATTACK, MenuAction.Send("attack -t beacon -a"), dangerous = true),
                    MenuItem("Deauth Targeted", "attack -t deauth -c", Icons.Filled.GpsFixed, Accent.ATTACK, MenuAction.Send("attack -t deauth -c"), dangerous = true),
                    MenuItem("Karma", "karma", Icons.Filled.Whatshot, Accent.ATTACK, MenuAction.Send("karma"), dangerous = true),
                    MenuItem("Bad Msg", "attack -t badmsg", Icons.Filled.Warning, Accent.ATTACK, MenuAction.Send("attack -t badmsg"), dangerous = true),
                    MenuItem("Bad Msg Targeted", "attack -t badmsg -c", Icons.Filled.Warning, Accent.ATTACK, MenuAction.Send("attack -t badmsg -c"), dangerous = true),
                    MenuItem("Assoc Sleep", "attack -t sleep", Icons.Filled.Bedtime, Accent.ATTACK, MenuAction.Send("attack -t sleep"), dangerous = true),
                    MenuItem("Assoc Sleep Targ", "attack -t sleep -c", Icons.Filled.Bedtime, Accent.ATTACK, MenuAction.Send("attack -t sleep -c"), dangerous = true),
                    MenuItem("SAE Commit Flood", "attack -t sae", Icons.Filled.Lock, Accent.ATTACK, MenuAction.Send("attack -t sae"), dangerous = true),
                    MenuItem("Channel Switch", "attack -t csa", Icons.Filled.SwapHoriz, Accent.ATTACK, MenuAction.Send("attack -t csa"), dangerous = true),
                    MenuItem("Quiet Time", "attack -t quiet", Icons.AutoMirrored.Filled.VolumeOff, Accent.ATTACK, MenuAction.Send("attack -t quiet"), dangerous = true),
                ),
            ),
        )
        add(
            MenuScreen(
                "evil_portal", "Evil Portal",
                listOf(
                    MenuItem("Access Points", "Pick a target AP", Icons.Filled.Wifi, Accent.GENERAL, MenuAction.OpenList(ListType.ACCESS_POINTS)),
                    MenuItem("Set AP #0", "evilportal -c setap 0", Icons.Filled.Router, Accent.ATTACK, MenuAction.Send("evilportal -c setap 0")),
                    MenuItem("Set HTML (index.html)", "evilportal -c sethtml index.html", Icons.Filled.Code, Accent.ATTACK, MenuAction.Send("evilportal -c sethtml index.html")),
                    MenuItem("Start", "evilportal -c start", Icons.Filled.PlayArrow, Accent.ATTACK, MenuAction.Send("evilportal -c start"), dangerous = true),
                    MenuItem("Start (index.html)", "evilportal -c start -w index.html", Icons.Filled.PlayCircle, Accent.ATTACK, MenuAction.Send("evilportal -c start -w index.html"), dangerous = true),
                ),
            ),
        )
        add(
            MenuScreen(
                "wifi_general", "General",
                listOf(
                    MenuItem("Scan AP/STA", "scanall", Icons.Filled.PermScanWifi, Accent.GENERAL, MenuAction.Send("scanall")),
                    MenuItem("Access Points", "Live AP list", Icons.Filled.Wifi, Accent.GENERAL, MenuAction.OpenList(ListType.ACCESS_POINTS)),
                    MenuItem("Stations", "Live client list", Icons.Filled.Devices, Accent.GENERAL, MenuAction.OpenList(ListType.STATIONS)),
                    MenuItem("SSIDs", "Saved / generated SSIDs", Icons.AutoMirrored.Filled.FormatListBulleted, Accent.GENERAL, MenuAction.OpenList(ListType.SSIDS)),
                    MenuItem("Probe Requests", "Captured probes", Icons.Filled.Podcasts, Accent.GENERAL, MenuAction.OpenList(ListType.PROBES)),
                    MenuItem("IP List", "Discovered hosts", Icons.Filled.Lan, Accent.GENERAL, MenuAction.OpenList(ListType.IPS)),
                    MenuItem("AirTags", "Detected AirTags", Icons.Filled.Luggage, Accent.GENERAL, MenuAction.OpenList(ListType.AIRTAGS)),
                    MenuItem("Set MACs", "Randomise / clone", Icons.Filled.Badge, Accent.GENERAL, MenuAction.Submenu("wifi_macs")),
                    MenuItem("Join Saved WiFi", "join -s", Icons.AutoMirrored.Filled.Login, Accent.GENERAL, MenuAction.Send("join -s")),
                    MenuItem("Shutdown WiFi", "stopscan -f", Icons.Filled.PowerSettingsNew, Accent.GENERAL, MenuAction.Send("stopscan -f")),
                ),
            ),
        )
        add(
            MenuScreen(
                "wifi_macs", "Set MACs",
                listOf(
                    MenuItem("Generate AP MAC", "randapmac", Icons.Filled.Shuffle, Accent.GENERAL, MenuAction.Send("randapmac")),
                    MenuItem("Generate STA MAC", "randstamac", Icons.Filled.Shuffle, Accent.GENERAL, MenuAction.Send("randstamac")),
                    MenuItem("Clone AP MAC #0", "cloneapmac -a 0", Icons.Filled.ContentCopy, Accent.GENERAL, MenuAction.Send("cloneapmac -a 0")),
                    MenuItem("Clone STA MAC #0", "clonestamac -s 0", Icons.Filled.ContentCopy, Accent.GENERAL, MenuAction.Send("clonestamac -s 0")),
                ),
            ),
        )

        // ---------------- Bluetooth ----------------
        add(
            MenuScreen(
                "bluetooth", "Bluetooth",
                listOf(
                    MenuItem("Sniffers", "Detect & analyze", Icons.AutoMirrored.Filled.BluetoothSearching, Accent.BLUETOOTH, MenuAction.Submenu("bt_sniffers")),
                    MenuItem("Attacks", "BLE spam & spoof", Icons.Filled.Bolt, Accent.ATTACK, MenuAction.Submenu("bt_attacks")),
                ),
            ),
        )
        add(
            MenuScreen(
                "bt_sniffers", "Sniffers",
                listOf(
                    MenuItem("Bluetooth Sniff", "sniffbt", Icons.Filled.Bluetooth, Accent.BLUETOOTH, MenuAction.Send("sniffbt")),
                    MenuItem("Flipper Sniff", "sniffbt -t flipper", Icons.Filled.SmartToy, Accent.BLUETOOTH, MenuAction.Send("sniffbt -t flipper")),
                    MenuItem("Airtag Sniff", "sniffbt -t airtag", Icons.Filled.Luggage, Accent.BLUETOOTH, MenuAction.Send("sniffbt -t airtag")),
                    MenuItem("Airtag Monitor", "Live AirTag list", Icons.Filled.Luggage, Accent.BLUETOOTH, MenuAction.OpenList(ListType.AIRTAGS)),
                    MenuItem("Bluetooth Analyzer", "Live BLE graph", Icons.Filled.GraphicEq, Accent.BLUETOOTH, MenuAction.OpenAnalyzer("analyzer -t bt", AnalyzerKind.BT)),
                    MenuItem("Flock Sniff", "sniffbt -t flock", Icons.Filled.Videocam, Accent.BLUETOOTH, MenuAction.Send("sniffbt -t flock")),
                    MenuItem("Meta Detect", "sniffbt -t meta", Icons.Filled.Visibility, Accent.BLUETOOTH, MenuAction.Send("sniffbt -t meta")),
                    MenuItem("Detect Card Skimmers", "sniffskim", Icons.Filled.CreditCard, Accent.BLUETOOTH, MenuAction.Send("sniffskim")),
                ),
            ),
        )
        add(
            MenuScreen(
                "bt_attacks", "Attacks",
                listOf(
                    MenuItem("Sour Apple", "blespam -t sourapple", Icons.Filled.PhoneIphone, Accent.ATTACK, MenuAction.Send("blespam -t sourapple"), dangerous = true),
                    MenuItem("Apple Juice", "blespam -t applejuice", Icons.Filled.PhoneIphone, Accent.ATTACK, MenuAction.Send("blespam -t applejuice"), dangerous = true),
                    MenuItem("Swiftpair Spam", "blespam -t windows", Icons.Filled.DesktopWindows, Accent.ATTACK, MenuAction.Send("blespam -t windows"), dangerous = true),
                    MenuItem("Samsung BLE Spam", "blespam -t samsung", Icons.Filled.Smartphone, Accent.ATTACK, MenuAction.Send("blespam -t samsung"), dangerous = true),
                    MenuItem("Google BLE Spam", "blespam -t google", Icons.Filled.Android, Accent.ATTACK, MenuAction.Send("blespam -t google"), dangerous = true),
                    MenuItem("Flipper BLE Spam", "blespam -t flipper", Icons.Filled.SmartToy, Accent.ATTACK, MenuAction.Send("blespam -t flipper"), dangerous = true),
                    MenuItem("BLE Spam All", "blespam -t all", Icons.Filled.Campaign, Accent.ATTACK, MenuAction.Send("blespam -t all"), dangerous = true),
                    MenuItem("Spoof Airtag", "spoofat -t 0", Icons.Filled.Luggage, Accent.ATTACK, MenuAction.Send("spoofat -t 0"), dangerous = true),
                ),
            ),
        )

        // ---------------- GPS ----------------
        add(
            MenuScreen(
                "gps", "GPS",
                listOf(
                    MenuItem("GPS Data", "gpsdata", Icons.Filled.MyLocation, Accent.GPS, MenuAction.Send("gpsdata")),
                    MenuItem("NMEA Stream", "nmea", Icons.Filled.Satellite, Accent.GPS, MenuAction.Send("nmea")),
                    MenuItem("GPS Tracker", "gpstracker -c start", Icons.Filled.Route, Accent.GPS, MenuAction.Send("gpstracker -c start")),
                    MenuItem("Stop Tracker", "gpstracker -c stop", Icons.Filled.Stop, Accent.GPS, MenuAction.Send("gpstracker -c stop")),
                    MenuItem("GPS POI", "Mark points of interest", Icons.Filled.PushPin, Accent.GPS, MenuAction.Submenu("gps_poi")),
                ),
            ),
        )
        add(
            MenuScreen(
                "gps_poi", "GPS POI",
                listOf(
                    MenuItem("Start POI", "gpspoi -s", Icons.Filled.PlayArrow, Accent.GPS, MenuAction.Send("gpspoi -s")),
                    MenuItem("Mark POI", "gpspoi -m", Icons.Filled.PushPin, Accent.GPS, MenuAction.Send("gpspoi -m")),
                    MenuItem("End POI", "gpspoi -e", Icons.Filled.Stop, Accent.GPS, MenuAction.Send("gpspoi -e")),
                ),
            ),
        )

        // ---------------- Device ----------------
        add(
            MenuScreen(
                "device", "Device",
                listOf(
                    MenuItem("Device Info", "jsoninfo", Icons.Filled.Info, Accent.DEVICE, MenuAction.Send("jsoninfo")),
                    MenuItem("Status", "jsonstatus", Icons.Filled.Analytics, Accent.DEVICE, MenuAction.Send("jsonstatus")),
                    MenuItem("Settings", "settings", Icons.Filled.Settings, Accent.DEVICE, MenuAction.Send("settings")),
                    MenuItem("Channel", "channel", Icons.Filled.Tune, Accent.DEVICE, MenuAction.Send("channel")),
                    MenuItem("Brightness Cycle", "brightness -c", Icons.Filled.BrightnessMedium, Accent.DEVICE, MenuAction.Send("brightness -c")),
                    MenuItem("LED Rainbow", "led -p rainbow", Icons.Filled.Palette, Accent.DEVICE, MenuAction.Send("led -p rainbow")),
                    MenuItem("LED Cyan", "led -s #00d5ff", Icons.Filled.Lightbulb, Accent.DEVICE, MenuAction.Send("led -s #00d5ff")),
                    MenuItem("Save/Load Files", "SD list persistence", Icons.Filled.Folder, Accent.DEVICE, MenuAction.Submenu("device_files")),
                    MenuItem("List SD Files", "ls /", Icons.Filled.FolderOpen, Accent.DEVICE, MenuAction.Send("ls /")),
                    MenuItem("Console", "Raw serial terminal", Icons.Filled.Terminal, Accent.DEVICE, MenuAction.OpenConsole),
                    MenuItem("Update Firmware", "update -s", Icons.Filled.SystemUpdate, Accent.DEVICE, MenuAction.Send("update -s"), dangerous = true),
                    MenuItem("Reboot", "reboot", Icons.Filled.RestartAlt, Accent.NEUTRAL, MenuAction.Send("reboot")),
                ),
            ),
        )
        add(
            MenuScreen(
                "device_files", "Save / Load Files",
                listOf(
                    MenuItem("Save APs", "save -a", Icons.Filled.Folder, Accent.DEVICE, MenuAction.Send("save -a")),
                    MenuItem("Load APs", "load -a", Icons.Filled.FolderOpen, Accent.DEVICE, MenuAction.Send("load -a")),
                    MenuItem("Save SSIDs", "save -s", Icons.Filled.Folder, Accent.DEVICE, MenuAction.Send("save -s")),
                    MenuItem("Load SSIDs", "load -s", Icons.Filled.FolderOpen, Accent.DEVICE, MenuAction.Send("load -s")),
                ),
            ),
        )
    }.associateBy { it.id }

    fun screen(id: String): MenuScreen? = screens[id]
}
