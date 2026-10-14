package com.angmarx.bounceblade

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        gameView = GameView(this)
        setContentView(gameView)
    }

    @Deprecated("Deprecated by Android; retained for API 23 compatibility")
    override fun onBackPressed() {
        if (!gameView.handleBack()) super.onBackPressed()
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.pauseIfPlaying()
        super.onPause()
    }
}

private data class PointN(val x: Float, val y: Float)
private data class EnemyState(val x: Float, val y: Float, var alive: Boolean = true, val kind: Int = 0)
private data class CrateState(val x: Float, val y: Float, var alive: Boolean = true)
private data class HazardState(
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
private data class GameLevel(
    val id: Int,
    val name: String,
    val maxBounces: Int,
    val spawn: PointN,
    val exit: PointN,
    val switch: PointN,
    val enemies: List<PointN>,
    val crates: List<PointN>,
    val hazards: List<HazardState>
)
private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Int,
    val radius: Float
)
private enum class GameScreen { MENU, LEVELS, PLAYING, PAUSED, SETTINGS, WON, FAILED, CAMPAIGN_COMPLETE }

private class GameView(context: Context) : View(context) {
    private val prefs = context.getSharedPreferences("bounce_blade_save", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val minDim get() = min(viewW, viewH)
    private var viewW = 1f
    private var viewH = 1f
    private val cyan = Color.rgb(45, 235, 255)
    private val pink = Color.rgb(255, 65, 150)
    private val gold = Color.rgb(255, 199, 83)
    private val dark = Color.rgb(7, 10, 22)
    private val muted = Color.rgb(94, 120, 150)
    private val grid = Color.rgb(22, 35, 58)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 28)
    private val stars = (0 until 54).map { i ->
        PointN(((i * 73 + 19) % 997) / 997f, ((i * 151 + 41) % 991) / 991f)
    }
    private val levels: List<GameLevel> = loadLevels(context)
    private var screen = GameScreen.MENU
    private var levelId = 1
    private var level = levels.first()
    private var enemies = mutableListOf<EnemyState>()
    private var crates = mutableListOf<CrateState>()
    private var bladeX = 0.14f
    private var bladeY = 0.80f
    private var velocityX = 0f
    private var velocityY = 0f
    private var angle = 0f
    private var flying = false
    private var aiming = false
    private var slowMotion = false
    private var switchActivated = false
    private var bouncesLeft = 5
    private var slowMeter = 1f
    private var aimStartX = 0f
    private var aimStartY = 0f
    private var aimX = 0f
    private var aimY = 0f
    private var lastFrame = System.nanoTime()
    private var flightTime = 0f
    private var soundEnabled = prefs.getBoolean("sound_enabled", true)
    private var vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
    private var message = ""
    private var messageUntil = 0L
    private val particles = mutableListOf<Particle>()

    init {
        loadLevel(1)
        screen = GameScreen.MENU
        isFocusable = true
        isClickable = true
    }

    fun handleBack(): Boolean {
        when (screen) {
            GameScreen.PLAYING -> screen = GameScreen.PAUSED
            GameScreen.PAUSED, GameScreen.SETTINGS -> screen = GameScreen.MENU
            GameScreen.LEVELS -> screen = GameScreen.MENU
            GameScreen.WON, GameScreen.FAILED, GameScreen.CAMPAIGN_COMPLETE -> screen = GameScreen.LEVELS
            GameScreen.MENU -> return false
        }
        slowMotion = false
        aiming = false
        lastFrame = System.nanoTime()
        invalidate()
        return true
    }

    fun pauseIfPlaying() {
        if (screen == GameScreen.PLAYING) {
            screen = GameScreen.PAUSED
            slowMotion = false
            aiming = false
            lastFrame = System.nanoTime()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        tone.release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        viewW = width.toFloat().coerceAtLeast(1f)
        viewH = height.toFloat().coerceAtLeast(1f)
        advanceFrame()
        drawBackground(canvas)
        when (screen) {
            GameScreen.MENU -> drawMenu(canvas)
            GameScreen.LEVELS -> drawLevelSelect(canvas)
            GameScreen.PLAYING -> drawGame(canvas)
            GameScreen.PAUSED -> {
                drawGame(canvas)
                drawOverlay(canvas, "PAUSED", "Take a breath. The blade can wait.")
                drawButton(canvas, 0.24f, 0.43f, 0.76f, 0.51f, "RESUME", cyan)
                drawButton(canvas, 0.24f, 0.54f, 0.76f, 0.62f, "RESTART LEVEL", gold)
                drawButton(canvas, 0.24f, 0.65f, 0.76f, 0.73f, "MAIN MENU", pink)
            }
            GameScreen.SETTINGS -> drawSettings(canvas)
            GameScreen.WON -> {
                drawGame(canvas)
                drawOverlay(canvas, "LEVEL CLEARED", "Nice shot! The exit is open.")
                drawButton(canvas, 0.22f, 0.51f, 0.78f, 0.60f, "NEXT LEVEL", cyan)
                drawButton(canvas, 0.22f, 0.63f, 0.78f, 0.71f, "LEVEL SELECT", gold)
            }
            GameScreen.FAILED -> {
                drawGame(canvas)
                drawOverlay(canvas, "TRY AGAIN", "The arena won this round. Reset and try a new angle.")
                drawButton(canvas, 0.22f, 0.51f, 0.78f, 0.60f, "RETRY", cyan)
                drawButton(canvas, 0.22f, 0.63f, 0.78f, 0.71f, "LEVEL SELECT", gold)
            }
            GameScreen.CAMPAIGN_COMPLETE -> {
                drawMenuBackdrop(canvas)
                drawText(canvas, "CAMPAIGN", 0.5f, 0.31f, 0.075f, cyan, Paint.Align.CENTER)
                drawText(canvas, "COMPLETE", 0.5f, 0.40f, 0.075f, gold, Paint.Align.CENTER)
                drawText(canvas, "You cleared every included level.", 0.5f, 0.46f, 0.024f, Color.WHITE, Paint.Align.CENTER)
                drawButton(canvas, 0.20f, 0.57f, 0.80f, 0.66f, "PLAY AGAIN", cyan)
                drawButton(canvas, 0.20f, 0.69f, 0.80f, 0.78f, "LEVEL SELECT", gold)
            }
        }
        if (flying || particles.isNotEmpty()) postInvalidateDelayed(16L)
    }

    private fun advanceFrame() {
        val now = System.nanoTime()
        val rawDt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.025f)
        lastFrame = now
        if (screen == GameScreen.PLAYING && flying) {
            val timeScale = if (slowMotion && slowMeter > 0f) 0.28f else 1f
            val dt = rawDt * timeScale
            if (slowMotion && slowMeter > 0f) {
                slowMeter = (slowMeter - rawDt * 0.20f).coerceAtLeast(0f)
            } else {
                slowMeter = (slowMeter + rawDt * 0.075f).coerceAtMost(1f)
            }
            flightTime += dt
            angle = (angle + 1080f * dt) % 360f
            val substeps = 3
            val step = dt / substeps
            repeat(substeps) {
                if (screen == GameScreen.PLAYING && flying) simulateStep(step)
            }
            if (flying && (sqrt(velocityX * velocityX + velocityY * velocityY) < minDim * 0.055f || flightTime > 12f)) {
                flying = false
                velocityX = 0f
                velocityY = 0f
            }
        } else {
            if (!slowMotion) slowMeter = (slowMeter + rawDt * 0.04f).coerceAtMost(1f)
            if (screen != GameScreen.PLAYING) slowMotion = false
        }
        val particleDt = rawDt
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.life -= particleDt
            particle.x += particle.vx * particleDt
            particle.y += particle.vy * particleDt
            particle.vx *= 0.985f
            particle.vy *= 0.985f
            if (particle.life <= 0f) iterator.remove()
        }
    }

    private fun simulateStep(dt: Float) {
        val radius = minDim * 0.022f
        val left = viewW * 0.075f + radius
        val right = viewW * 0.925f - radius
        val top = viewH * 0.145f + radius
        val bottom = viewH * 0.925f - radius
        var px = bladeX * viewW + velocityX * dt
        var py = bladeY * viewH + velocityY * dt

        for (hazard in level.hazards) {
            val hx = hazard.x * viewW
            val hy = hazard.y * viewH
            val hw = hazard.width * viewW
            val hh = max(hazard.height * viewH, minDim * 0.018f)
            if (px + radius > hx - hw / 2f && px - radius < hx + hw / 2f &&
                py + radius > hy - hh / 2f && py - radius < hy + hh / 2f
            ) {
                fail("You hit a hazard.")
                return
            }
        }

        var bounced = false
        if (px < left) {
            px = left
            velocityX = abs(velocityX)
            bounced = true
        } else if (px > right) {
            px = right
            velocityX = -abs(velocityX)
            bounced = true
        }
        if (py < top) {
            py = top
            velocityY = abs(velocityY)
            bounced = true
        } else if (py > bottom) {
            py = bottom
            velocityY = -abs(velocityY)
            bounced = true
        }
        bladeX = px / viewW
        bladeY = py / viewH
        if (bounced) {
            useBounce()
            addBurst(px, py, cyan, 8)
            playTone(ToneGenerator.TONE_PROP_BEEP)
            if (screen != GameScreen.PLAYING) return
        }

        for (enemy in enemies) {
            if (!enemy.alive) continue
            val ex = enemy.x * viewW
            val ey = enemy.y * viewH
            val dx = px - ex
            val dy = py - ey
            val hitRadius = radius + minDim * 0.027f
            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                enemy.alive = false
                reflectFrom(dx, dy)
                useBounce()
                addBurst(ex, ey, pink, 14)
                playTone(ToneGenerator.TONE_PROP_ACK)
                haptic()
                if (screen != GameScreen.PLAYING) return
            }
        }

        for (crate in crates) {
            if (!crate.alive) continue
            val cx = crate.x * viewW
            val cy = crate.y * viewH
            val half = minDim * 0.035f
            if (abs(px - cx) < half + radius && abs(py - cy) < half + radius) {
                crate.alive = false
                if (abs(px - cx) > abs(py - cy)) velocityX = -velocityX else velocityY = -velocityY
                useBounce()
                addBurst(cx, cy, gold, 12)
                playTone(ToneGenerator.TONE_PROP_ACK)
                haptic()
                if (screen != GameScreen.PLAYING) return
            }
        }

        val sx = level.switch.x * viewW
        val sy = level.switch.y * viewH
        val switchRadius = minDim * 0.032f
        if (!switchActivated && distanceSquared(px, py, sx, sy) < (radius + switchRadius) * (radius + switchRadius)) {
            switchActivated = true
            addBurst(sx, sy, gold, 18)
            showMessage("SWITCH ACTIVATED")
            playTone(ToneGenerator.TONE_PROP_ACK)
            haptic()
        }

        val exitReady = switchActivated && enemies.none { it.alive } && crates.none { it.alive }
        val ex = level.exit.x * viewW
        val ey = level.exit.y * viewH
        val exitRadius = minDim * 0.034f
        if (distanceSquared(px, py, ex, ey) < (radius + exitRadius) * (radius + exitRadius)) {
            if (exitReady) {
                completeLevel()
                return
            } else {
                val dx = px - ex
                val dy = py - ey
                if (velocityX * dx + velocityY * dy < 0f) reflectFrom(dx, dy)
                showMessage("EXIT LOCKED: CLEAR TARGETS AND HIT SWITCH")
            }
        }
    }

    private fun reflectFrom(dx: Float, dy: Float) {
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.001f) {
            velocityX = -velocityX
            velocityY = -velocityY
            return
        }
        val nx = dx / length
        val ny = dy / length
        val dot = velocityX * nx + velocityY * ny
        velocityX -= 2f * dot * nx
        velocityY -= 2f * dot * ny
    }

    private fun useBounce() {
        bouncesLeft -= 1
        if (bouncesLeft < 0 && screen == GameScreen.PLAYING) fail("Out of bounces.")
    }

    private fun completeLevel() {
        flying = false
        aiming = false
        velocityX = 0f
        velocityY = 0f
        val nextId = levelId + 1
        val currentUnlocked = prefs.getInt("unlocked_level", 1)
        val newUnlocked = max(currentUnlocked, min(nextId, levels.size))
        prefs.edit().putInt("unlocked_level", newUnlocked).apply()
        screen = if (levelId >= levels.size) GameScreen.CAMPAIGN_COMPLETE else GameScreen.WON
        playTone(ToneGenerator.TONE_PROP_ACK)
        haptic()
    }

    private fun fail(reason: String) {
        if (screen != GameScreen.PLAYING) return
        flying = false
        aiming = false
        velocityX = 0f
        velocityY = 0f
        message = reason
        screen = GameScreen.FAILED
        playTone(ToneGenerator.TONE_PROP_NACK)
        haptic()
    }

    private fun loadLevel(id: Int) {
        levelId = id.coerceIn(1, levels.size)
        level = levels.firstOrNull { it.id == levelId } ?: levels.first()
        enemies = level.enemies.mapIndexed { index, point ->
            EnemyState(point.x, point.y, true, index % 3)
        }.toMutableList()
        crates = level.crates.map { CrateState(it.x, it.y) }.toMutableList()
        bladeX = level.spawn.x
        bladeY = level.spawn.y
        velocityX = 0f
        velocityY = 0f
        angle = 0f
        flying = false
        aiming = false
        slowMotion = false
        switchActivated = false
        bouncesLeft = level.maxBounces
        slowMeter = 1f
        flightTime = 0f
        particles.clear()
        message = ""
        lastFrame = System.nanoTime()
    }

    private fun startLevel(id: Int) {
        val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
        if (id > unlocked) {
            showMessage("CLEAR PREVIOUS LEVELS TO UNLOCK THIS")
            return
        }
        loadLevel(id)
        screen = GameScreen.PLAYING
        invalidate()
    }

    private fun readLevel(json: String): GameLevel {
        val root = JSONObject(json)
        val enemiesJson = root.optJSONArray("enemies")
        val cratesJson = root.optJSONArray("crates")
        val hazardsJson = root.optJSONArray("hazards")
        return GameLevel(
            id = root.optInt("id", 1),
            name = root.optString("name", "Arena"),
            maxBounces = root.optInt("maxBounces", 5).coerceAtLeast(1),
            spawn = readPoint(root.optJSONObject("playerSpawn"), PointN(0.14f, 0.80f)),
            exit = readPoint(root.optJSONObject("exit"), PointN(0.88f, 0.18f)),
            switch = readPoint(root.optJSONObject("switch"), PointN(0.78f, 0.76f)),
            enemies = readPoints(enemiesJson),
            crates = readPoints(cratesJson),
            hazards = readHazards(hazardsJson)
        )
    }

    private fun readPoint(obj: JSONObject?, fallback: PointN): PointN {
        if (obj == null) return fallback
        return PointN(
            obj.optDouble("x", fallback.x.toDouble()).toFloat().coerceIn(0.06f, 0.94f),
            obj.optDouble("y", fallback.y.toDouble()).toFloat().coerceIn(0.15f, 0.94f)
        )
    }

    private fun readPoints(array: JSONArray?): List<PointN> {
        if (array == null) return emptyList()
        val result = mutableListOf<PointN>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                PointN(
                    item.optDouble("x", 0.5).toFloat().coerceIn(0.08f, 0.92f),
                    item.optDouble("y", 0.5).toFloat().coerceIn(0.17f, 0.86f)
                )
            )
        }
        return result
    }

    private fun readHazards(array: JSONArray?): List<HazardState> {
        if (array == null) return emptyList()
        val result = mutableListOf<HazardState>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                HazardState(
                    item.optString("type", "spikes").lowercase(),
                    item.optDouble("x", 0.5).toFloat().coerceIn(0.1f, 0.9f),
                    item.optDouble("y", 0.9).toFloat().coerceIn(0.2f, 0.94f),
                    item.optDouble("width", 0.56).toFloat().coerceIn(0.05f, 0.9f),
                    item.optDouble("height", 0.025).toFloat().coerceIn(0.01f, 0.12f)
                )
            )
        }
        return result
    }

    private fun loadLevels(ctx: Context): List<GameLevel> {
        val loaded = mutableListOf<GameLevel>()
        try {
            val names = ctx.assets.list("levels").orEmpty()
                .filter { it.matches(Regex("level_[0-9]{3}\\.json")) }
                .sorted()
            for (name in names) {
                try {
                    val text = ctx.assets.open("levels/" + name)
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    loaded.add(readLevel(text))
                } catch (_: Exception) {
                    // One malformed user-edited level should not stop the game from starting.
                }
            }
        } catch (_: Exception) {
            // Fall through to the built-in safe levels.
        }
        val byId = loaded.associateBy { it.id }
        return (1..20).map { id -> byId[id] ?: fallbackLevel(id) }
    }

    private fun fallbackLevel(id: Int): GameLevel {
        val enemyPositions = listOf(
            PointN(0.31f, 0.28f), PointN(0.65f, 0.27f), PointN(0.72f, 0.48f),
            PointN(0.31f, 0.52f), PointN(0.51f, 0.36f), PointN(0.65f, 0.64f),
            PointN(0.42f, 0.70f), PointN(0.78f, 0.37f)
        )
        val crates = listOf(PointN(0.45f, 0.55f), PointN(0.60f, 0.43f), PointN(0.34f, 0.66f))
        val count = (2 + id / 3).coerceAtMost(enemyPositions.size)
        return GameLevel(
            id, "Arena " + id, 5 + (id - 1) / 4,
            PointN(0.14f, 0.80f),
            PointN(0.87f, 0.18f),
            if (id % 2 == 0) PointN(0.78f, 0.75f) else PointN(0.21f, 0.75f),
            enemyPositions.take(count),
            crates.take(if (id % 3 == 0) 3 else 2),
            listOf(HazardState("spikes", 0.5f, 0.90f, 0.56f, 0.025f))
        )
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(dark)
        paint.style = Paint.Style.FILL
        for (star in stars) {
            paint.color = if ((star.x * 1000f).toInt() % 3 == 0) cyan else muted
            canvas.drawCircle(star.x * viewW, star.y * viewH, density * 1.1f, paint)
        }
        paint.color = grid
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, density * 0.6f)
        for (i in 1..5) {
            val x = viewW * i / 6f
            canvas.drawLine(x, viewH * 0.02f, x, viewH * 0.98f, paint)
        }
        for (i in 1..12) {
            val y = viewH * i / 13f
            canvas.drawLine(viewW * 0.02f, y, viewW * 0.98f, y, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawMenuBackdrop(canvas: Canvas) {
        drawBackground(canvas)
        val cx = viewW * 0.5f
        val cy = viewH * 0.21f
        val r = minDim * 0.09f
        paint.color = cyan
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2f)
        paint.setShadowLayer(density * 22f, 0f, 0f, cyan)
        canvas.drawCircle(cx, cy, r, paint)
        paint.clearShadowLayer()
        canvas.save()
        canvas.rotate(-35f, cx, cy)
        drawBladeAt(canvas, cx, cy, r * 1.45f)
        canvas.restore()
        paint.style = Paint.Style.FILL
    }

    private fun drawMenu(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "BOUNCE", 0.5f, 0.35f, 0.095f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "BLADE", 0.5f, 0.43f, 0.095f, cyan, Paint.Align.CENTER)
        drawText(canvas, "AIM  •  RICOCHET  •  ESCAPE", 0.5f, 0.475f, 0.022f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.18f, 0.53f, 0.82f, 0.615f, "PLAY CAMPAIGN", cyan)
        drawButton(canvas, 0.18f, 0.635f, 0.82f, 0.715f, "LEVEL SELECT", gold)
        drawButton(canvas, 0.18f, 0.735f, 0.82f, 0.815f, "SETTINGS", pink)
        drawText(canvas, "20 HANDMADE PUZZLE ARENAS", 0.5f, 0.88f, 0.018f, muted, Paint.Align.CENTER)
        drawText(canvas, "DRAG BACK TO LAUNCH  •  TWO FINGERS TO SLOW TIME", 0.5f, 0.925f, 0.014f, muted, Paint.Align.CENTER)
    }

    private fun drawLevelSelect(canvas: Canvas) {
        drawText(canvas, "SELECT ARENA", 0.5f, 0.13f, 0.062f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "Unlocked: " + prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size) + " / " + levels.size,
            0.5f, 0.175f, 0.024f, cyan, Paint.Align.CENTER)
        val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
        for (id in 1..levels.size) {
            val column = (id - 1) % 4
            val row = (id - 1) / 4
            val left = 0.055f + column * 0.23f
            val top = 0.225f + row * 0.113f
            val available = id <= unlocked
            val col = if (available) cyan else muted
            drawButton(canvas, left, top, left + 0.19f, top + 0.078f,
                if (available) id.toString().padStart(2, '0') else "LOCKED", col, available)
        }
        drawButton(canvas, 0.22f, 0.84f, 0.78f, 0.91f, "BACK", gold)
    }

    private fun drawGame(canvas: Canvas) {
        drawText(canvas, "BOUNCE BLADE", 0.07f, 0.047f, 0.028f, Color.WHITE)
        drawText(canvas, "ARENA " + levelId + " / " + levels.size, 0.5f, 0.047f, 0.024f, cyan, Paint.Align.CENTER)
        drawText(canvas, "BOUNCES " + bouncesLeft, 0.72f, 0.047f, 0.022f, gold, Paint.Align.RIGHT)
        drawButton(canvas, 0.81f, 0.065f, 0.94f, 0.115f, "Ⅱ", pink)
        val rect = RectF(viewW * 0.05f, viewH * 0.13f, viewW * 0.95f, viewH * 0.94f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2f)
        paint.color = cyan
        paint.setShadowLayer(density * 12f, 0f, 0f, cyan)
        canvas.drawRoundRect(rect, density * 16f, density * 16f, paint)
        paint.clearShadowLayer()
        paint.color = grid
        paint.strokeWidth = max(1f, density * 0.7f)
        for (i in 1..4) canvas.drawLine(viewW * (0.05f + 0.18f * i), viewH * 0.14f, viewW * (0.05f + 0.18f * i), viewH * 0.93f, paint)
        for (i in 1..8) canvas.drawLine(viewW * 0.055f, viewH * (0.14f + 0.087f * i), viewW * 0.945f, viewH * (0.14f + 0.087f * i), paint)

        drawHazards(canvas)
        drawSwitch(canvas)
        drawExit(canvas)
        for (crate in crates) if (crate.alive) drawCrate(canvas, crate)
        for (enemy in enemies) if (enemy.alive) drawEnemy(canvas, enemy)
        drawParticles(canvas)
        if (aiming && !flying) drawAim(canvas)
        val bladePx = bladeX * viewW
        val bladePy = bladeY * viewH
        canvas.save()
        canvas.rotate(angle, bladePx, bladePy)
        drawBladeAt(canvas, bladePx, bladePy, minDim * 0.033f)
        canvas.restore()

        val targetsLeft = enemies.count { it.alive } + crates.count { it.alive }
        val objective = when {
            targetsLeft > 0 -> "TARGETS LEFT: " + targetsLeft
            !switchActivated -> "HIT THE GOLD SWITCH"
            else -> "EXIT OPEN  •  REACH THE PORTAL"
        }
        drawText(canvas, objective, 0.5f, 0.112f, 0.019f,
            if (switchActivated && targetsLeft == 0) cyan else muted, Paint.Align.CENTER)
        drawMeter(canvas)
        if (System.nanoTime() < messageUntil && message.isNotBlank()) {
            drawText(canvas, message, 0.5f, 0.855f, 0.019f, gold, Paint.Align.CENTER)
        }
    }

    private fun drawHazards(canvas: Canvas) {
        for (hazard in level.hazards) {
            val cx = hazard.x * viewW
            val cy = hazard.y * viewH
            val halfWidth = hazard.width * viewW / 2f
            val h = max(hazard.height * viewH, minDim * 0.018f)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(80, 22, 51)
            canvas.drawRoundRect(cx - halfWidth, cy - h / 2f, cx + halfWidth, cy + h / 2f, density * 4f, density * 4f, paint)
            if (hazard.type.contains("spike")) {
                paint.color = pink
                val count = max(4, (hazard.width * 22f).toInt())
                for (i in 0 until count) {
                    val x = cx - halfWidth + (2f * halfWidth) * i / count
                    val nextX = min(cx + halfWidth, x + max(density * 6f, 2f * halfWidth / count))
                    val path = Path()
                    path.moveTo(x, cy + h / 2f)
                    path.lineTo((x + nextX) / 2f, cy - h / 2f)
                    path.lineTo(nextX, cy + h / 2f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            } else {
                paint.color = pink
                paint.strokeWidth = density * 3f
                canvas.drawLine(cx - halfWidth, cy, cx + halfWidth, cy, paint)
            }
        }
    }

    private fun drawSwitch(canvas: Canvas) {
        val x = level.switch.x * viewW
        val y = level.switch.y * viewH
        val radius = minDim * 0.031f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2f)
        paint.color = if (switchActivated) cyan else gold
        paint.setShadowLayer(density * 10f, 0f, 0f, paint.color)
        canvas.drawCircle(x, y, radius, paint)
        canvas.drawCircle(x, y, radius * 0.56f, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        paint.color = if (switchActivated) cyan else gold
        canvas.drawCircle(x, y, radius * 0.18f, paint)
        drawText(canvas, if (switchActivated) "ON" else "SWITCH", x / viewW, (y + radius + density * 15f) / viewH,
            0.015f, paint.color, Paint.Align.CENTER)
    }

    private fun drawExit(canvas: Canvas) {
        val x = level.exit.x * viewW
        val y = level.exit.y * viewH
        val ready = switchActivated && enemies.none { it.alive } && crates.none { it.alive }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 3f)
        paint.color = if (ready) cyan else muted
        paint.setShadowLayer(if (ready) density * 15f else density * 3f, 0f, 0f, paint.color)
        canvas.drawOval(x - minDim * 0.038f, y - minDim * 0.052f, x + minDim * 0.038f, y + minDim * 0.052f, paint)
        canvas.drawOval(x - minDim * 0.020f, y - minDim * 0.036f, x + minDim * 0.020f, y + minDim * 0.036f, paint)
        paint.clearShadowLayer()
        drawText(canvas, if (ready) "EXIT" else "LOCKED", x / viewW, (y + minDim * 0.065f) / viewH, 0.014f,
            if (ready) cyan else muted, Paint.Align.CENTER)
        paint.style = Paint.Style.FILL
    }

    private fun drawEnemy(canvas: Canvas, enemy: EnemyState) {
        val x = enemy.x * viewW
        val y = enemy.y * viewH
        val r = minDim * (if (enemy.kind == 1) 0.031f else 0.026f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2f)
        paint.color = pink
        paint.setShadowLayer(density * 10f, 0f, 0f, pink)
        canvas.drawCircle(x, y, r, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        paint.color = pink
        canvas.drawCircle(x - r * 0.32f, y - r * 0.12f, max(2f, r * 0.14f), paint)
        canvas.drawCircle(x + r * 0.32f, y - r * 0.12f, max(2f, r * 0.14f), paint)
        paint.strokeWidth = max(1.5f, density * 1.5f)
        paint.style = Paint.Style.STROKE
        canvas.drawArc(x - r * 0.42f, y + r * 0.05f, x + r * 0.42f, y + r * 0.6f, 15f, 150f, false, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCrate(canvas: Canvas, crate: CrateState) {
        val x = crate.x * viewW
        val y = crate.y * viewH
        val r = minDim * 0.034f
        val rect = RectF(x - r, y - r, x + r, y + r)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(48, 37, 25)
        canvas.drawRoundRect(rect, density * 3f, density * 3f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2f)
        paint.color = gold
        canvas.drawRoundRect(rect, density * 3f, density * 3f, paint)
        canvas.drawLine(rect.left + r * 0.25f, rect.top + r * 0.25f, rect.right - r * 0.25f, rect.bottom - r * 0.25f, paint)
        canvas.drawLine(rect.right - r * 0.25f, rect.top + r * 0.25f, rect.left + r * 0.25f, rect.bottom - r * 0.25f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBladeAt(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val path = Path()
        path.moveTo(x - radius * 0.72f, y + radius * 0.63f)
        path.lineTo(x + radius * 0.88f, y - radius * 0.88f)
        path.lineTo(x + radius * 0.40f, y + radius * 0.05f)
        path.lineTo(x + radius * 1.12f, y + radius * 0.22f)
        path.lineTo(x - radius * 0.36f, y + radius * 0.90f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = cyan
        paint.setShadowLayer(density * 15f, 0f, 0f, cyan)
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, max(1.5f, radius * 0.12f), paint)
    }

    private fun drawAim(canvas: Canvas) {
        val dx = aimStartX - aimX
        val dy = aimStartY - aimY
        val magnitude = sqrt(dx * dx + dy * dy)
        if (magnitude < density * 5f) return
        val ux = dx / magnitude
        val uy = dy / magnitude
        val x = bladeX * viewW
        val y = bladeY * viewH
        val length = minDim * 0.30f
        val endX = x + ux * length
        val endY = y + uy * length
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, density * 2f)
        paint.color = cyan
        paint.setPathEffect(android.graphics.DashPathEffect(floatArrayOf(density * 7f, density * 5f), 0f))
        canvas.drawLine(x, y, endX, endY, paint)
        paint.setPathEffect(null)
        canvas.drawCircle(endX, endY, density * 4f, paint)
        drawText(canvas, "RELEASE TO LAUNCH", 0.5f, 0.19f, 0.019f, cyan, Paint.Align.CENTER)
        paint.style = Paint.Style.FILL
    }

    private fun drawMeter(canvas: Canvas) {
        val left = viewW * 0.34f
        val right = viewW * 0.66f
        val top = viewH * 0.965f
        val bottom = top + density * 4f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(37, 49, 70)
        canvas.drawRoundRect(left, top, right, bottom, density * 4f, density * 4f, paint)
        paint.color = if (slowMotion) gold else cyan
        canvas.drawRoundRect(left, top, left + (right - left) * slowMeter, bottom, density * 4f, density * 4f, paint)
        drawText(canvas, "SLOW TIME  •  " + (slowMeter * 100f).toInt() + "%", 0.5f, 0.955f, 0.016f, muted, Paint.Align.CENTER)
    }

    private fun drawParticles(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (particle in particles) {
            val alpha = (255f * (particle.life / 0.55f).coerceIn(0f, 1f)).toInt()
            paint.color = Color.argb(alpha, Color.red(particle.color), Color.green(particle.color), Color.blue(particle.color))
            canvas.drawCircle(particle.x, particle.y, particle.radius * (particle.life / 0.55f).coerceIn(0.1f, 1f), paint)
        }
    }

    private fun drawMenuOverlayPanel(canvas: Canvas, title: String, subtitle: String) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(232, 5, 9, 22)
        canvas.drawRoundRect(viewW * 0.08f, viewH * 0.31f, viewW * 0.92f, viewH * 0.48f,
            density * 16f, density * 16f, paint)
        drawText(canvas, title, 0.5f, 0.385f, 0.044f, cyan, Paint.Align.CENTER)
        drawText(canvas, subtitle, 0.5f, 0.435f, 0.020f, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(228, 3, 6, 18)
        canvas.drawRoundRect(viewW * 0.08f, viewH * 0.30f, viewW * 0.92f, viewH * 0.75f,
            density * 18f, density * 18f, paint)
        drawText(canvas, title, 0.5f, 0.365f, 0.046f, if (title.contains("CLEAR")) cyan else pink, Paint.Align.CENTER)
        drawText(canvas, subtitle, 0.5f, 0.405f, 0.018f, Color.WHITE, Paint.Align.CENTER)
    }

    private fun drawSettings(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "SETTINGS", 0.5f, 0.29f, 0.07f, Color.WHITE, Paint.Align.CENTER)
        drawButton(canvas, 0.15f, 0.43f, 0.85f, 0.52f, "SOUND: " + if (soundEnabled) "ON" else "OFF", cyan)
        drawButton(canvas, 0.15f, 0.55f, 0.85f, 0.64f, "VIBRATION: " + if (vibrationEnabled) "ON" else "OFF", gold)
        drawButton(canvas, 0.15f, 0.69f, 0.85f, 0.78f, "BACK", pink)
        drawText(canvas, "Settings are saved on this device.", 0.5f, 0.84f, 0.020f, muted, Paint.Align.CENTER)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = (size * minDim).coerceAtLeast(8f * density)
        paint.typeface = TypefaceFactory.bold
        paint.textAlign = align
        canvas.drawText(text, x * viewW, y * viewH, paint)
    }

    private fun drawButton(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        label: String,
        color: Int,
        enabled: Boolean = true
    ) {
        val rect = RectF(left * viewW, top * viewH, right * viewW, bottom * viewH)
        paint.style = Paint.Style.FILL
        paint.color = if (enabled) Color.argb(218, 12, 23, 42) else Color.argb(145, 15, 21, 34)
        canvas.drawRoundRect(rect, density * 11f, density * 11f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, density * 1.5f)
        paint.color = if (enabled) color else muted
        if (enabled) paint.setShadowLayer(density * 7f, 0f, 0f, color)
        canvas.drawRoundRect(rect, density * 11f, density * 11f, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        drawText(canvas, label, (left + right) / 2f, (top + bottom) / 2f + 0.010f,
            if (label.length > 18) 0.019f else 0.024f, if (enabled) color else muted, Paint.Align.CENTER)
    }

    private fun addBurst(x: Float, y: Float, color: Int, count: Int) {
        val seed = (x.toInt() * 31 + y.toInt() * 17 + count * 13)
        for (i in 0 until count) {
            val angle = ((i * 137 + seed) % 360) * Math.PI.toFloat() / 180f
            val speed = density * (35f + (i % 5) * 16f)
            particles.add(Particle(x, y, kotlin.math.cos(angle) * speed, kotlin.math.sin(angle) * speed,
                0.30f + (i % 4) * 0.07f, color, density * (2f + (i % 3))))
        }
    }

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private fun showMessage(text: String) {
        message = text
        messageUntil = System.nanoTime() + 1_800_000_000L
    }

    private fun playTone(toneType: Int) {
        if (soundEnabled) {
            try {
                tone.startTone(toneType, 65)
            } catch (_: RuntimeException) {
                // Audio feedback is optional on devices without an available tone stream.
            }
        }
    }

    private fun haptic() {
        if (vibrationEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val nx = event.x / viewW
        val ny = event.y / viewH
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                slowMotion = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                slowMotion = event.pointerCount > 2
                invalidate()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                when (screen) {
                    GameScreen.MENU -> {
                        when {
                            inRect(nx, ny, 0.18f, 0.53f, 0.82f, 0.615f) -> {
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.18f, 0.635f, 0.82f, 0.715f) -> screen = GameScreen.LEVELS
                            inRect(nx, ny, 0.18f, 0.735f, 0.82f, 0.815f) -> screen = GameScreen.SETTINGS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.LEVELS -> {
                        if (inRect(nx, ny, 0.22f, 0.84f, 0.78f, 0.91f)) {
                            screen = GameScreen.MENU
                        } else {
                            for (id in 1..levels.size) {
                                val column = (id - 1) % 4
                                val row = (id - 1) / 4
                                val left = 0.055f + column * 0.23f
                                val top = 0.225f + row * 0.113f
                                if (inRect(nx, ny, left, top, left + 0.19f, top + 0.078f)) {
                                    val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
                                    if (id <= unlocked) startLevel(id) else showMessage("LOCKED")
                                    break
                                }
                            }
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.SETTINGS -> {
                        when {
                            inRect(nx, ny, 0.15f, 0.43f, 0.85f, 0.52f) -> {
                                soundEnabled = !soundEnabled
                                prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
                            }
                            inRect(nx, ny, 0.15f, 0.55f, 0.85f, 0.64f) -> {
                                vibrationEnabled = !vibrationEnabled
                                prefs.edit().putBoolean("vibration_enabled", vibrationEnabled).apply()
                            }
                            inRect(nx, ny, 0.15f, 0.69f, 0.85f, 0.78f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.PAUSED -> {
                        when {
                            inRect(nx, ny, 0.24f, 0.43f, 0.76f, 0.51f) -> {
                                screen = GameScreen.PLAYING
                                lastFrame = System.nanoTime()
                            }
                            inRect(nx, ny, 0.24f, 0.54f, 0.76f, 0.62f) -> {
                                loadLevel(levelId)
                                screen = GameScreen.PLAYING
                            }
                            inRect(nx, ny, 0.24f, 0.65f, 0.76f, 0.73f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.WON -> {
                        when {
                            inRect(nx, ny, 0.22f, 0.51f, 0.78f, 0.60f) -> {
                                if (levelId >= levels.size) screen = GameScreen.CAMPAIGN_COMPLETE
                                else startLevel(levelId + 1)
                            }
                            inRect(nx, ny, 0.22f, 0.63f, 0.78f, 0.71f) -> screen = GameScreen.LEVELS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.FAILED -> {
                        when {
                            inRect(nx, ny, 0.22f, 0.51f, 0.78f, 0.60f) -> startLevel(levelId)
                            inRect(nx, ny, 0.22f, 0.63f, 0.78f, 0.71f) -> screen = GameScreen.LEVELS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.CAMPAIGN_COMPLETE -> {
                        when {
                            inRect(nx, ny, 0.20f, 0.57f, 0.80f, 0.66f) -> startLevel(1)
                            inRect(nx, ny, 0.20f, 0.69f, 0.80f, 0.78f) -> screen = GameScreen.LEVELS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.PLAYING -> {
                        if (inRect(nx, ny, 0.81f, 0.065f, 0.94f, 0.115f)) {
                            screen = GameScreen.PAUSED
                            slowMotion = false
                            invalidate()
                            return true
                        }
                        if (!flying) {
                            aiming = true
                            aimStartX = event.x
                            aimStartY = event.y
                            aimX = event.x
                            aimY = event.y
                        }
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) slowMotion = true
                if (aiming && !flying && screen == GameScreen.PLAYING) {
                    aimX = event.x
                    aimY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (screen == GameScreen.PLAYING && aiming && !flying) {
                    aiming = false
                    slowMotion = false
                    val dx = aimStartX - event.x
                    val dy = aimStartY - event.y
                    val magnitude = sqrt(dx * dx + dy * dy)
                    if (magnitude > max(density * 14f, minDim * 0.035f)) {
                        val ux = dx / magnitude
                        val uy = dy / magnitude
                        val power = (magnitude / (minDim * 0.42f)).coerceIn(0.18f, 1f)
                        val speed = minDim * (0.62f + power * 0.90f)
                        velocityX = ux * speed
                        velocityY = uy * speed
                        flying = true
                        flightTime = 0f
                        lastFrame = System.nanoTime()
                        playTone(ToneGenerator.TONE_PROP_BEEP)
                        haptic()
                    }
                    invalidate()
                    return true
                }
                slowMotion = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                aiming = false
                slowMotion = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun inRect(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    companion object {
        private val TypefaceFactory = object {
            val bold = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }

        private fun loadLevels(context: Context): List<GameLevel> {
            val loaded = mutableListOf<GameLevel>()
            val view = GameViewLevelParser
            try {
                val names = context.assets.list("levels").orEmpty()
                    .filter { it.matches(Regex("level_[0-9]{3}\\.json")) }
                    .sorted()
                for (name in names) {
                    try {
                        val raw = context.assets.open("levels/" + name).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        loaded.add(view.parse(raw))
                    } catch (_: Exception) {
                        // Fall back to a generated stage if a level file is incomplete.
                    }
                }
            } catch (_: Exception) {
                // Built-in levels are always available.
            }
            val byId = loaded.associateBy { it.id }
            return (1..20).map { id -> byId[id] ?: makeFallbackLevel(id) }
        }

        private fun makeFallbackLevel(id: Int): GameLevel {
            val points = listOf(
                PointN(0.31f, 0.28f), PointN(0.65f, 0.27f), PointN(0.72f, 0.48f),
                PointN(0.31f, 0.52f), PointN(0.51f, 0.36f), PointN(0.65f, 0.64f),
                PointN(0.42f, 0.70f), PointN(0.78f, 0.37f)
            )
            val boxPoints = listOf(PointN(0.45f, 0.55f), PointN(0.60f, 0.43f), PointN(0.34f, 0.66f))
            return GameLevel(
                id, "Arena " + id, 5 + (id - 1) / 4, PointN(0.14f, 0.80f),
                PointN(0.87f, 0.18f), if (id % 2 == 0) PointN(0.78f, 0.75f) else PointN(0.21f, 0.75f),
                points.take((2 + id / 3).coerceAtMost(points.size)),
                boxPoints.take(if (id % 3 == 0) 3 else 2),
                listOf(HazardState("spikes", 0.5f, 0.90f, 0.56f, 0.025f))
            )
        }
    }
}

private object GameViewLevelParser {
    fun parse(json: String): GameLevel {
        val root = JSONObject(json)
        return GameLevel(
            id = root.optInt("id", 1),
            name = root.optString("name", "Arena"),
            maxBounces = root.optInt("maxBounces", 5).coerceAtLeast(1),
            spawn = point(root.optJSONObject("playerSpawn"), PointN(0.14f, 0.80f)),
            exit = point(root.optJSONObject("exit"), PointN(0.88f, 0.18f)),
            switch = point(root.optJSONObject("switch"), PointN(0.78f, 0.76f)),
            enemies = points(root.optJSONArray("enemies")),
            crates = points(root.optJSONArray("crates")),
            hazards = hazards(root.optJSONArray("hazards"))
        )
    }

    private fun point(value: JSONObject?, default: PointN): PointN = if (value == null) default else PointN(
        value.optDouble("x", default.x.toDouble()).toFloat().coerceIn(0.06f, 0.94f),
        value.optDouble("y", default.y.toDouble()).toFloat().coerceIn(0.15f, 0.94f)
    )

    private fun points(values: JSONArray?): List<PointN> {
        if (values == null) return emptyList()
        val result = mutableListOf<PointN>()
        for (i in 0 until values.length()) {
            val item = values.optJSONObject(i) ?: continue
            result.add(PointN(item.optDouble("x", 0.5).toFloat().coerceIn(0.08f, 0.92f),
                item.optDouble("y", 0.5).toFloat().coerceIn(0.17f, 0.86f)))
        }
        return result
    }

    private fun hazards(values: JSONArray?): List<HazardState> {
        if (values == null) return emptyList()
        val result = mutableListOf<HazardState>()
        for (i in 0 until values.length()) {
            val item = values.optJSONObject(i) ?: continue
            result.add(HazardState(
                item.optString("type", "spikes").lowercase(),
                item.optDouble("x", 0.5).toFloat().coerceIn(0.1f, 0.9f),
                item.optDouble("y", 0.9).toFloat().coerceIn(0.2f, 0.94f),
                item.optDouble("width", 0.56).toFloat().coerceIn(0.05f, 0.9f),
                item.optDouble("height", 0.025).toFloat().coerceIn(0.01f, 0.12f)
            ))
        }
        return result
    }
}
