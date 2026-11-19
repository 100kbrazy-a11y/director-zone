package com.directorzone.catalog

import com.directorzone.data.Adjust

data class FilterDef(val id: String, val name: String, val a: Adjust, val duoA: Long = 0, val duoB: Long = 0, val duo: Float = 0f, val fx: Int = 0, val swatch: Long)

/** fx: 0 none, 1 scanlines/VHS, 2 pixel-soft bloom, 3 film-halation */
val FILTERS: List<FilterDef> = listOf(
    FilterDef("none", "Original", Adjust(), swatch = 0xFF8A8F98),
    FilterDef("vivid", "Vivid", Adjust(contrast = 0.12f, saturation = 0.35f, highlights = -0.05f), swatch = 0xFFFF4D6D),
    FilterDef("cinema", "Cinema", Adjust(contrast = 0.2f, saturation = -0.05f, warmth = 0.1f, tint = -0.08f, vignette = 0.3f, shadows = -0.1f), duoA = 0xFF0B3C4D, duoB = 0xFFFFB27A, duo = 0.18f, swatch = 0xFF2F8F9D),
    FilterDef("noir", "Noir", Adjust(contrast = 0.35f, saturation = -1f, vignette = 0.45f, grain = 0.25f), swatch = 0xFF222222),
    FilterDef("mono", "Soft Mono", Adjust(contrast = -0.05f, saturation = -1f, fade = 0.12f, glow = 0.2f), swatch = 0xFF9A9A9A),
    FilterDef("film", "Warm Film", Adjust(contrast = 0.1f, warmth = 0.28f, saturation = 0.05f, grain = 0.3f, fade = 0.08f, vignette = 0.2f), swatch = 0xFFE8A55B),
    FilterDef("chrome", "Cool Chrome", Adjust(contrast = 0.18f, warmth = -0.25f, saturation = 0.1f, highlights = -0.1f), swatch = 0xFF6FA8DC),
    FilterDef("sunset", "Sunset", Adjust(warmth = 0.4f, tint = 0.12f, saturation = 0.25f, contrast = 0.1f, glow = 0.15f), duoA = 0xFF5B2A86, duoB = 0xFFFF9A5A, duo = 0.14f, swatch = 0xFFFF8A5B),
    FilterDef("pastel", "Pastel Dream", Adjust(contrast = -0.15f, saturation = -0.1f, fade = 0.22f, exposure = 0.12f, glow = 0.3f), swatch = 0xFFF4C2E0),
    FilterDef("retro", "Retro 70s", Adjust(warmth = 0.3f, tint = 0.08f, fade = 0.18f, contrast = 0.05f, grain = 0.35f, saturation = -0.08f, vignette = 0.3f), swatch = 0xFFCB8F3E),
    FilterDef("cyber", "Cyberpunk", Adjust(contrast = 0.25f, saturation = 0.4f, chroma = 0.35f, glow = 0.3f), duoA = 0xFF2B00A8, duoB = 0xFF00F0FF, duo = 0.28f, swatch = 0xFF9B4DFF),
    FilterDef("matte", "Matte", Adjust(contrast = -0.05f, fade = 0.25f, saturation = -0.12f, shadows = 0.1f), swatch = 0xFF7B7F86),
    FilterDef("golden", "Golden Hour", Adjust(warmth = 0.45f, exposure = 0.1f, glow = 0.35f, saturation = 0.15f, vignette = 0.15f), swatch = 0xFFFFC145),
    FilterDef("forest", "Moody Forest", Adjust(contrast = 0.2f, saturation = -0.2f, warmth = -0.1f, tint = -0.15f, shadows = -0.15f, vignette = 0.4f), swatch = 0xFF2E5339),
    FilterDef("bleach", "Bleach Bypass", Adjust(contrast = 0.4f, saturation = -0.5f, highlights = 0.1f, grain = 0.2f), swatch = 0xFFB0B4B9),
    FilterDef("fade", "Faded", Adjust(fade = 0.3f, contrast = -0.1f, saturation = -0.2f), swatch = 0xFFC9C3B8),
    FilterDef("neon", "Neon Night", Adjust(contrast = 0.3f, saturation = 0.5f, shadows = -0.2f, glow = 0.4f, chroma = 0.2f), duoA = 0xFF12002F, duoB = 0xFFFF3DCB, duo = 0.22f, swatch = 0xFFFF3DCB),
    FilterDef("polaroid", "Polaroid", Adjust(warmth = 0.12f, tint = 0.05f, fade = 0.15f, contrast = -0.05f, saturation = -0.05f, vignette = 0.35f, grain = 0.15f), swatch = 0xFFE9DCC3),
    FilterDef("dream", "Dream Glow", Adjust(glow = 0.7f, exposure = 0.08f, saturation = 0.1f, fade = 0.08f), swatch = 0xFFFFD6F6),
    FilterDef("vhs", "VHS", Adjust(chroma = 0.5f, grain = 0.4f, saturation = 0.15f, contrast = 0.1f), fx = 1, swatch = 0xFFB35B5B),
    FilterDef("duopink", "Duo Pink", Adjust(contrast = 0.15f), duoA = 0xFF2A0A4A, duoB = 0xFFFF6BB5, duo = 1f, swatch = 0xFFFF6BB5),
    FilterDef("duoblue", "Duo Blue", Adjust(contrast = 0.15f), duoA = 0xFF021B3B, duoB = 0xFF68D0FF, duo = 1f, swatch = 0xFF68D0FF),
    FilterDef("sepia", "Sepia", Adjust(saturation = -0.3f, fade = 0.1f, vignette = 0.3f, grain = 0.2f), duoA = 0xFF2B1A0B, duoB = 0xFFF2D6A8, duo = 0.85f, swatch = 0xFF9C7A4F),
    FilterDef("hdr", "HDR Pop", Adjust(contrast = 0.3f, saturation = 0.3f, highlights = -0.3f, shadows = 0.3f), swatch = 0xFF3FD0A5),
)
fun filterById(id: String) = FILTERS.firstOrNull { it.id == id } ?: FILTERS[0]

/** mode id is passed to the GLSL transition function. A transition can appear in several categories. */
data class TransitionDef(val id: String, val name: String, val mode: Int, val sound: String?, val cats: List<String> = listOf("Basic"))
val TRANSITION_CATS = listOf("Basic", "Camera", "Light", "Wipe", "Slide", "Blur", "Glitch", "Cinematic", "Distortion", "Retro")
val TRANSITIONS: List<TransitionDef> = listOf(
    TransitionDef("none", "None", 0, null, listOf("Basic")),
    TransitionDef("fade", "Dip to Black", 1, null, listOf("Basic", "Cinematic")),
    TransitionDef("flash", "Dip to White", 2, "Soft Whoosh", listOf("Basic", "Light")),
    TransitionDef("zoomin", "Zoom In", 3, "Fast Whoosh", listOf("Basic", "Camera", "Blur")),
    TransitionDef("zoomout", "Zoom Out", 4, "Air Whoosh", listOf("Camera", "Blur")),
    TransitionDef("whipl", "Whip Left", 5, "Swipe Left", listOf("Slide", "Camera", "Blur")),
    TransitionDef("whipr", "Whip Right", 6, "Swipe Right", listOf("Slide", "Camera", "Blur")),
    TransitionDef("whipu", "Whip Up", 7, "Fast Whoosh", listOf("Slide", "Camera", "Blur")),
    TransitionDef("whipd", "Whip Down", 8, "Fast Whoosh", listOf("Slide", "Camera", "Blur")),
    TransitionDef("spin", "Spin", 9, "Spin", listOf("Camera", "Distortion", "Blur")),
    TransitionDef("glitch", "Glitch", 10, "Glitch", listOf("Glitch")),
    TransitionDef("rgb", "RGB Split", 11, "Zap", listOf("Glitch")),
    TransitionDef("blur", "Blur", 12, "Air Whoosh", listOf("Basic", "Blur")),
    TransitionDef("pixel", "Pixelate", 13, "Digital Blip", listOf("Glitch", "Retro")),
    TransitionDef("shake", "Shake Hit", 14, "Thud", listOf("Camera", "Glitch")),
    TransitionDef("swirl", "Swirl", 15, "Flip", listOf("Distortion")),
    TransitionDef("mirror", "Kaleido", 16, "Sparkle", listOf("Distortion")),
    TransitionDef("burn", "Film Burn", 17, "Cinematic Whoosh", listOf("Light", "Cinematic", "Retro")),
    TransitionDef("iris", "Iris", 18, "Pop", listOf("Wipe", "Cinematic")),
    TransitionDef("push", "Slide Push", 19, "Swipe Left", listOf("Slide", "Basic")),
    TransitionDef("lightleak", "Light Leak", 20, "Dreamy Swell", listOf("Light", "Cinematic")),
    TransitionDef("bounce", "Stretch", 21, "Punch", listOf("Camera", "Distortion")),
    TransitionDef("wipel", "Wipe Right", 22, "Soft Whoosh", listOf("Wipe", "Basic")),
    TransitionDef("wiper", "Wipe Left", 23, "Soft Whoosh", listOf("Wipe")),
    TransitionDef("wipeu", "Wipe Down", 24, "Soft Whoosh", listOf("Wipe")),
    TransitionDef("wiped", "Wipe Up", 25, "Soft Whoosh", listOf("Wipe")),
    TransitionDef("diag", "Diagonal Wipe", 26, "Fast Whoosh", listOf("Wipe")),
    TransitionDef("clock", "Clock Wipe", 27, "Swish In", listOf("Wipe", "Retro")),
    TransitionDef("split", "Barn Doors", 28, "Soft Whoosh", listOf("Wipe", "Cinematic")),
    TransitionDef("vhs", "VHS Tape", 29, "Static Burst", listOf("Retro", "Glitch")),
    TransitionDef("oldtv", "Old TV", 30, "Power Down", listOf("Retro")),
    TransitionDef("flare", "Anamorphic Flare", 31, "Cinematic Whoosh", listOf("Light", "Cinematic")),
    TransitionDef("ripple", "Ripple", 32, "Dreamy Swell", listOf("Distortion")),
    TransitionDef("fisheye", "Fisheye", 33, "Air Whoosh", listOf("Distortion", "Camera")),
    TransitionDef("bloom", "Bloom", 34, "Sparkle", listOf("Light", "Blur")),
)
fun transitionById(id: String) = TRANSITIONS.firstOrNull { it.id == id } ?: TRANSITIONS[0]

data class TextAnimDef(val id: String, val name: String)
val TEXT_ANIMS = listOf(
    TextAnimDef("none", "Static"), TextAnimDef("fade", "Fade"), TextAnimDef("pop", "Pop"), TextAnimDef("slideup", "Slide Up"),
    TextAnimDef("slidedown", "Slide Down"), TextAnimDef("typewriter", "Typewriter"), TextAnimDef("bounce", "Bounce"),
    TextAnimDef("glitch", "Glitch"), TextAnimDef("wave", "Wave"), TextAnimDef("zoom", "Zoom"), TextAnimDef("blurin", "Blur In"),
    TextAnimDef("words", "Word by Word"), TextAnimDef("pulse", "Neon Pulse"), TextAnimDef("rotate", "Rotate In"),
    TextAnimDef("shake", "Shake"), TextAnimDef("float", "Float"), TextAnimDef("spin", "Spin"), TextAnimDef("stretch", "Stretch"),
)

data class FontDef(val id: String, val name: String, val cat: String)
val FONT_CATS = listOf("All", "Clean", "Display", "Script", "Fun")
val FONTS = listOf(
    FontDef("bold", "Sans Bold", "Clean"), FontDef("black", "Sans Black", "Clean"), FontDef("light", "Sans Light", "Clean"), FontDef("medium", "Sans Medium", "Clean"),
    FontDef("montserrat", "Montserrat", "Clean"), FontDef("poppins", "Poppins Bold", "Clean"), FontDef("oswald", "Oswald", "Clean"), FontDef("condensed", "Condensed", "Clean"),
    FontDef("anton", "Anton", "Display"), FontDef("bebas", "Bebas Neue", "Display"), FontDef("abril", "Abril Fatface", "Display"), FontDef("playfair", "Playfair", "Display"),
    FontDef("serif", "Serif Bold", "Display"), FontDef("monoton", "Monoton", "Display"), FontDef("orbitron", "Orbitron", "Display"), FontDef("righteous", "Righteous", "Display"),
    FontDef("pacifico", "Pacifico", "Script"), FontDef("lobster", "Lobster", "Script"), FontDef("caveat", "Caveat", "Script"), FontDef("script", "Cursive", "Script"),
    FontDef("bangers", "Bangers", "Fun"), FontDef("fugaz", "Fugaz One", "Fun"), FontDef("arcade", "Press Start", "Fun"), FontDef("mono", "Mono Bold", "Fun"),
)
fun fontDef(id: String) = FONTS.firstOrNull { it.id == id } ?: FONTS[0]
val TEXT_COLORS = listOf("FFFFFF", "000000", "F5F5DC", "FF5CA8", "FF6B6B", "FFA63D", "FFD166", "FFF04D", "7CFFB2", "2FD99A", "68D0FF", "33AFFF", "9B7BFF", "B14DFF", "FF3DCB", "8A8F98")
val EMOJI = listOf("🔥", "✨", "💖", "😍", "😂", "🤯", "👀", "💯", "🚀", "⚡", "🎬", "🎵", "⭐", "🌈", "👑", "💎", "🎉", "🫶", "😎", "🥹", "💥", "🌙", "🍿", "📱")

data class RatioDef(val id: String, val name: String, val w: Int, val h: Int)
val RATIOS = listOf(RatioDef("9:16", "TikTok / Reels", 9, 16), RatioDef("16:9", "YouTube", 16, 9), RatioDef("1:1", "Square", 1, 1), RatioDef("4:5", "Portrait", 4, 5), RatioDef("3:4", "Classic", 3, 4), RatioDef("21:9", "Cinema", 21, 9), RatioDef("4:3", "Standard", 4, 3))
fun ratioById(id: String) = RATIOS.firstOrNull { it.id == id } ?: RATIOS[0]

data class BezelDef(val id: String, val name: String, val frame: Long, val back: Long, val island: Int, val corner: Float, val bezel: Float)
val BEZELS = listOf(
    BezelDef("titanium", "Natural Titanium", 0xFFB9B3A9, 0xFFA8A296, 1, 0.115f, 0.017f),
    BezelDef("blackti", "Black Titanium", 0xFF2A2B2E, 0xFF1C1D1F, 1, 0.115f, 0.017f),
    BezelDef("silver", "Silver", 0xFFDCDDDF, 0xFFCFD1D4, 1, 0.115f, 0.017f),
    BezelDef("gold", "Gold", 0xFFD9BE8C, 0xFFCDB07A, 1, 0.115f, 0.017f),
    BezelDef("blue", "Midnight Blue", 0xFF1F2E4A, 0xFF1A2640, 2, 0.12f, 0.018f),
    BezelDef("rose", "Rose", 0xFFE5B7B0, 0xFFD9A8A1, 3, 0.11f, 0.016f),
    BezelDef("punch", "Android Punch-hole", 0xFF3A3D42, 0xFF2D2F33, 3, 0.085f, 0.014f),
    BezelDef("frost", "Frosted White", 0xFFF3F4F6, 0xFFE6E8EB, 0, 0.1f, 0.015f),
    BezelDef("neonframe", "Neon Frame", 0xFFFF3DCB, 0xFF3A0A33, 1, 0.115f, 0.016f),
    BezelDef("bare", "Edge-to-edge", 0xFF111111, 0xFF111111, 0, 0.1f, 0.006f),
)
fun bezelById(id: String) = BEZELS.firstOrNull { it.id == id } ?: BEZELS[0]

data class PhoneAnimDef(val id: String, val name: String, val code: Int)
val PHONE_ANIMS = listOf(
    PhoneAnimDef("float", "Float", 0), PhoneAnimDef("orbit", "Orbit", 1), PhoneAnimDef("spin", "Full Spin", 2), PhoneAnimDef("flip", "Flip Over", 3),
    PhoneAnimDef("hover", "Hover Tilt", 4), PhoneAnimDef("reveal", "Zoom Reveal", 5), PhoneAnimDef("slideup", "Rise Up", 6), PhoneAnimDef("drop", "Drop & Bounce", 7),
    PhoneAnimDef("dolly", "Dolly Push", 8), PhoneAnimDef("roll", "Cinematic Roll", 9), PhoneAnimDef("iso", "Isometric", 10), PhoneAnimDef("wobble", "Wobble", 11),
    PhoneAnimDef("popspin", "Pop & Spin", 12), PhoneAnimDef("carousel", "Carousel", 13), PhoneAnimDef("still", "Still", 14), PhoneAnimDef("tumble", "Tumble", 15),
)
fun phoneAnimById(id: String) = PHONE_ANIMS.firstOrNull { it.id == id } ?: PHONE_ANIMS[0]

/** c1,c2,c3 are the gradient colours; mode picks the animated background shader. */
data class BgDef(val id: String, val name: String, val mode: Int, val c1: Long, val c2: Long, val c3: Long)
val BACKGROUNDS = listOf(
    BgDef("aurora", "Aurora", 0, 0xFF0B1026, 0xFF7C5CFF, 0xFF18E0C0),
    BgDef("sunset", "Sunset Mesh", 1, 0xFF2B0B3F, 0xFFFF5C8A, 0xFFFFB86B),
    BgDef("ocean", "Deep Ocean", 1, 0xFF021B3B, 0xFF0A74DA, 0xFF3EE6D0),
    BgDef("candy", "Candy", 1, 0xFFFFD1E8, 0xFFB8A6FF, 0xFF9AF0FF),
    BgDef("midnight", "Midnight", 2, 0xFF05050A, 0xFF1B1B3A, 0xFF4B3FA0),
    BgDef("studio", "Studio Light", 3, 0xFFEDEEF2, 0xFFCFD3DB, 0xFFFFFFFF),
    BgDef("black", "Pure Black", 3, 0xFF000000, 0xFF0A0A0A, 0xFF1A1A1A),
    BgDef("grid", "Neon Grid", 4, 0xFF07040F, 0xFFB14DFF, 0xFF2EF2FF),
    BgDef("dots", "Dot Matrix", 5, 0xFF0D0F1A, 0xFF2B3050, 0xFF68D0FF),
    BgDef("bokeh", "Bokeh", 6, 0xFF120A24, 0xFFFF7AC8, 0xFF7AB8FF),
    BgDef("lava", "Lava", 1, 0xFF1A0505, 0xFFFF3B1F, 0xFFFFB400),
    BgDef("mint", "Fresh Mint", 0, 0xFF062B22, 0xFF18C48B, 0xFFB6FFE0),
    BgDef("rays", "Sun Rays", 7, 0xFF0E1230, 0xFFFFC857, 0xFFFF7A59),
    BgDef("paper", "Paper", 3, 0xFFF6F1E7, 0xFFE9E1D0, 0xFFFFFFFF),
)
fun bgById(id: String) = BACKGROUNDS.firstOrNull { it.id == id } ?: BACKGROUNDS[0]

data class TemplateDef(
    val id: String, val name: String, val blurb: String, val emoji: String, val accent: Long,
    val phone: Boolean, val bezel: String, val anim: String, val bg: String, val filter: String,
    val transition: String, val ratio: String, val title: String?, val titleAnim: String,
    val music: String?, val sfx: List<String>, val clipMs: Long, val category: String = "",
)
private val BASE_TEMPLATES = listOf(
    TemplateDef("appdrop", "App Drop", "Phone floats over an aurora with a punchy title", "📱", 0xFF7C5CFF, true, "titanium", "float", "aurora", "none", "flash", "9:16", "Now Live", "pop", "Tech Pad", listOf("Pop", "Sparkle"), 5000),
    TemplateDef("spinreveal", "Spin Reveal", "Full 360° phone spin, big reveal", "🌀", 0xFFFF5CA8, true, "blackti", "popspin", "sunset", "vivid", "spin", "9:16", "Meet the update", "zoom", "Dreamy Pad", listOf("Spin", "Hit"), 6000),
    TemplateDef("flipshow", "Flip Showcase", "Flip front/back between features", "🔄", 0xFF18E0C0, true, "silver", "flip", "studio", "none", "whipl", "9:16", "Two sides", "slideup", "Calm Pad", listOf("Flip", "Ding"), 5500),
    TemplateDef("cinemaorbit", "Cinematic Orbit", "Slow orbit, teal & orange grade", "🎬", 0xFFFFB27A, true, "blackti", "orbit", "midnight", "cinema", "fade", "16:9", "A Film By You", "fade", "Cinematic Pad", listOf("Cinematic Whoosh", "Boom"), 6500),
    TemplateDef("neongrid", "Neon Grid", "Synthwave grid, glitch cuts", "🌃", 0xFFB14DFF, true, "neonframe", "hover", "grid", "neon", "glitch", "9:16", "ONLINE", "glitch", "Tech Pad", listOf("Glitch", "Power Up"), 4000),
    TemplateDef("rise", "Rise Up", "Phone rises into frame, soft pastel", "🌸", 0xFFF4C2E0, true, "rose", "slideup", "candy", "pastel", "blur", "9:16", "New look", "blurin", "Dreamy Pad", listOf("Soft Whoosh", "Chime"), 5000),
    TemplateDef("dropbounce", "Drop & Bounce", "Phone drops in with a bounce", "⬇️", 0xFFFFC857, true, "gold", "drop", "rays", "vivid", "zoomin", "9:16", "Boom.", "bounce", "Chill Groove", listOf("Thud", "Punch"), 4500),
    TemplateDef("vlog", "Daily Vlog", "Warm film grade, quick whip cuts", "☀️", 0xFFE8A55B, false, "titanium", "float", "aurora", "film", "whipl", "9:16", "my day", "typewriter", "Lofi Beat", listOf("Swipe Left", "Pop"), 3000),
    TemplateDef("hype", "Hype Reel", "Shake hits, flashes, trap beat", "⚡", 0xFFFF3B1F, false, "titanium", "float", "aurora", "hdr", "shake", "9:16", "LET'S GO", "shake", "Trap Beat", listOf("Boom", "Hit"), 2200),
    TemplateDef("travel", "Travel Film", "Golden hour, smooth zooms", "🌅", 0xFFFFC145, false, "titanium", "float", "aurora", "golden", "zoomin", "16:9", "Wanderlust", "slideup", "Calm Pad", listOf("Air Whoosh"), 3500),
    TemplateDef("retro", "Retro VHS", "Tape grain, scanlines, glitches", "📼", 0xFFB35B5B, false, "titanium", "float", "aurora", "vhs", "rgb", "4:5", "PLAY ▶", "typewriter", "Lofi Beat", listOf("Static Burst", "Digital Blip"), 3000),
    TemplateDef("clean", "Clean Promo", "Studio white, soft whips, no fuss", "🤍", 0xFFCFD3DB, true, "silver", "hover", "studio", "none", "whipr", "1:1", "Simple. Fast.", "fade", "Chill Groove", listOf("Soft Click", "Soft Whoosh"), 4500),
)

private val TEMPLATE_CAT = mapOf("appdrop" to "Phone Showcase", "spinreveal" to "Phone Showcase", "flipshow" to "Phone Showcase", "cinemaorbit" to "Cinematic", "neongrid" to "Gaming",
    "rise" to "Fashion", "dropbounce" to "Promotional", "vlog" to "Vlog", "hype" to "Social", "travel" to "Travel", "retro" to "Retro", "clean" to "Minimal")
val TEMPLATE_CATS = listOf("Phone Showcase", "Product", "Music", "Social", "Cinematic", "Vlog", "Travel", "Fashion", "Gaming", "Promotional", "Minimal", "Retro")
private val EXTRA_TEMPLATES = listOf(
    TemplateDef("prodspot", "Product Spotlight", "Isometric phone on studio white", "🛍️", 0xFFCFD3DB, true, "silver", "iso", "studio", "none", "whipr", "1:1", "New Arrival", "slideup", "Chill Groove", listOf("Soft Click", "Soft Whoosh"), 4000, "Product"),
    TemplateDef("reveal", "Product Reveal", "Punchy zooms, vivid colour", "📦", 0xFFFF4D6D, false, "titanium", "float", "aurora", "vivid", "zoomin", "4:5", "Just Dropped", "pop", "House Beat", listOf("Pop", "Sparkle"), 3000, "Product"),
    TemplateDef("beatdrop", "Beat Drop", "Hard cuts, neon grade, trap drop", "🎧", 0xFF3DFFE0, false, "titanium", "float", "aurora", "neon", "flash", "9:16", "DROP", "shake", "Trap Beat", listOf("Bass Drop", "Boom"), 1800, "Music"),
    TemplateDef("lyric", "Lyric Vibes", "Phone orbiting in a midnight glow", "🎵", 0xFF9B7BFF, true, "blackti", "orbit", "midnight", "dream", "blur", "9:16", "now playing", "fade", "Dreamy Pad", listOf("Sparkle"), 5000, "Music"),
    TemplateDef("story", "Story Pop", "Bouncy titles, RGB-split cuts", "💬", 0xFFFF6BB5, false, "titanium", "float", "aurora", "vivid", "rgb", "9:16", "swipe up", "bounce", "House Beat", listOf("Pop", "Notify"), 2600, "Social"),
    TemplateDef("trailer", "Trailer", "Scope frame, flares, big riser", "🎞️", 0xFF68D0FF, false, "titanium", "float", "aurora", "cinema", "flare", "21:9", "COMING SOON", "fade", "Cinematic Pad", listOf("Riser Long", "Boom"), 4500, "Cinematic"),
    TemplateDef("roadtrip", "Road Trip", "Sunset grade, smooth whips", "🚗", 0xFFFF8A5B, false, "titanium", "float", "aurora", "sunset", "whipl", "9:16", "road trip", "slideup", "Lofi Beat", listOf("Swipe Left"), 3200, "Travel"),
    TemplateDef("runway", "Runway", "Matte grade, clean wipes", "👗", 0xFFE5B7B0, false, "titanium", "float", "aurora", "matte", "wipel", "4:5", "SS/26", "slideup", "Chill Groove", listOf("Swipe Right"), 2600, "Fashion"),
    TemplateDef("levelup", "Level Up", "Glitch cuts, arcade energy", "🎮", 0xFF9B4DFF, false, "titanium", "float", "aurora", "cyber", "glitch", "16:9", "LEVEL UP", "glitch", "Trap Beat", listOf("Level Up", "Power Up"), 2400, "Gaming"),
    TemplateDef("sale", "Sale Blast", "Phone drop + flash cuts", "🏷️", 0xFFFFC857, true, "gold", "popspin", "rays", "vivid", "flash", "9:16", "50% OFF", "pop", "House Beat", listOf("Coin", "Hit"), 3500, "Promotional"),
    TemplateDef("quiet", "Quiet Frame", "Soft fades, no title, no fuss", "◻️", 0xFFC9C3B8, false, "titanium", "float", "aurora", "matte", "fade", null, "fade", "Calm Pad", emptyList(), 4000, "Minimal"),
    TemplateDef("super8", "Super 8", "Grainy 4:3 film, TV-off cuts", "📽️", 0xFFCB8F3E, false, "titanium", "float", "aurora", "retro", "oldtv", "4:3", "1984", "typewriter", "Lofi Beat", listOf("Rewind", "Tick"), 3000, "Retro"),
    TemplateDef("diary", "Video Diary", "Warm film, typed captions", "📖", 0xFFE8A55B, false, "titanium", "float", "aurora", "film", "wipel", "9:16", "dear diary", "typewriter", "Lofi Beat", listOf("Typewriter"), 3000, "Vlog"),
)
val TEMPLATES: List<TemplateDef> = BASE_TEMPLATES.map { it.copy(category = TEMPLATE_CAT[it.id] ?: "Promotional") } + EXTRA_TEMPLATES

// ---------------------------------------------------------------- Effects (look presets that stack on filter + adjust, rendered by the grade shader)
data class FxDef(val id: String, val name: String, val cat: String, val a: Adjust, val fx: Int = 0)
val FX_CATS = listOf("Trending", "Cinematography", "Light & Blur", "Glitch", "Retro", "Detail")
val EFFECTS: List<FxDef> = listOf(
    FxDef("none", "None", "Trending", Adjust()),
    FxDef("glowpop", "Glow Pop", "Trending", Adjust(glow = 0.6f, saturation = 0.15f)),
    FxDef("punch", "Cine Punch", "Trending", Adjust(contrast = 0.25f, vignette = 0.3f, fade = 0.05f)),
    FxDef("softfocus", "Soft Focus", "Trending", Adjust(glow = 0.5f, fade = 0.1f, exposure = 0.08f)),
    FxDef("hypervivid", "Hyper Vivid", "Trending", Adjust(saturation = 0.5f, contrast = 0.2f, sharpen = 0.4f)),
    FxDef("flare", "Anamorphic Flare", "Cinematography", Adjust(glow = 0.55f, warmth = 0.1f, chroma = 0.15f, vignette = 0.25f)),
    FxDef("halation", "Halation", "Cinematography", Adjust(glow = 0.7f, warmth = 0.2f)),
    FxDef("grain35", "35mm Grain", "Cinematography", Adjust(grain = 0.55f, fade = 0.08f, contrast = 0.1f)),
    FxDef("prism", "Prism Blur", "Cinematography", Adjust(chroma = 0.6f, glow = 0.3f)),
    FxDef("vignette", "Vignette", "Cinematography", Adjust(vignette = 0.7f)),
    FxDef("tealorange", "Teal & Orange", "Cinematography", Adjust(warmth = 0.2f, tint = -0.1f, contrast = 0.2f, saturation = 0.1f)),
    FxDef("haze", "Dream Haze", "Light & Blur", Adjust(glow = 0.8f, fade = 0.12f, exposure = 0.1f)),
    FxDef("bloom", "Bloom", "Light & Blur", Adjust(glow = 1f)),
    FxDef("mist", "Soft Mist", "Light & Blur", Adjust(fade = 0.2f, glow = 0.4f, contrast = -0.1f)),
    FxDef("overexp", "Overexposed", "Light & Blur", Adjust(exposure = 0.5f, highlights = -0.2f, glow = 0.3f)),
    FxDef("lowkey", "Low Key", "Light & Blur", Adjust(exposure = -0.35f, contrast = 0.3f, vignette = 0.4f)),
    FxDef("rgbshift", "RGB Shift", "Glitch", Adjust(chroma = 0.8f)),
    FxDef("vhstape", "VHS Tape", "Glitch", Adjust(chroma = 0.5f, grain = 0.35f), fx = 1),
    FxDef("static", "Static", "Glitch", Adjust(grain = 0.9f, contrast = 0.15f)),
    FxDef("digital", "Digital Noise", "Glitch", Adjust(grain = 0.6f, chroma = 0.35f, saturation = 0.2f)),
    FxDef("scan", "Scan Lines", "Glitch", Adjust(contrast = 0.1f), fx = 1),
    FxDef("oldfilm", "Old Film", "Retro", Adjust(grain = 0.6f, fade = 0.2f, vignette = 0.45f, warmth = 0.2f, saturation = -0.15f)),
    FxDef("polaroid", "Faded Print", "Retro", Adjust(fade = 0.3f, warmth = 0.15f, vignette = 0.35f)),
    FxDef("sepiawash", "Sepia Wash", "Retro", Adjust(saturation = -0.5f, warmth = 0.35f, fade = 0.12f)),
    FxDef("super8", "Super 8", "Retro", Adjust(grain = 0.5f, warmth = 0.3f, contrast = 0.15f, vignette = 0.5f, chroma = 0.15f)),
    FxDef("dispo", "Disposable", "Retro", Adjust(exposure = 0.15f, contrast = 0.2f, warmth = 0.2f, vignette = 0.4f, grain = 0.3f)),
    FxDef("crisp", "Crisp", "Detail", Adjust(sharpen = 0.7f)),
    FxDef("clarity", "Clarity", "Detail", Adjust(sharpen = 0.5f, contrast = 0.15f)),
    FxDef("punchy", "Punchy Detail", "Detail", Adjust(sharpen = 0.6f, contrast = 0.2f, saturation = 0.2f)),
    FxDef("softskin", "Soft & Clean", "Detail", Adjust(sharpen = -0.0f, glow = 0.25f, fade = 0.05f, contrast = -0.05f)),
)
fun fxById(id: String) = EFFECTS.firstOrNull { it.id == id } ?: EFFECTS[0]
