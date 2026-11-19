package com.directorzone.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.Size
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.directorzone.catalog.*
import com.directorzone.data.*
import kotlin.math.*

private fun argb3(c: Long): FloatArray = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)

/** Shared plumbing: one full-screen quad, one fragment shader, per-frame uniforms. */
internal abstract class FxProgram(fragment: String, private val outSize: (Int, Int) -> Size) : BaseGlShaderProgram(false, 1) {
    private val program: GlProgram = try {
        GlProgram(Shaders.VERTEX, fragment)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var firstPts = Long.MIN_VALUE
    protected var outW = 1
    protected var outH = 1
    protected var inW = 1
    protected var inH = 1

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    protected fun GlProgram.u(name: String, vararg v: Float) = setFloatsUniformIfPresent(name, v)

    abstract fun uniforms(p: GlProgram, t: Float)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        inW = inputWidth; inH = inputHeight
        val s = outSize(inputWidth, inputHeight)
        outW = s.width; outH = s.height
        return s
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (firstPts == Long.MIN_VALUE || presentationTimeUs < firstPts) firstPts = presentationTimeUs
            val t = (presentationTimeUs - firstPts) / 1_000_000f
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.u("uRes", outW.toFloat(), outH.toFloat())
            program.u("uTime", t)
            uniforms(program, t)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}

/** Colour grade + filter + Ken Burns. Clip-local time drives grain / VHS jitter. */
class GradeEffect(private val filter: FilterDef, private val amount: Float, private val adj: Adjust, private val kenBurns: Boolean, private val clipSec: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        object : FxProgram(Shaders.GRADE, { w, h -> Size(w, h) }) {
            override fun uniforms(p: GlProgram, t: Float) {
                val f = filter.a; val a = amount
                p.u("uExposure", f.exposure * a + adj.exposure)
                p.u("uContrast", f.contrast * a + adj.contrast)
                p.u("uSat", f.saturation * a + adj.saturation)
                p.u("uWarm", f.warmth * a + adj.warmth)
                p.u("uTint", f.tint * a + adj.tint)
                p.u("uHigh", f.highlights * a + adj.highlights)
                p.u("uShad", f.shadows * a + adj.shadows)
                p.u("uVig", f.vignette * a + adj.vignette)
                p.u("uGrain", f.grain * a + adj.grain)
                p.u("uFade", f.fade * a + adj.fade)
                p.u("uHue", adj.hue * 3.14159f)
                p.u("uGlow", f.glow * a + adj.glow)
                p.u("uChroma", f.chroma * a + adj.chroma)
                p.u("uFx", filter.fx.toFloat())
                p.u("uDuo", filter.duo * a)
                p.setFloatsUniformIfPresent("uDuoA", argb3(filter.duoA))
                p.setFloatsUniformIfPresent("uDuoB", argb3(filter.duoB))
                p.u("uKen", if (kenBurns) 0.09f * (t / max(clipSec, 0.5f)).coerceIn(0f, 1f) else 0f)
            }
        }

    override fun isNoOp(inputWidth: Int, inputHeight: Int) = false
}

/** Plays one half of a transition: [isIn] eases the start of a clip, otherwise the tail. */
class TransitionEffect(private val mode: Int, private val isIn: Boolean, private val durSec: Float, private val clipSec: Float, private val canvas: Size? = null) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        object : FxProgram(Shaders.TRANSITION, { w, h -> Size(w, h) }) {
            override fun uniforms(p: GlProgram, t: Float) {
                val q = if (isIn) 1f - t / durSec else (t - (clipSec - durSec)) / durSec
                p.u("uMode", mode.toFloat())
                p.u("uIn", if (isIn) 1f else 0f)
                p.u("uQ", q.coerceIn(0f, 1f))
            }
        }

    override fun isNoOp(inputWidth: Int, inputHeight: Int) = mode == 0
}

class PhoneEffect(private val cfg: PhoneCfg, private val canvas: Size, private val clipSec: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        object : FxProgram(Shaders.PHONE, { _, _ -> canvas }) {
            val bz = bezelById(cfg.bezel); val bg = bgById(cfg.bg); val anim = phoneAnimById(cfg.anim)
            override fun uniforms(p: GlProgram, t: Float) {
                val pose = PhoneMotion.pose(anim.code, t * cfg.tempo)
                p.u("uRot", pose.yaw, pose.pitch, pose.roll)
                p.u("uPos", pose.x, pose.y, pose.z)
                p.u("uScale", pose.scale * cfg.scale)
                p.u("uVid", inW.toFloat() / inH.toFloat())
                p.setFloatsUniformIfPresent("uFrame", argb3(bz.frame))
                p.setFloatsUniformIfPresent("uBack", argb3(bz.back))
                p.u("uIsland", bz.island.toFloat())
                p.u("uCorner", bz.corner)
                p.u("uBezel", bz.bezel)
                p.u("uBgMode", bg.mode.toFloat())
                p.setFloatsUniformIfPresent("uBg1", argb3(bg.c1))
                p.setFloatsUniformIfPresent("uBg2", argb3(bg.c2))
                p.setFloatsUniformIfPresent("uBg3", argb3(bg.c3))
                p.u("uShadow", if (cfg.shadow) 1f else 0f)
            }
        }

    override fun isNoOp(inputWidth: Int, inputHeight: Int) = false
}

data class Pose(val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f, val x: Float = 0f, val y: Float = 0f, val z: Float = 0f, val scale: Float = 1f)

/** All the premade phone animations as plain functions of time. */
object PhoneMotion {
    private const val TAU = 6.2831855f
    private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    private fun outCubic(x: Float): Float { val c = 1f - x.coerceIn(0f, 1f); return 1f - c * c * c }
    private fun outBack(x: Float): Float { val c = x.coerceIn(0f, 1f); val s = 1.70158f; val k = c - 1f; return 1f + (s + 1f) * k * k * k + s * k * k }
    private fun inOut(x: Float): Float { val c = x.coerceIn(0f, 1f); return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f }

    fun pose(code: Int, t: Float): Pose {
        val bob = 0.022f * sin(t * 1.3f)
        return when (code) {
            0 -> Pose(yaw = 0.2f * sin(t * 0.9f), pitch = 0.1f * sin(t * 0.7f + 1f), roll = 0.02f * sin(t * 0.5f), y = bob)
            1 -> Pose(yaw = 0.7f * sin(t * 0.7f), pitch = 0.12f * sin(t * 0.5f), y = bob * 0.5f)
            2 -> { val u = (t / 4.2f) % 1f; Pose(yaw = TAU * inOut(u * 1.35f), pitch = 0.05f * sin(t), y = bob) }
            3 -> { val u = (t / 4.6f) % 1f; Pose(yaw = 0.18f * sin(t * 0.8f), pitch = TAU * inOut(u * 1.4f), y = bob) }
            4 -> Pose(yaw = 0.38f + 0.1f * sin(t * 1.1f), pitch = -0.24f + 0.06f * sin(t * 0.8f), roll = -0.05f, y = 0.02f * sin(t * 1.5f))
            5 -> { val e = outBack(t / 0.95f); Pose(yaw = (1f - e) * 1.3f + 0.15f * sin(t * 0.8f), pitch = 0.08f * sin(t * 0.6f), scale = (0.25f + 0.75f * e).coerceAtLeast(0.05f), y = bob) }
            6 -> { val e = outCubic(t / 1.1f); Pose(yaw = 0.15f * sin(t * 0.8f), pitch = (1f - e) * 0.7f + 0.06f * sin(t * 0.6f), y = -(1f - e) * 1.3f + bob * e) }
            7 -> { val y = if (t < 2.6f) 1.35f * exp(-2.3f * t) * abs(cos(5.2f * t)) else 0f; Pose(yaw = 0.14f * sin(t * 0.8f), y = y + (if (t >= 2.6f) bob else 0f), roll = 0.04f * sin(t * 1.4f) * exp(-t * 0.6f)) }
            8 -> Pose(yaw = 0.12f * sin(t * 0.6f), pitch = 0.05f * sin(t * 0.5f), scale = 0.78f + 0.4f * ease(t / 6f), y = bob)
            9 -> Pose(yaw = 0.5f * sin(t * 0.45f), pitch = 0.2f * sin(t * 0.4f), roll = 0.28f * sin(t * 0.6f), y = bob)
            10 -> Pose(yaw = -0.72f + 0.08f * sin(t * 0.5f), pitch = -0.58f + 0.04f * sin(t * 0.4f), roll = 0.46f, scale = 0.92f, y = bob * 0.5f)
            11 -> Pose(yaw = 0.32f * sin(t * 2.2f) * exp(-t * 0.28f) + 0.1f * sin(t * 0.7f), roll = 0.07f * sin(t * 3.1f) * exp(-t * 0.35f), y = bob)
            12 -> { val e = outBack(t / 0.75f); Pose(yaw = (1f - outCubic(t / 1.8f)) * TAU * 2f + 0.12f * sin(t * 0.8f), scale = e.coerceAtLeast(0.02f), y = bob * e) }
            13 -> Pose(yaw = 1.05f * sin(t * 0.5f), pitch = 0.05f, x = 0.03f * sin(t * 0.5f), y = bob * 0.4f)
            14 -> Pose()
            else -> Pose(yaw = 0.4f * sin(t * 0.6f), pitch = 0.5f * sin(t * 0.9f), roll = 0.3f * sin(t), y = bob)
        }
    }
}
