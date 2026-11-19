package com.directorzone.engine

import android.graphics.*
import android.util.Size
import androidx.media3.effect.BitmapOverlay
import com.directorzone.data.TextItem
import kotlin.math.*

/** Renders every text / sticker active at a timestamp into one transparent full-frame bitmap. */
class TextBitmapOverlay(private val texts: List<TextItem>, private val w: Int, private val h: Int) : BitmapOverlay() {
    private val bmps = arrayOf(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
    private val canvases = bmps.map { Canvas(it) }
    private var flip = 0
    private val empty = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        val active = texts.filter { ms >= it.startMs && ms < it.startMs + it.durMs }
        if (active.isEmpty()) return empty
        flip = 1 - flip
        val bmp = bmps[flip]; val c = canvases[flip]
        bmp.eraseColor(Color.TRANSPARENT)
        for (t in active) TextPainter.draw(c, paint, t, ms - t.startMs, w, h)
        return bmp
    }
}

object TextPainter {
    private fun outCubic(x: Float): Float { val c = 1f - x.coerceIn(0f, 1f); return 1f - c * c * c }
    private fun outBack(x: Float): Float { val c = x.coerceIn(0f, 1f); val s = 1.70158f; val k = c - 1f; return 1f + (s + 1f) * k * k * k + s * k * k }
    private fun hash(a: Int): Float { var x = a * 374761393 + 668265263; x = (x xor (x shr 13)) * 1274126177; return ((x xor (x shr 16)) and 0xFFFF) / 65535f }

    /** set once from the Application so bundled fonts can be loaded from assets/fonts */
    var assets: android.content.res.AssetManager? = null
    private val cache = HashMap<String, Typeface>()
    val FILES = mapOf("anton" to "anton", "bebas" to "bebas", "pacifico" to "pacifico", "arcade" to "arcade", "righteous" to "righteous", "bangers" to "bangers",
        "lobster" to "lobster", "poppins" to "poppins", "abril" to "abril", "monoton" to "monoton", "fugaz" to "fugaz", "playfair" to "playfair",
        "montserrat" to "montserrat", "oswald" to "oswald", "orbitron" to "orbitron", "caveat" to "caveat")

    fun typeface(font: String): Typeface = cache.getOrPut(font) {
        val f = FILES[font]
        val am = assets
        if (f != null && am != null) try { Typeface.createFromAsset(am, "fonts/$f.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        else when (font) {
            "black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "serif" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            "mono" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            "script" -> Typeface.create("cursive", Typeface.BOLD)
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            "medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    private fun parse(hex: String): Int = try { Color.parseColor("#$hex") } catch (e: Exception) { Color.WHITE }

    private class Glyph(val s: String, var x: Float, var w: Float)

    fun draw(c: Canvas, p: Paint, t: TextItem, local: Long, W: Int, H: Int) {
        val px = (t.size * H).coerceIn(8f, H * 0.6f)
        p.reset(); p.isAntiAlias = true
        p.typeface = if (t.sticker) Typeface.DEFAULT else typeface(t.font)
        p.textSize = px
        val lines = t.text.split("\n")
        val perWord = t.anim == "words"
        var fit = 1f
        val maxW = lines.maxOf { p.measureText(it) }
        if (maxW > W * 0.92f) fit = W * 0.92f / maxW
        p.textSize = px * fit
        val lineH = p.textSize * t.lineSp
        val total = lines.size * lineH
        val cx = t.x * W; val cy = t.y * H
        val outA = ((t.durMs - local) / 280f).coerceIn(0f, 1f)
        val inA = (local / 300f).coerceIn(0f, 1f)
        val baseA = min(outA, if (t.anim == "none" || t.anim == "typewriter" || t.anim == "words") 1f else inA)
        val color = parse(t.color)

        c.save()
        c.rotate(t.rotation, cx, cy)
        // pill background
        if (t.pill && !t.sticker) {
            val bw = lines.maxOf { p.measureText(it) } + p.textSize * 0.9f
            val bh = total + p.textSize * 0.35f
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = parse(t.pillColor); alpha = (255 * t.bgAlpha * baseA).toInt().coerceIn(0, 255) }
            c.drawRoundRect(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, bh * 0.35f, bh * 0.35f, bp)
        }
        var idx = 0
        val totalUnits = if (perWord) t.text.split(Regex("\\s+")).size else t.text.replace("\n", "").length
        val track = t.letterSp * p.textSize
        val laid = ArrayList<Pair<List<Glyph>, Float>>()
        for (line in lines) {
            val units = ArrayList<Glyph>()
            if (perWord) {
                var x = 0f
                val words = line.split(" ")
                for ((i, wd) in words.withIndex()) { val s = if (i < words.size - 1) "$wd " else wd; val wi = p.measureText(s); units.add(Glyph(s, x, wi)); x += wi + track }
            } else {
                var x = 0f
                for (ch in line) { val s = ch.toString(); val wi = p.measureText(s); units.add(Glyph(s, x, wi)); x += wi + track }
            }
            val lw = if (units.isEmpty()) 0f else units.last().x + units.last().w
            laid.add(units to lw)
        }
        val blockW = laid.maxOfOrNull { it.second } ?: 0f
        for ((li, pair) in laid.withIndex()) {
            val (units, lw) = pair
            val baseY = cy - total / 2 + li * lineH + lineH * 0.78f
            val left = when (t.align) { "left" -> cx - blockW / 2; "right" -> cx + blockW / 2 - lw; else -> cx - lw / 2 }
            for (u in units) {
                val ux = left + u.x
                drawUnit(c, p, t, u.s, ux, baseY, u.w, idx, totalUnits, local, color, baseA, W, H)
                idx++
            }
        }
        c.restore()
    }

    private fun drawUnit(c: Canvas, p: Paint, t: TextItem, s: String, x: Float, y: Float, uw: Float, i: Int, n: Int, local: Long, color: Int, baseA: Float, W: Int, H: Int) {
        var a = baseA; var dx = 0f; var dy = 0f; var sx = 1f; var sy = 1f; var rot = 0f
        var blur = 0f; var glow = 0f; var split = false
        val lt = local.toFloat()
        val anim = if (t.sticker && t.anim == "none") "float" else t.anim
        when (anim) {
            "pop" -> { val e = outBack(lt / 450f); sx = e; sy = e }
            "slideup" -> dy = (1f - outCubic(lt / 500f)) * H * 0.06f
            "slidedown" -> dy = -(1f - outCubic(lt / 500f)) * H * 0.06f
            "typewriter" -> { val reveal = n * lt / max(min(t.durMs * 0.6f, 2600f), 400f); a = if (i < reveal) a else 0f }
            "bounce" -> { val tt = lt - i * 55f; if (tt < 0) a = 0f else { dy = -abs(sin(tt / 110f)) * exp(-tt / 260f) * H * 0.07f; sx = 1f; a = min(a, 1f) } }
            "glitch" -> {
                val fr = (lt / 45f).toInt()
                val r = hash(fr * 7 + i)
                if (lt < 650f || r > 0.9f) { dx = (hash(fr * 13 + i) - 0.5f) * W * 0.05f; split = true; if (r < 0.2f) a = 0f }
            }
            "wave" -> dy = sin(lt / 260f + i * 0.55f) * H * 0.014f
            "zoom" -> { val e = 1.9f - 0.9f * outCubic(lt / 520f); sx = e; sy = e }
            "blurin" -> { blur = (1f - outCubic(lt / 700f)) * 28f; val e = 1.12f - 0.12f * outCubic(lt / 700f); sx = e; sy = e }
            "words" -> { val e = outBack((lt - i * 230f) / 320f); sx = e; sy = e; a = if (lt < i * 230f) 0f else a }
            "pulse" -> { glow = 16f + 12f * sin(lt / 180f); }
            "rotate" -> { val e = outCubic(lt / 650f); rot = (1f - e) * -26f; val sc = 0.6f + 0.4f * e; sx = sc; sy = sc; dy = (1f - e) * H * 0.03f }
            "shake" -> { dx = sin(lt * 0.09f + i) * exp(-lt / 520f) * W * 0.035f + sin(lt * 0.05f) * 1.5f }
            "float" -> dy = sin(lt / 460f + i * 0.2f) * H * 0.013f
            "spin" -> { val e = outCubic(lt / 800f); sx = cos((1f - e) * 6.2832f).let { if (abs(it) < 0.02f) 0.02f else it } }
            "stretch" -> { val e = outCubic(lt / 650f); sx = 1f + (1f - e) * 1.6f; sy = 0.4f + 0.6f * e }
        }
        if (t.sticker) {
            if (t.anim == "pulse") { val e = 1f + 0.1f * sin(lt / 160f); sx = e; sy = e }
            if (t.anim == "spin") rot = lt / 4f
            if (t.anim == "bounce") dy = -abs(sin(lt / 240f)) * H * 0.04f
            if (t.anim == "wave") rot = sin(lt / 300f) * 14f
        }
        if (!t.sticker && t.glow > 0f) glow = maxOf(glow, t.glow * p.textSize * 0.55f)
        if (a <= 0.003f) return
        c.save()
        val px = x + uw / 2 + dx; val py = y + dy
        c.translate(px, py)
        c.rotate(rot)
        c.scale(sx, sy)
        c.translate(-uw / 2, 0f)
        if (!t.sticker) {
            if (t.shadow && glow == 0f && blur == 0f) { p.setShadowLayer(p.textSize * 0.12f, 0f, p.textSize * 0.05f, Color.argb((150 * a).toInt(), 0, 0, 0)) } else p.clearShadowLayer()
            if (glow > 0f) p.setShadowLayer(glow, 0f, 0f, color)
            p.maskFilter = if (blur > 0.5f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
            if (split) {
                p.color = Color.argb((190 * a).toInt(), 255, 40, 80); c.drawText(s, -5f, 0f, p)
                p.color = Color.argb((190 * a).toInt(), 40, 240, 255); c.drawText(s, 5f, 0f, p)
            }
        } else p.clearShadowLayer()
        if (!t.sticker && t.outlineW > 0f) {
            p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = p.textSize * t.outlineW
            p.color = parse(t.outlineColor); p.alpha = (255 * a).toInt().coerceIn(0, 255)
            p.clearShadowLayer()
            c.drawText(s, 0f, 0f, p)
            p.style = Paint.Style.FILL
            if (glow > 0f) p.setShadowLayer(glow, 0f, 0f, color)
            else if (t.shadow && blur == 0f) p.setShadowLayer(p.textSize * 0.12f, 0f, p.textSize * 0.05f, Color.argb((150 * a).toInt(), 0, 0, 0))
        }
        p.color = if (t.sticker) Color.WHITE else color
        p.alpha = (255 * a).toInt().coerceIn(0, 255)
        c.drawText(s, 0f, 0f, p)
        p.maskFilter = null
        c.restore()
    }
}
