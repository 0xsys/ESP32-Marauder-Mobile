package com.marauder.mobile.esp

import com.marauder.mobile.esp.EspProtocol.CHANGE_BAUDRATE
import com.marauder.mobile.esp.EspProtocol.CHIP_DETECT_MAGIC_REG
import com.marauder.mobile.esp.EspProtocol.ESP32_MAGIC
import com.marauder.mobile.esp.EspProtocol.FLASH_BEGIN
import com.marauder.mobile.esp.EspProtocol.FLASH_DATA
import com.marauder.mobile.esp.EspProtocol.FLASH_DEFL_BEGIN
import com.marauder.mobile.esp.EspProtocol.FLASH_DEFL_DATA
import com.marauder.mobile.esp.EspProtocol.FLASH_WRITE_SIZE
import com.marauder.mobile.esp.EspProtocol.READ_REG
import com.marauder.mobile.esp.EspProtocol.SPI_ATTACH
import com.marauder.mobile.esp.EspProtocol.SPI_FLASH_MD5
import com.marauder.mobile.esp.EspProtocol.SPI_SET_PARAMS
import com.marauder.mobile.esp.EspProtocol.STATUS_BYTES
import com.marauder.mobile.esp.EspProtocol.SYNC
import com.marauder.mobile.esp.EspProtocol.checksum
import com.marauder.mobile.esp.EspProtocol.le32
import com.marauder.mobile.esp.EspProtocol.md5Hex
import com.marauder.mobile.esp.EspProtocol.readLe32
import com.marauder.mobile.esp.EspProtocol.slipEncode
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

open class FlashException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Couldn't reach the ROM bootloader at all — retrying a different transfer mode won't help. */
class SyncException(message: String, cause: Throwable? = null) : FlashException(message, cause)

/**
 * Drives the ESP32 ROM serial bootloader over a [SerialLink] to flash one or more
 * image regions, then verifies each with the device's own MD5 and reboots into the
 * new firmware.
 *
 * Stub-less by design: it talks to the mask-ROM loader directly (no RAM stub upload),
 * which keeps the implementation small and self-contained at the cost of flashing at
 * the ROM block size. Every write is MD5-checked against the device, so a bad transfer
 * fails loudly rather than silently corrupting flash.
 *
 * Targets the classic ESP32 (e.g. marauder_v4). Other chip families need different
 * reset timing / flash offsets and are out of scope here.
 */
class EspFlasher(private val link: SerialLink) {

    private val inBuf = ArrayDeque<Byte>()

    /**
     * Flash [images] (offset → bytes), reporting progress via [onProgress].
     *
     * Tries the fast path first — a higher baud rate plus zlib-compressed transfer —
     * and transparently falls back to plain uncompressed writes if the ROM rejects
     * compression, so it never performs worse than the simplest working method. Every
     * region is MD5-verified against the device either way. Throws [FlashException] on
     * any protocol or verification failure.
     */
    suspend fun flash(images: List<Pair<Int, ByteArray>>, onProgress: (FlashProgress) -> Unit) {
        try {
            runFlash(images, onProgress, compressed = true)
        } catch (e: SyncException) {
            throw e // couldn't reach the bootloader — a different transfer mode won't help
        } catch (e: FlashException) {
            onProgress(FlashProgress(FlashStage.WRITE, 0f, "Compressed transfer failed — retrying uncompressed…"))
            runFlash(images, onProgress, compressed = false)
        }
    }

    private suspend fun runFlash(
        images: List<Pair<Int, ByteArray>>,
        onProgress: (FlashProgress) -> Unit,
        compressed: Boolean,
    ) {
        // A freshly reset chip always speaks 115200; make sure we do too before syncing.
        link.setBaud(INITIAL_BAUD)

        onProgress(FlashProgress(FlashStage.CONNECT, 0f, "Entering bootloader…"))
        connect()

        val magic = readReg(CHIP_DETECT_MAGIC_REG)
        if (magic != ESP32_MAGIC) {
            throw FlashException(
                "Unexpected chip (magic 0x%08X). This build targets marauder_v4 (ESP32).".format(magic),
            )
        }
        onProgress(FlashProgress(FlashStage.CONNECT, 0.2f, "ESP32 detected"))

        spiAttach()
        // Raise the line speed for a much faster transfer; on failure fall back to a
        // clean 115200 session (which needs the SPI pins re-attached).
        if (!trySpeedUp(FLASH_BAUD)) {
            connect()
            spiAttach()
        }
        spiSetParams(FLASH_SIZE_BYTES)

        // Compressed path pre-deflates each image; the ROM inflates it on-device.
        val payloads = images.map { (offset, data) ->
            Triple(offset, data, if (compressed) deflate(data) else data)
        }
        val total = payloads.sumOf { it.third.size }.coerceAtLeast(1)
        var sent = 0

        for ((offset, data, wire) in payloads) {
            onProgress(FlashProgress(FlashStage.ERASE, sent.toFloat() / total, "Erasing flash…"))
            if (compressed) {
                flashDeflBegin(data.size, wire.size, offset)
            } else {
                flashBegin(data.size, offset, (data.size + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE)
            }

            val blocks = (wire.size + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE
            for (seq in 0 until blocks) {
                val start = seq * FLASH_WRITE_SIZE
                val len = minOf(FLASH_WRITE_SIZE, wire.size - start)
                if (compressed) {
                    flashDeflData(wire.copyOfRange(start, start + len), seq)
                } else {
                    // Plain writes pad the trailing block to a full write size with 0xFF.
                    val block = ByteArray(FLASH_WRITE_SIZE) { 0xFF.toByte() }
                    System.arraycopy(wire, start, block, 0, len)
                    flashData(block, seq)
                }
                sent += len
                onProgress(FlashProgress(FlashStage.WRITE, sent.toFloat() / total, "Writing ${sent / 1024}/${total / 1024} KiB"))
            }

            onProgress(FlashProgress(FlashStage.VERIFY, 1f, "Verifying 0x%X…".format(offset)))
            val deviceMd5 = flashMd5(offset, data.size)
            val localMd5 = md5Hex(data)
            if (!deviceMd5.equals(localMd5, ignoreCase = true)) {
                throw FlashException("Verification failed at 0x%X (device $deviceMd5 ≠ local $localMd5)".format(offset))
            }
        }

        // No FLASH_END / FLASH_DEFL_END: the ROM loader's "stay in loader" finish is
        // unreliable and unnecessary here — pulsing EN boots straight into the new firmware.
        onProgress(FlashProgress(FlashStage.DONE, 1f, "Flash complete — rebooting device"))
        hardReset()
    }

    // --- Connection / reset --------------------------------------------------

    /** Toggle into the download-mode ROM loader and sync, retrying with resets. */
    private suspend fun connect() {
        var lastError: Exception? = null
        for (attempt in 1..CONNECT_ATTEMPTS) {
            classicReset()
            drainInput()
            repeat(SYNC_TRIES_PER_ATTEMPT) {
                try {
                    sync()
                    return
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        throw SyncException(
            "Couldn't sync with the bootloader. Put the ESP32 into boot mode and retry.",
            lastError,
        )
    }

    /**
     * Switch the link to [newBaud] for a faster transfer. The ROM replies at the old
     * baud then switches; we confirm with a register read. Returns false and reverts
     * to 115200 if the device doesn't answer at the new speed.
     */
    private suspend fun trySpeedUp(newBaud: Int): Boolean = try {
        command(CHANGE_BAUDRATE, le32(newBaud) + le32(0), timeoutMs = 3000, checkStatus = false)
        link.setBaud(newBaud)
        delay(50)
        drainInput()
        readReg(CHIP_DETECT_MAGIC_REG)
        true
    } catch (e: Exception) {
        link.setBaud(INITIAL_BAUD)
        delay(50)
        drainInput()
        false
    }

    /** esptool "classic" auto-reset: pulse EN while holding IO0 low → download mode. */
    private suspend fun classicReset() {
        link.setDtr(false); link.setRts(true)   // EN low → chip in reset
        delay(100)
        link.setDtr(true); link.setRts(false)    // IO0 low, EN high → boot into download
        delay(50)
        link.setDtr(false)                        // release IO0
        delay(50)
    }

    /** Pulse EN to run the freshly flashed firmware (IO0 released → normal boot). */
    private suspend fun hardReset() {
        link.setDtr(false)
        link.setRts(true)
        delay(100)
        link.setRts(false)
    }

    private fun sync() {
        val payload = ByteArray(36)
        payload[0] = 0x07; payload[1] = 0x07; payload[2] = 0x12; payload[3] = 0x20
        for (i in 4 until 36) payload[i] = 0x55
        command(SYNC, payload, timeoutMs = 120, checkStatus = false)
        // The ROM echoes the sync several times; drain the extras so they don't
        // bleed into the next command's response.
        repeat(7) { readFrame(20) }
    }

    private fun drainInput() {
        inBuf.clear()
        repeat(4) { if (link.read(40).isEmpty()) return }
    }

    // --- Individual commands -------------------------------------------------

    private fun readReg(reg: Int): Int =
        command(READ_REG, le32(reg), timeoutMs = 500).value

    private fun spiAttach() {
        // Chips with in-package flash (e.g. the ESP32-PICO in the M5StickC Plus 2)
        // wire the SPI flash to non-default pins; the ROM won't attach it unless we
        // pass the pad map read from eFuse. A standard board reads all-zero pads →
        // value 0 = default pins (unchanged behaviour). Mirrors esptool's
        // get_chip_spi_pads() + attach packing exactly.
        val rdata5 = readReg(EFUSE_BLK0_RDATA5_REG)
        val clk = rdata5 and 0x1F
        val q = (rdata5 ushr 5) and 0x1F
        val d = (rdata5 ushr 10) and 0x1F
        val cs = (rdata5 ushr 15) and 0x1F
        val hd = (readReg(EFUSE_BLK0_RDATA3_REG) ushr 4) and 0x1F
        val pins = if ((clk or q or d or cs or hd) == 0) 0
                   else (hd shl 24) or (cs shl 18) or (d shl 12) or (q shl 6) or clk
        // ROM loader expects two little-endian words: (pin config, is_legacy/reserved).
        command(SPI_ATTACH, le32(pins) + le32(0), timeoutMs = 3000)
    }

    private fun spiSetParams(flashSize: Int) {
        val params = le32(0) +               // fl_id
            le32(flashSize) +                // total size
            le32(0x10000) +                  // block size (64 KiB)
            le32(0x1000) +                   // sector size (4 KiB)
            le32(0x100) +                    // page size (256 B)
            le32(0xFFFF)                     // status mask
        command(SPI_SET_PARAMS, params, timeoutMs = 3000)
    }

    private fun flashBegin(size: Int, offset: Int, numBlocks: Int) {
        val params = le32(size) +            // erase size (== size on ESP32 ROM)
            le32(numBlocks) +
            le32(FLASH_WRITE_SIZE) +
            le32(offset)
        // FLASH_BEGIN erases the whole region on the ROM loader; allow it plenty of time.
        command(FLASH_BEGIN, params, timeoutMs = 60_000)
    }

    private fun flashData(block: ByteArray, seq: Int) {
        val header = le32(block.size) + le32(seq) + le32(0) + le32(0)
        command(FLASH_DATA, header + block, checksum = checksum(block), timeoutMs = 3000)
    }

    private fun flashDeflBegin(uncompressedSize: Int, compressedSize: Int, offset: Int) {
        val eraseBlocks = (uncompressedSize + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE
        val numBlocks = (compressedSize + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE
        val params = le32(eraseBlocks * FLASH_WRITE_SIZE) + // ROM erases this many bytes
            le32(numBlocks) +                               // count of compressed blocks to follow
            le32(FLASH_WRITE_SIZE) +
            le32(offset)
        // FLASH_DEFL_BEGIN erases the region; allow plenty of time.
        command(FLASH_DEFL_BEGIN, params, timeoutMs = 60_000)
    }

    private fun flashDeflData(chunk: ByteArray, seq: Int) {
        // Compressed blocks are sent at their actual length (no 0xFF padding).
        val header = le32(chunk.size) + le32(seq) + le32(0) + le32(0)
        command(FLASH_DEFL_DATA, header + chunk, checksum = checksum(chunk), timeoutMs = 5000)
    }

    /** zlib-compress [data] for the FLASH_DEFL_* path (the ROM inflates it on-device). */
    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(maxOf(64, data.size / 2))
        val buf = ByteArray(64 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun flashMd5(offset: Int, size: Int): String {
        val (_, payload) = commandRaw(
            SPI_FLASH_MD5,
            le32(offset) + le32(size) + le32(0) + le32(0),
            timeoutMs = 30_000,
        )
        // ROM returns the digest as 32 hex ASCII chars, before the status bytes.
        val hexLen = payload.size - STATUS_BYTES
        if (hexLen < 32) throw FlashException("Malformed MD5 response (${payload.size} bytes)")
        return String(payload, 0, 32, Charsets.US_ASCII)
    }

    // --- Command transport ---------------------------------------------------

    private data class Response(val value: Int, val payload: ByteArray)

    private fun command(op: Int, data: ByteArray = ByteArray(0), checksum: Int = 0, timeoutMs: Int, checkStatus: Boolean = true): Response {
        val (value, payload) = commandRaw(op, data, checksum, timeoutMs)
        if (checkStatus && payload.size >= STATUS_BYTES) {
            val status = payload[payload.size - STATUS_BYTES].toInt() and 0xFF
            if (status != 0) {
                val err = payload[payload.size - STATUS_BYTES + 1].toInt() and 0xFF
                throw FlashException("Command 0x%02X failed (status %d, error %d)".format(op, status, err))
            }
        }
        return Response(value, payload)
    }

    private fun commandRaw(op: Int, data: ByteArray, checksum: Int = 0, timeoutMs: Int): Response {
        val header = ByteArray(8)
        header[0] = 0x00                                   // direction: request
        header[1] = op.toByte()
        header[2] = (data.size and 0xFF).toByte()
        header[3] = ((data.size ushr 8) and 0xFF).toByte()
        le32(checksum).copyInto(header, 4)
        link.write(slipEncode(header + data))

        val deadline = now() + timeoutMs
        while (now() < deadline) {
            val frame = readFrame((deadline - now()).toInt().coerceAtLeast(1)) ?: continue
            if (frame.size < 8) continue
            if ((frame[0].toInt() and 0xFF) != 0x01) continue     // must be a response
            if ((frame[1].toInt() and 0xFF) != op) continue       // must match our op
            val size = (frame[2].toInt() and 0xFF) or ((frame[3].toInt() and 0xFF) shl 8)
            val value = readLe32(frame, 4)
            val end = minOf(frame.size, 8 + size)
            return Response(value, frame.copyOfRange(8, end))
        }
        throw FlashException("Timed out waiting for a reply to command 0x%02X".format(op))
    }

    /** Read one SLIP frame's inner bytes, or null if none arrives within [timeoutMs]. */
    private fun readFrame(timeoutMs: Int): ByteArray? {
        val deadline = now() + timeoutMs
        val out = ArrayList<Byte>(256)
        var started = false
        var escape = false
        while (now() < deadline) {
            if (inBuf.isEmpty()) {
                val chunk = link.read((deadline - now()).toInt().coerceIn(1, 50))
                if (chunk.isEmpty()) continue
                for (b in chunk) inBuf.addLast(b)
            }
            while (inBuf.isNotEmpty()) {
                val b = inBuf.removeFirst().toInt() and 0xFF
                if (!started) {
                    if (b == 0xC0) started = true
                    continue
                }
                if (escape) {
                    out.add(when (b) { 0xDC -> 0xC0.toByte(); 0xDD -> 0xDB.toByte(); else -> b.toByte() })
                    escape = false
                    continue
                }
                when (b) {
                    0xC0 -> if (out.isEmpty()) Unit /* run of frame delimiters */ else return out.toByteArray()
                    0xDB -> escape = true
                    else -> out.add(b.toByte())
                }
            }
        }
        return null
    }

    private fun now(): Long = System.nanoTime() / 1_000_000

    companion object {
        private const val CONNECT_ATTEMPTS = 10
        private const val SYNC_TRIES_PER_ATTEMPT = 5

        /** Every ESP32 ROM loader starts at 115200; we sync here before speeding up. */
        private const val INITIAL_BAUD = 115200

        /** Transfer speed after sync. 460800 is a widely-reliable step up from 115200. */
        private const val FLASH_BAUD = 460800

        /** marauder_v4 is a 4 MiB ESP32-WROOM; used only for SPI param bounds. */
        private const val FLASH_SIZE_BYTES = 4 * 1024 * 1024

        // eFuse BLK0 words holding the SPI flash pad map (read to attach in-package
        // flash on ESP32-PICO boards). Addresses per esptool's ESP32 target.
        private const val EFUSE_BLK0_RDATA5_REG = 0x3FF5A014.toInt()
        private const val EFUSE_BLK0_RDATA3_REG = 0x3FF5A00C.toInt()
    }
}
