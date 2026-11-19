# 🎬 Director Zone

A video editor for Android with a **3D phone-mockup mode**, animated templates, a built-in sound library and a pile of transitions. Everything renders on-device with Media3 + OpenGL — no servers, no watermark, no account.

**Download:** grab `Director-Zone.apk` from the [latest release](../../releases/latest) and install it (allow "install unknown apps").

## What's inside
| | |
|---|---|
| **Phone Studio** | Drop any video in a raymarched-style 3D phone: 16 premade animations (float, orbit, full spin, flip, rise-up, drop & bounce, dolly, cinematic roll, isometric, wobble, pop-and-spin, carousel, tumble…), 10 devices (titanium, black, gold, midnight, rose, Android punch-hole, frosted, neon frame, edge-to-edge), 14 animated backdrops (aurora, mesh, neon grid, bokeh, sun rays…), real thickness, back-face with camera bump, soft shadow, dynamic island / notch / punch-hole |
| **Templates** | 12 one-tap looks (App Drop, Spin Reveal, Flip Showcase, Cinematic Orbit, Neon Grid, Hype Reel, Daily Vlog, Retro VHS…) that set phone, filter, transition, title, music and SFX |
| **Transitions** | 21 GPU transitions (whip pans, zoom punch, spin, glitch, RGB split, pixelate, shake hit, swirl, kaleido, film burn, iris, light leak…), adjustable length, auto-whoosh on every cut |
| **Filters & colour** | 24 filters + 13 manual sliders (exposure, contrast, saturation, warmth, tint, highlights, shadows, hue, fade, glow, chromatic aberration, vignette, grain) — single-pass shader |
| **Text & stickers** | 17 text animations (typewriter, bounce, glitch, wave, word-by-word, neon pulse…), 7 fonts, colours, label pill, emoji stickers with motion |
| **Sound** | 58 original synthesized sounds & loops (UI, whooshes, impacts, risers, chimes, glitch, ambience pads, beats), voice-over recorder, import your own audio, per-sound volume / fades |
| **Editing** | split, trim, duplicate, reorder, speed 0.25–4× (audio follows), volume, Ken Burns, photos, 7 aspect ratios, fit/fill, undo/redo, auto-saved projects |
| **Export** | H.264 or HEVC, 720p / 1080p / 2K, 24/30/60 fps, up to 40 Mbps, save to gallery or share |

The preview plays the *same* composition that gets exported (just smaller).

## Build
CI builds a signed APK on every push (`.github/workflows/build.yml`). Locally: `./gradlew assembleRelease` (needs the Android SDK, JDK 17).

Regenerate the sound pack: `python3 tools/make_sounds.py` (numpy + scipy).

## Stack
Kotlin · Jetpack Compose · Media3 Transformer / CompositionPlayer / Effect · GLSL ES 2.0 shaders (`engine/Shaders.kt`).

> The release is signed with a debug-grade key committed in `signing/` so anyone can build an installable APK. Swap it for your own before publishing anywhere.
