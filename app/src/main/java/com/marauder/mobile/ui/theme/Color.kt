package com.marauder.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// --- Canvas / neutrals -------------------------------------------------------
val MarauderBg = Color(0xFF0A0C10)
val MarauderSurface = Color(0xFF12151C)
val MarauderSurfaceHi = Color(0xFF1A1F29)
val MarauderOutline = Color(0xFF262C39)
val MarauderText = Color(0xFFE6EAF2)
val MarauderTextDim = Color(0xFF8A93A6)

// --- Light theme canvas / neutrals -------------------------------------------
val MarauderBgLight = Color(0xFFFFFFFF)
val MarauderSurfaceLight = Color(0xFFF3F5F9)
val MarauderSurfaceHiLight = Color(0xFFE8ECF3)
val MarauderOutlineLight = Color(0xFFD2D8E2)
val MarauderTextLight = Color(0xFF0C1017)
val MarauderTextDimLight = Color(0xFF586173)

// --- Signature accent (the firmware's default NeoPixel colour, #00d5ff) ------
val MarauderCyan = Color(0xFF00D5FF)
// Deeper cyan for light surfaces — the bright NeoPixel cyan lacks contrast on white.
val MarauderCyanDeep = Color(0xFF008BAA)

// --- Firmware menu category colours (mirror the on-device TFT palette) -------
val CatWifi = Color(0xFF35E07B)      // WiFi       -> green
val CatBluetooth = Color(0xFF00D5FF) // Bluetooth  -> cyan
val CatGps = Color(0xFFFF5A5A)       // GPS        -> red
val CatDevice = Color(0xFF4D8BFF)    // Device     -> blue
val CatNeutral = Color(0xFF9AA3B2)   // Reboot/Back-> light grey
val CatSniffer = Color(0xFFFFD24D)   // Sniffers   -> yellow
val CatScanner = Color(0xFFFF9F33)   // Scanners   -> orange
val CatAttack = Color(0xFFFF3B47)    // Attacks    -> red
val CatGeneral = Color(0xFFB57BFF)   // General    -> purple

// --- Semantic ----------------------------------------------------------------
val Danger = Color(0xFFFF3B47)
val Success = Color(0xFF35E07B)
val Warning = Color(0xFFFFB020)
