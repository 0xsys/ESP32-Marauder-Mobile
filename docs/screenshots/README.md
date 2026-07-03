# Screenshots

Images referenced by the root `README.md`. All are stripped of metadata (EXIF/GPS).

| File | What |
|------|------|
| `phone-esp32.jpg` | Photo of the phone connected to the ESP32 (hero image) |
| `menu.png` | Main menu — centered logo, live status panel, credit |
| `wifi.png` | WiFi menu — sniffers, scanners, attacks, general |
| `console.png` | Raw serial console with the `jsoninfo` handshake |

To add or replace shots, drop the file here and update the `## Screenshots`
section in the root `README.md`. Strip metadata first, e.g.
`magick in.png -strip out.png` (PNG) or `jpegtran -copy none in.jpg > out.jpg` (JPG).
