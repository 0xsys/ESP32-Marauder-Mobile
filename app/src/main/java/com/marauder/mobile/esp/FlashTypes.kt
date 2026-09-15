package com.marauder.mobile.esp

/** ESP32 chip family a board is built for. Only the classic [ESP32] can be flashed
 *  by the in-app stub-less ROM flasher today; the rest are listed for reference and
 *  must be flashed with the web installer / esptool. */
enum class Chip(val label: String) {
    ESP32("ESP32"),
    ESP32_S2("ESP32-S2"),
    ESP32_S3("ESP32-S3"),
    ESP32_C5("ESP32-C5"),
    ESP32_C6("ESP32-C6"),
}

/** One region to write: an image [bytes]-blob at flash [offset]. */
data class FlashPart(val offset: Int, val assetName: String)

/**
 * A flashable firmware target: one hardware variant of the fork's release.
 * [parts] is the single **merged** image flashed at 0x0 (it already contains the
 * bootloader, partition table, boot_app0 and app, so one write + one MD5 check
 * flashes the whole device).
 */
data class FlashProfile(
    val id: String,
    val label: String,     // short board name shown in the picker
    val hardware: String,  // which hardware this covers (incl. aliases)
    val chip: Chip,
    val parts: List<FlashPart>,
) {
    /** The in-app stub-less flasher drives the classic ESP32 ROM only (S2/S3/C5/C6
     *  use native USB-JTAG / different reset timing and aren't supported yet). */
    val flashableInApp: Boolean get() = chip == Chip.ESP32
}

/**
 * The firmware source (the maintainer's **fork**, which carries the JSON-serial
 * interface these apps depend on) and the full board catalog. Every entry maps to
 * the fork's `ESP32Marauder-<tag>-<token>.merged.bin` release asset.
 */
object Firmware {
    const val REPO = "0xsys/ESP32Marauder"
    const val TAG = "v1.12.6-jsonserial"
    const val RELEASE_PAGE = "https://github.com/0xsys/ESP32Marauder/releases/tag/$TAG"

    private fun merged(id: String, label: String, hardware: String, chip: Chip, token: String) =
        FlashProfile(
            id = id,
            label = label,
            hardware = hardware,
            chip = chip,
            parts = listOf(FlashPart(offset = 0x0, assetName = "ESP32Marauder-$TAG-$token.merged.bin")),
        )

    /** Every board the fork publishes, in menu order. Tokens match the fork's
     *  release naming (build-all-firmware.sh / finalize-release.sh). */
    val PROFILES: List<FlashProfile> = listOf(
        // --- Classic ESP32 — flashable in-app ---
        merged("lddb", "Generic ESP32 (WROOM)", "LDDB / NodeMCU / Wemos / any plain ESP32 WROOM-32 dev board", Chip.ESP32, "esp32_lddb"),
        merged("og", "v4 (OG)", "Original Marauder v4", Chip.ESP32, "old_hardware"),
        merged("v6", "v6", "Marauder v6", Chip.ESP32, "v6"),
        merged("v6_1", "v6.1 / v6.2", "v6.1 / v6.2 · AWOK V2/V3 screen (white USB)", Chip.ESP32, "v6_1"),
        merged("v7", "v7", "Marauder v7", Chip.ESP32, "marauder_v7"),
        merged("kit", "Kit", "Marauder Kit", Chip.ESP32, "kit"),
        merged("mini", "Mini", "Marauder Mini", Chip.ESP32, "mini"),
        merged("devpro", "Dev Board Pro", "Dev Board Pro · BFFB · AWOK V3 flipper (orange USB)", Chip.ESP32, "marauder_dev_board_pro"),
        merged("cyd_2usb", "CYD 2432S028 2 USB", "CYD 2432S028 (dual USB)", Chip.ESP32, "cyd_2432S028_2usb"),
        merged("cyd", "CYD 2432S028(R)", "CYD 2432S028(R)", Chip.ESP32, "cyd_2432S028"),
        merged("cyd_guition", "RL Phantom / CYD GUITION", "RL Phantom · CYD 2432S024 GUITION", Chip.ESP32, "cyd_2432S024_guition"),
        merged("cyd_35", "CYD 3.5 inch", "CYD 3.5\" display", Chip.ESP32, "cyd_3_5_inch"),
        merged("m5stickc", "M5StickC Plus", "M5StickC Plus", Chip.ESP32, "m5stickc_plus"),
        merged("m5stickc2", "M5StickC Plus 2", "M5StickC Plus 2", Chip.ESP32, "m5stickc_plus2"),
        // --- Other chip families — pick the binary, flash with the web installer ---
        merged("flipper", "Flipper WiFi Dev Board", "Flipper Zero WiFi Dev Board · AWOK V2 flipper (orange USB)", Chip.ESP32_S2, "flipper"),
        merged("rev_feather", "S2 Reverse Feather", "ESP32-S2 Reverse TFT Feather", Chip.ESP32_S2, "rev_feather"),
        merged("multiboardS3", "Generic ESP32-S3", "Flipper Multi Board S3 / generic ESP32-S3 dev board", Chip.ESP32_S3, "multiboardS3"),
        merged("m5cardputer", "M5 Cardputer", "M5 Cardputer", Chip.ESP32_S3, "m5cardputer"),
        merged("m5cardputer_adv", "M5 Cardputer ADV", "M5 Cardputer ADV", Chip.ESP32_S3, "m5cardputer_adv"),
        merged("esp32c5", "ESP32-C5 DevKit", "ESP32-C5-DevKitC-1 (5 GHz)", Chip.ESP32_C5, "esp32c5devkitc1"),
        merged("m5nanoc6", "M5NanoC6", "M5NanoC6", Chip.ESP32_C6, "m5nanoc6"),
    )

    /** Sensible default selection: the generic classic-ESP32 (WROOM) image. */
    val DEFAULT: FlashProfile = PROFILES.first { it.id == "lddb" }

    /** Kept for the OG board / the view-model's default argument. */
    val MARAUDER_V4: FlashProfile = PROFILES.first { it.id == "og" }

    fun byId(id: String): FlashProfile? = PROFILES.firstOrNull { it.id == id }
}

enum class FlashStage { IDLE, DOWNLOAD, CONNECT, ERASE, WRITE, VERIFY, DONE, ERROR }

/** A single progress update emitted by [EspFlasher] / the download step. */
data class FlashProgress(val stage: FlashStage, val fraction: Float, val message: String)

/** UI-facing flashing state held by the view-model. */
data class FlashUiState(
    val stage: FlashStage = FlashStage.IDLE,
    val fraction: Float = 0f,
    val message: String = "",
    val log: List<String> = emptyList(),
) {
    val inProgress: Boolean
        get() = stage == FlashStage.DOWNLOAD || stage == FlashStage.CONNECT ||
            stage == FlashStage.ERASE || stage == FlashStage.WRITE || stage == FlashStage.VERIFY

    val finished: Boolean get() = stage == FlashStage.DONE || stage == FlashStage.ERROR
}
