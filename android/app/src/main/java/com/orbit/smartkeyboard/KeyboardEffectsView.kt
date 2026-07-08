package com.orbit.smartkeyboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

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
    private val rgbGlows = mutableListOf<RgbGlow>()

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

    // Press state for continuous effects
    private var pressX = 0f
    private var pressY = 0f
    private var pressWidth = 0
    private var pressHeight = 0
    private var isPressed = false
    private var pressEffectActive = false

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
        rgbGlows.clear()
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

    fun triggerEffect(x: Float, y: Float, width: Int, height: Int) {
        when (effectType) {
            "fire" -> { fireParticles.clear(); spawnFire(x, y) }
            "water_ripple" -> { ripples.clear(); spawnRipple(x, y) }
            "matrix_rain" -> { matrixStreams.clear(); spawnMatrix(x, y, width, height) }
            "galaxy" -> { galaxyParticles.clear(); spawnGalaxy(x, y) }
            "mechanical_flash" -> { mechanicalFlashes.clear(); spawnMechanicalFlash(x, y, width, height) }
            "neon_trail" -> spawnNeonTap(x, y)
            "rgb_glow" -> { rgbGlows.clear(); spawnRgbGlow(x, y, width, height) }
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
        val vx: Float,
        val vy: Float,
        val color: Int,
        var size: Float,
        val maxLife: Float,
        var life: Float
    )

    private fun spawnFire(x: Float, y: Float) {
        for (i in 0..22) {
            val vx = -4f + random.nextFloat() * 8f
            val vy = -3f - random.nextFloat() * 6f
            val size = 10f + random.nextFloat() * 16f
            val life = 600f + random.nextFloat() * 400f
            val colors = arrayOf("#FFD700", "#FF8C00", "#FF4500", "#FF0000")
            val colorStr = colors[random.nextInt(colors.size)]
            val color = Color.parseColor(colorStr)
            fireParticles.add(FireParticle(x, y, vx, vy, color, size, life, life))
        }
    }

    // --- WATER RIPPLE ---
    private class WaterRipple(
        val x: Float,
        val y: Float,
        var radius: Float,
        val maxRadius: Float,
        val color: Int,
        val duration: Float,
        var progress: Float
    )

    private fun spawnRipple(x: Float, y: Float) {
        val color = Color.argb(180, 79, 140, 255)
        ripples.add(WaterRipple(x, y, 0f, 220f, color, 800f, 0f))
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
        val matrixChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿ"
        for (i in 0..2) {
            val streamX = (x - keyWidth / 2) + random.nextFloat() * keyWidth
            val streamY = y - keyHeight / 2
            val speed = 8f + random.nextFloat() * 12f
            val charsList = mutableListOf<String>()
            for (j in 0..15) {
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

    // --- RGB GLOW ---
    private class RgbGlow(
        val x: Float,
        val y: Float,
        val keyWidth: Int,
        val keyHeight: Int,
        var scale: Float,
        var alpha: Int
    )

    private fun spawnRgbGlow(x: Float, y: Float, width: Int, height: Int) {
        rgbGlows.add(RgbGlow(x, y, width, height, 1.0f, 255))
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
                    if (fireParticles.size < 30) {
                        spawnFire(pressX, pressY)
                    }
                }
                "water_ripple" -> {
                    // Keep ripple alive while pressed - reset progress
                    if (ripples.isNotEmpty()) {
                        val r = ripples[0]
                        r.progress = 0f
                        r.radius = 0f
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
                "rgb_glow" -> {
                    // Keep glow bright while pressed
                    if (rgbGlows.isNotEmpty()) {
                        val g = rgbGlows[0]
                        g.alpha = 255
                        g.scale = 1.0f
                    } else {
                        spawnRgbGlow(pressX, pressY, pressWidth, pressHeight)
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

                p.x += p.vx
                p.y += p.vy
                p.size *= 0.95f

                val ratio = p.life / p.maxLife
                val alpha = (ratio * 255).toInt().coerceIn(0, 255)
                paint.color = p.color
                paint.alpha = alpha

                paint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawCircle(p.x, p.y, p.size, paint)
                paint.maskFilter = null
            }
        }

        // 3. Draw Water Ripples
        if (ripples.isNotEmpty()) {
            needsRedraw = true
            val iterator = ripples.iterator()
            while (iterator.hasNext()) {
                val r = iterator.next()
                r.progress += 16f
                if (r.progress >= r.duration) {
                    iterator.remove()
                    continue
                }

                val ratio = r.progress / r.duration
                r.radius = r.maxRadius * ratio
                val alpha = ((1f - ratio) * 255).toInt().coerceIn(0, 255)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f * (1f - ratio)
                paint.color = r.color
                paint.alpha = alpha
                canvas.drawCircle(r.x, r.y, r.radius, paint)

                if (ratio > 0.3f) {
                    val ratio2 = (r.progress - r.duration * 0.3f) / (r.duration * 0.7f)
                    val r2 = r.maxRadius * 0.7f * ratio2
                    val alpha2 = ((1f - ratio2) * 180).toInt().coerceIn(0, 255)
                    paint.strokeWidth = 4f * (1f - ratio2)
                    paint.alpha = alpha2
                    canvas.drawCircle(r.x, r.y, r2, paint)
                }

                paint.style = Paint.Style.FILL
            }
        }

        // 4. Draw Matrix Rain
        if (matrixStreams.isNotEmpty()) {
            needsRedraw = true
            val iterator = matrixStreams.iterator()
            while (iterator.hasNext()) {
                val s = iterator.next()
                s.y += s.speed
                s.alpha -= 2
                if (s.alpha <= 0 || s.y > height) {
                    iterator.remove()
                    continue
                }

                paint.textSize = 28f
                paint.typeface = Typeface.MONOSPACE
                paint.style = Paint.Style.FILL

                for (j in 0 until s.chars.size) {
                    val charY = s.y - (j * 32f)
                    if (charY < 0) continue

                    val itemAlpha = (s.alpha * (1f - j.toFloat() / s.chars.size)).toInt().coerceIn(0, 255)
                    if (j == 0) {
                        paint.color = Color.rgb(180, 255, 180)
                    } else {
                        paint.color = Color.rgb(0, 255, 70)
                    }
                    paint.alpha = itemAlpha

                    val displayChar = if (random.nextFloat() > 0.95f) {
                        s.chars[random.nextInt(s.chars.size)]
                    } else {
                        s.chars[(s.activeCharIndex + j) % s.chars.size]
                    }

                    canvas.drawText(displayChar, s.x, charY, paint)
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

        // 7. Draw RGB Glow around pressed keys
        if (rgbGlows.isNotEmpty()) {
            needsRedraw = true
            val iterator = rgbGlows.iterator()
            while (iterator.hasNext()) {
                val g = iterator.next()
                if (!isPressed) {
                    g.scale += 0.03f
                    g.alpha -= 4
                }
                if (g.alpha <= 0) {
                    iterator.remove()
                    continue
                }

                val glowHue = (rgbHue + g.alpha / 5f) % 360f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = Color.HSVToColor(g.alpha, floatArrayOf(glowHue, 1f, 1f))
                paint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.OUTER)

                val rectWidth = g.keyWidth * g.scale
                val rectHeight = g.keyHeight * g.scale
                val rect = RectF(
                    g.x - rectWidth / 2,
                    g.y - rectHeight / 2,
                    g.x + rectWidth / 2,
                    g.y + rectHeight / 2
                )
                canvas.drawRoundRect(rect, 14f, 14f, paint)

                paint.style = Paint.Style.FILL
                paint.maskFilter = null
            }
        }

        if (needsRedraw || isPressed) {
            postInvalidateOnAnimation()
        }
    }
}
