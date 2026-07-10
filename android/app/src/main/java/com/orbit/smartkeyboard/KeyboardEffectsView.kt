package com.orbit.smartkeyboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.abs

class KeyboardEffectsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var effectType = "none"
    private val random = Random()
    private val paint = Paint().apply {
        isAntiAlias = true
    }

    private val fireParticles = mutableListOf<FireParticle>()
    private val ripples = mutableListOf<WaterRipple>()
    private val matrixStreams = mutableListOf<MatrixStream>()
    private val galaxyParticles = mutableListOf<GalaxyParticle>()
    private val mechanicalFlashes = mutableListOf<MechanicalFlash>()
    private val trailPoints = mutableListOf<TrailPoint>()

    private val trailPath = Path()
    private val trailPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var lastX = 0f
    private var lastY = 0f

    private var rgbHue = 0f
    private val matrixChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿ"

    // Press state for continuous effects
    private var pressX = 0f
    private var pressY = 0f
    private var pressWidth = 0
    private var pressHeight = 0
    private var isPressed = false
    private var pressEffectActive = false
    private var ambientMode = false

    init {
        isClickable = false
        isFocusable = false
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        return false
    }

    fun setEffectType(type: String) {
        this.effectType = type
        clearAll()
        postInvalidate()
    }

    fun clearAll() {
        fireParticles.clear()
        ripples.clear()
        matrixStreams.clear()
        galaxyParticles.clear()
        mechanicalFlashes.clear()
        trailPoints.clear()
        trailPath.reset()
        isPressed = false
        pressEffectActive = false
    }

    fun setPressed(x: Float, y: Float, width: Int, height: Int) {
        pressX = x
        pressY = y
        pressWidth = width
        pressHeight = height
        isPressed = true
        pressEffectActive = true
        triggerEffect(x, y, width, height)
    }

    fun setReleased() {
        isPressed = false
    }

    fun setAmbient(enable: Boolean, w: Int, h: Int) {
        ambientMode = enable
        if (enable) {
            pressX = w / 2f; pressY = h / 2f; pressWidth = w; pressHeight = h
            isPressed = true; pressEffectActive = true
            if (effectType == "matrix_rain") matrixRainBg(w, h)
            postInvalidateOnAnimation()
        } else {
            clearAll(); postInvalidate()
        }
    }

    private fun matrixRainBg(w: Int, h: Int) {
        matrixStreams.clear()
        for (i in 0 until (w / 10).coerceAtLeast(16)) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h * -1
            val speed = 2f + random.nextFloat() * 4f
            val chars = mutableListOf<String>()
            for (j in 0..14) { chars.add(matrixChars[random.nextInt(matrixChars.length)].toString()) }
            matrixStreams.add(MatrixStream(x, y, speed, chars, 0, 200 + random.nextInt(56)))
        }
    }

    fun clearAmbient() {
        ambientMode = false
        clearAll(); postInvalidate()
    }

    fun triggerEffect(x: Float, y: Float, width: Int, height: Int) {
        when (effectType) {
            "fire" -> { fireParticles.clear(); spawnFire(x, y) }
            "water_ripple" -> { ripples.clear(); spawnRipple(x, y) }
            "matrix_rain" -> { matrixStreams.clear(); spawnMatrix(x, y, width, height) }
            "galaxy" -> { galaxyParticles.clear(); spawnGalaxy(x, y) }
            "mechanical_flash" -> { mechanicalFlashes.clear(); spawnMechanicalFlash(x, y, width, height) }
            "neon_trail" -> spawnNeonTap(x, y)
            "rgb_glow" -> { /* ambient only */ }
        }
        postInvalidateOnAnimation()
    }

    fun addTrailPoint(x: Float, y: Float) {
        if (effectType == "neon_trail") {
            trailPoints.add(TrailPoint(x, y, System.currentTimeMillis()))
            postInvalidateOnAnimation()
        }
    }

    // --- NEON TRAIL ---
    private class TrailPoint(val x: Float, val y: Float, val timestamp: Long)

    private fun spawnNeonTap(x: Float, y: Float) {
        for (i in 0..8) {
            val angle = random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = 3f + random.nextFloat() * 6f
            val life = 300 + random.nextInt(200)
            galaxyParticles.add(
                GalaxyParticle(
                    x, y,
                    (cos(angle) * speed).toFloat(),
                    (sin(angle) * speed).toFloat(),
                    Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 1f, 1f)),
                    4f + random.nextFloat() * 6f,
                    life.toLong()
                )
            )
        }
    }

    // --- FIRE EFFECT ---
    private class FireParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        var size: Float,
        val maxLife: Float,
        var life: Float,
        val wobblePhase: Float
    )

    private fun spawnFire(x: Float, y: Float) {
        for (i in 0..34) {
            val baseVx = -2f + random.nextFloat() * 4f
            val vy = -(4f + random.nextFloat() * 8f)
            val size = 8f + random.nextFloat() * 14f
            val life = 500f + random.nextFloat() * 500f
            val colors = arrayOf("#FF4500", "#FF8C00", "#FFD700", "#FFFF00")
            val colorStr = colors[random.nextInt(colors.size)]
            val color = Color.parseColor(colorStr)
            val spawnY = y + random.nextFloat() * 20f
            fireParticles.add(FireParticle(x, spawnY, baseVx, vy, color, size, life, life, random.nextFloat() * 6f))
        }
    }

    // --- WATER DROPLET ---
    private class WaterRipple(
        var x: Float,
        var y: Float,
        val vx: Float,
        var vy: Float,
        val color: Int,
        var size: Float,
        val maxLife: Float,
        var life: Float,
        var hasSplashed: Boolean
    )

    private fun spawnRipple(x: Float, y: Float) {
        for (i in 0..24) {
            val vx = -1.5f + random.nextFloat() * 3f
            val vy = 2f + random.nextFloat() * 6f
            val size = 3f + random.nextFloat() * 5f
            val life = 700f + random.nextFloat() * 400f
            val colors = arrayOf("#00BFFF", "#1E90FF", "#00CED1", "#87CEEB")
            val colorStr = colors[random.nextInt(colors.size)]
            val color = Color.parseColor(colorStr)
            ripples.add(WaterRipple(x, y, vx, vy, color, size, life, life, false))
        }
    }

    // --- MATRIX RAIN ---
    private class MatrixStream(
        val x: Float,
        var y: Float,
        val speed: Float,
        val chars: List<String>,
        var activeCharIndex: Int,
        var alpha: Int
    )

    private fun spawnMatrix(x: Float, y: Float, keyWidth: Int, keyHeight: Int) {
        for (i in 0..2) {
            val streamX = (x - keyWidth / 2) + random.nextFloat() * keyWidth
            val streamY = y - keyHeight / 2
            val speed = 3f + random.nextFloat() * 5f
            val charsList = mutableListOf<String>()
            for (j in 0..12) {
                charsList.add(matrixChars[random.nextInt(matrixChars.length)].toString())
            }
            matrixStreams.add(MatrixStream(streamX, streamY, speed, charsList, 0, 255))
        }
    }

    // --- GALAXY PARTICLES ---
    private class GalaxyParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        var size: Float,
        val duration: Long,
        val spawnTime: Long = System.currentTimeMillis()
    )

    private fun spawnGalaxy(x: Float, y: Float) {
        for (i in 0..28) {
            val angle = random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = 2f + random.nextFloat() * 6f
            val life = 800 + random.nextInt(400)
            val colors = arrayOf("#00FFFF", "#8A2BE2", "#FF007F", "#FFFFFF")
            val color = Color.parseColor(colors[random.nextInt(colors.size)])
            val size = 5f + random.nextFloat() * 7f
            galaxyParticles.add(GalaxyParticle(x, y, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(), color, size, life.toLong()))
        }
    }

    // --- MECHANICAL FLASH ---
    private class MechanicalFlash(
        val x: Float,
        val y: Float,
        val keyWidth: Int,
        val keyHeight: Int,
        var alpha: Int
    )

    private fun spawnMechanicalFlash(x: Float, y: Float, width: Int, height: Int) {
        mechanicalFlashes.add(MechanicalFlash(x, y, width, height, 255))
    }


    // --- ON DRAW ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var needsRedraw = false

        rgbHue = (rgbHue + 2f) % 360f

        // Handle pressed state: keep effects alive while pressed
        if (isPressed && pressEffectActive) {
            val now = System.currentTimeMillis()

            when (effectType) {
                "fire" -> {
                    // Keep spawning fire particles while pressed
                    if (fireParticles.size < 60) {
                        spawnFire(pressX, pressY)
                    }
                }
                "water_ripple" -> {
                    // Keep spawning water droplets while pressed
                    if (ripples.size < 80) {
                        spawnRipple(pressX, pressY)
                    }
                }
                "matrix_rain" -> {
                    // Keep respawning matrix streams while pressed
                    if (matrixStreams.isEmpty() || matrixStreams.all { it.alpha <= 50 }) {
                        spawnMatrix(pressX, pressY, pressWidth, pressHeight)
                    }
                }
                "galaxy" -> {
                    // Keep spawning galaxy particles while pressed
                    if (galaxyParticles.size < 40) {
                        spawnGalaxy(pressX, pressY)
                    }
                }
                "mechanical_flash" -> {
                    // Keep flash alive while pressed
                    if (mechanicalFlashes.isNotEmpty()) {
                        mechanicalFlashes[0].alpha = 255
                    } else {
                        spawnMechanicalFlash(pressX, pressY, pressWidth, pressHeight)
                    }
                }
            }
        }

        val now = System.currentTimeMillis()

        // 1. Draw Neon Trail
        if (effectType == "neon_trail" && trailPoints.isNotEmpty()) {
            val iterator = trailPoints.iterator()
            while (iterator.hasNext()) {
                val pt = iterator.next()
                val age = now - pt.timestamp
                if (age > 450) {
                    iterator.remove()
                }
            }

            if (trailPoints.size > 1) {
                needsRedraw = true
                for (i in 0 until trailPoints.size - 1) {
                    val p1 = trailPoints[i]
                    val p2 = trailPoints[i + 1]
                    val age = now - p2.timestamp
                    val alpha = ((1f - age.toFloat() / 450f).coerceIn(0f, 1f) * 255).toInt()

                    val segmentHue = (rgbHue + i * 15f) % 360f
                    trailPaint.color = Color.HSVToColor(alpha, floatArrayOf(segmentHue, 1f, 1f))
                    trailPaint.strokeWidth = 14f * (1f - age.toFloat() / 450f).coerceIn(0.2f, 1f)

                    trailPaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, trailPaint)

                    trailPaint.maskFilter = null
                    trailPaint.color = Color.argb(alpha, 255, 255, 255)
                    trailPaint.strokeWidth = 4f
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, trailPaint)
                }
            }
        }

        // 2. Draw Fire particles
        if (fireParticles.isNotEmpty()) {
            needsRedraw = true
            val iterator = fireParticles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.life -= 10f
                if (p.life <= 0) {
                    iterator.remove()
                    continue
                }

                val wobble = kotlin.math.sin(p.wobblePhase + p.life * 0.02f) * 1.5f
                p.x += p.vx + wobble
                p.y += p.vy
                p.size *= 0.97f

                val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
                val alpha = (ratio * 255).toInt().coerceIn(0, 255)

                val flicker = 0.7f + random.nextFloat() * 0.3f
                paint.color = p.color
                paint.alpha = (alpha * flicker).toInt().coerceIn(0, 255)

                paint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawCircle(p.x, p.y, p.size * ratio.coerceAtLeast(0.3f), paint)

                val glowPaint = Paint(paint)
                glowPaint.alpha = (paint.alpha * 0.3f).toInt()
                canvas.drawCircle(p.x, p.y, p.size * 2f, glowPaint)
                paint.maskFilter = null
            }
        }

        // 3. Draw Water Droplets
        if (ripples.isNotEmpty()) {
            needsRedraw = true
            val iterator = ripples.iterator()
            while (iterator.hasNext()) {
                val r = iterator.next()
                r.life -= 10f
                if (r.life <= 0) {
                    if (!r.hasSplashed) {
                        r.hasSplashed = true
                        for (i in 0..4) {
                            val splashX = r.x + random.nextFloat() * 8f - 4f
                            val splashY = r.y + random.nextFloat() * 4f
                            val splashVx = -2f + random.nextFloat() * 4f
                            val splashVy = -3f - random.nextFloat() * 3f
                            ripples.add(WaterRipple(splashX, splashY, splashVx, splashVy, r.color, r.size * 0.4f, 200f, 200f, false))
                        }
                    }
                    iterator.remove()
                    continue
                }

                r.x += r.vx
                r.y += r.vy
                r.vy += 0.3f

                val ratio = r.life / r.maxLife
                val alpha = (ratio * 255).toInt().coerceIn(0, 255)

                paint.color = r.color
                paint.alpha = alpha
                paint.style = Paint.Style.FILL
                paint.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawCircle(r.x, r.y, r.size * (0.5f + ratio * 0.5f), paint)
                paint.maskFilter = null
            }
            paint.style = Paint.Style.FILL
        }

        // 4. Draw Matrix Rain
        if (matrixStreams.isNotEmpty()) {
            needsRedraw = true
            val iterator = matrixStreams.iterator()
            while (iterator.hasNext()) {
                val s = iterator.next()
                s.y += s.speed
                s.alpha -= 1
                if (s.alpha <= 0 || s.y > height) {
                    iterator.remove()
                    continue
                }

                paint.textSize = 32f
                paint.typeface = Typeface.MONOSPACE
                paint.style = Paint.Style.FILL

                for (j in 0 until s.chars.size) {
                    val charY = s.y - (j * 34f)
                    if (charY < 0) continue

                    val itemAlpha = (s.alpha * (1f - j.toFloat() / s.chars.size)).toInt().coerceIn(0, 255)
                    if (j == 0) {
                        paint.color = Color.rgb(200, 255, 200)
                        paint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.OUTER)
                    } else {
                        paint.color = Color.rgb(20, 255, 80)
                        paint.maskFilter = null
                    }
                    paint.alpha = itemAlpha

                    val displayChar = if (random.nextFloat() > 0.95f) {
                        s.chars[random.nextInt(s.chars.size)]
                    } else {
                        s.chars[(s.activeCharIndex + j) % s.chars.size]
                    }

                    canvas.drawText(displayChar, s.x, charY, paint)
                    paint.maskFilter = null
                }
            }
            if (random.nextFloat() > 0.5f) {
                for (s in matrixStreams) {
                    s.activeCharIndex = (s.activeCharIndex + 1) % s.chars.size
                }
            }
        }

        // 5. Draw Galaxy particles
        if (galaxyParticles.isNotEmpty()) {
            needsRedraw = true
            val iterator = galaxyParticles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                val age = now - p.spawnTime
                if (age >= p.duration) {
                    iterator.remove()
                    continue
                }

                val ratio = age.toFloat() / p.duration.toFloat()

                val spiralAngle = ratio * 3f
                val rotX = p.vx * cos(spiralAngle) - p.vy * sin(spiralAngle)
                val rotY = p.vx * sin(spiralAngle) + p.vy * cos(spiralAngle)

                p.x += rotX * 0.15f
                p.y += rotY * 0.15f

                val size = p.size * (1f - ratio * 0.5f)
                val alpha = ((1f - ratio) * 255).toInt().coerceIn(0, 255)

                paint.color = p.color
                paint.alpha = alpha
                paint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawCircle(p.x, p.y, size, paint)
                paint.maskFilter = null
            }
        }

        // 6. Draw Mechanical Flash
        if (mechanicalFlashes.isNotEmpty()) {
            needsRedraw = true
            val iterator = mechanicalFlashes.iterator()
            while (iterator.hasNext()) {
                val f = iterator.next()
                if (!isPressed) {
                    f.alpha -= 4
                }
                if (f.alpha <= 0) {
                    iterator.remove()
                    continue
                }

                val radius = (1f - f.alpha / 255f) * f.keyWidth * 1.5f
                val radialGradient = RadialGradient(
                    f.x, f.y, radius,
                    intArrayOf(Color.argb(f.alpha, 255, 255, 255), Color.argb(f.alpha / 2, 255, 240, 200), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                paint.shader = radialGradient
                canvas.drawCircle(f.x, f.y, radius, paint)
                paint.shader = null
            }
        }

        // 7. Draw RGB ambient glow (per-key color cycle)
        if (ambientMode && effectType == "rgb_glow") {
            needsRedraw = true
            val cols = 10; val rows = 5
            val cellW = width / cols; val cellH = height / rows

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cellHue = (rgbHue + r * 30f + c * 20f) % 360f
                    val cx = c * cellW + cellW / 2f
                    val cy = r * cellH + cellH / 2f
                    val hw = cellW * 0.38f
                    val hh = cellH * 0.38f

                    paint.style = Paint.Style.FILL
                    paint.color = Color.HSVToColor(180, floatArrayOf(cellHue, 0.8f, 1f))
                    paint.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, 6f, 6f, paint)

                    paint.maskFilter = null
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = Color.HSVToColor(140, floatArrayOf(cellHue, 0.6f, 0.9f))
                    canvas.drawRoundRect(cx - hw + 3f, cy - hh + 3f, cx + hw - 3f, cy + hh - 3f, 5f, 5f, paint)
                }
            }
        }

        // Ambient mode: keep spawning effects across full area
        if (ambientMode) {
            needsRedraw = true
            when (effectType) {
                "matrix_rain" -> {
                    if (matrixStreams.size < (width / 8).coerceAtLeast(12)) {
                        matrixRainBg(width, height)
                    }
                }
            }
        }

        if (needsRedraw || isPressed) {
            postInvalidateOnAnimation()
        }
    }
}
