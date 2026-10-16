package com.angmarx.bounceblade

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.angmarx.bounceblade.game.session.GameRules
import com.angmarx.bounceblade.game.level.LevelRepository
import com.angmarx.bounceblade.game.level.CampaignWorlds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos

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
        if (::gameView.isInitialized) {
            gameView.pauseIfPlaying()
            gameView.pauseAudioForLifecycle()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) gameView.resumeAudioForLifecycle()
    }
}

private data class PointN(val x: Float, val y: Float)
private data class EnemyState(
    val x: Float, val y: Float, var alive: Boolean = true, val kind: Int = 0,
    var health: Int = 1, var fireCooldown: Float = 1.6f, val canSplit: Boolean = true
)
private data class TurretProjectile(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)
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
    val hazards: List<HazardState>,
    val requiresAllTargets: Boolean = true,
    val requiresSwitch: Boolean = true
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
private enum class GameScreen { MENU, WORLDS, LEVELS, PLAYING, PAUSED, SETTINGS, HELP, DIFFICULTY, WON, FAILED, CAMPAIGN_COMPLETE }

private class GameView(context: Context) : View(context) {
    private val prefs = context.getSharedPreferences("bounce_blade_save", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val minDim get() = min(viewW, viewH)
    private val wallGuardMode = true
    private var guardPaddleY = 0.5f
    private var viewW = 1f
    private var viewH = 1f
    private var themeId = prefs.getInt("theme_id", 0).coerceIn(0, 2)
    private var cyan = Color.rgb(45, 235, 255)
    private var pink = Color.rgb(255, 65, 150)
    private var gold = Color.rgb(255, 199, 83)
    private var dark = Color.rgb(7, 10, 22)
    private var muted = Color.rgb(94, 120, 150)
    private var grid = Color.rgb(22, 35, 58)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 20)
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()
    private val soundIds = mutableMapOf<String, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private val bitmapAssets = mutableMapOf<String, Bitmap>()
    private val failedBitmapAssets = mutableSetOf<String>()
    private var currentMusicPath: String? = null
    private var musicPlayer: MediaPlayer? = null
    private var musicPrepared = false
    private var audioLifecyclePaused = false
    private var musicPausedBySetting = false
    private var ricochetVariant = 0
    private var timerWarningPlayed = false
    private var lastImpactX = 0f
    private var lastImpactY = 0f
    private var impactUntil = 0L
    private val bladeSpinAsset = "Core Gameplay SFX/Blade Idle Spin.wav"
    private var bladeLoopStreamId = 0
    private val soundAssets = listOf(
        bladeSpinAsset,
        "Core Gameplay SFX/Blade Launch.wav",
        "Core Gameplay SFX/Crate Break.wav",
        "Core Gameplay SFX/Enemy Slice (Variation 1).wav",
        "Core Gameplay SFX/Enemy Slice (Variation 2).wav",
        "Core Gameplay SFX/Level Complete.wav",
        "Core Gameplay SFX/Level Fail - Death.wav",
        "Core Gameplay SFX/Portal Enter.wav",
        "Core Gameplay SFX/Switch Activation.wav",
        "Core Gameplay SFX/Wall Ricochet (Variation 1).wav",
        "Core Gameplay SFX/Wall Ricochet (Variation 2).wav",
        "Core Gameplay SFX/Wall Ricochet (Variation 3).wav",
        "Optional - Extra Sounds/Blade Stuck.wav",
        "Optional - Extra Sounds/Power-Up Collect.wav",
        "Optional - Extra Sounds/Shield Break.wav",
        "Optional - Extra Sounds/Teleport - Warp.wav",
        "UI & Feedback SFX/Button Back.wav",
        "UI & Feedback SFX/Button Click.wav",
        "UI & Feedback SFX/Combo - Streak.wav",
        "UI & Feedback SFX/Pause.wav",
        "UI & Feedback SFX/Star - Score Earned.wav",
        "UI & Feedback SFX/Timer Warning.wav",
        "UI & Feedback SFX/Unpause.wav"
    )
    private val stars = (0 until 54).map { i ->
        PointN(((i * 73 + 19) % 997) / 997f, ((i * 151 + 41) % 991) / 991f)
    }
    private val levels: List<GameLevel> = loadLevels(context)
    private var difficulty = prefs.getInt("difficulty_stars", 2).coerceIn(1, 3)
    private var screen = GameScreen.MENU
    private var selectedWorld = ((prefs.getInt("unlocked_level", 1).coerceAtLeast(1) - 1) / CampaignWorlds.LEVELS_PER_WORLD).coerceIn(0, CampaignWorlds.WORLD_COUNT - 1)
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
    private var runMaxBounces = 5
    private var shotsUsed = 0
    private var earnedStars = 0
    private var score = 0
    private var levelElapsedSeconds = 0f
    private var comboCount = 0
    private var bestCombo = 0
    private var comboWindow = 0f
    private var teleportCooldown = 0f
    private val collectedPowerUps = mutableSetOf<Int>()
    private val turretProjectiles = mutableListOf<TurretProjectile>()
    private var animationTime = 0f
    private var slowMeter = 1f
    private val bladeTrail = mutableListOf<PointF>()
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
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSoundIds.add(sampleId)
        }
        loadSoundAssets()
        applyTheme()
        loadLevel(1)
        screen = GameScreen.MENU
        isFocusable = true
        isClickable = true
    }

    fun handleBack(): Boolean {
        playSfx("UI & Feedback SFX/Button Back.wav")
        when (screen) {
            GameScreen.PLAYING -> screen = GameScreen.PAUSED
            GameScreen.PAUSED, GameScreen.SETTINGS, GameScreen.HELP, GameScreen.DIFFICULTY -> screen = GameScreen.MENU
            GameScreen.WORLDS -> screen = GameScreen.MENU
            GameScreen.LEVELS -> screen = GameScreen.WORLDS
            GameScreen.WON, GameScreen.FAILED, GameScreen.CAMPAIGN_COMPLETE -> {
                selectedWorld = CampaignWorlds.indexForLevel(levelId)
                screen = GameScreen.LEVELS
            }
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
        stopBladeIdleLoop()
        musicPlayer?.release()
        musicPlayer = null
        soundPool.release()
        tone.release()
        bitmapAssets.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        bitmapAssets.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        viewW = width.toFloat().coerceAtLeast(1f)
        viewH = height.toFloat().coerceAtLeast(1f)
        advanceFrame()
        updateMusicForScreen()
        updateBladeIdleLoop()
        drawBackground(canvas)
        when (screen) {
            GameScreen.MENU -> drawMenu(canvas)
            GameScreen.WORLDS -> drawWorldSelect(canvas)
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
            GameScreen.HELP -> drawHelp(canvas)
            GameScreen.DIFFICULTY -> drawDifficulty(canvas)
            GameScreen.WON -> {
                drawGame(canvas)
                drawOverlay(canvas, "LEVEL CLEARED", "Nice shot! The exit is open.")
                drawButton(canvas, 0.22f, 0.51f, 0.78f, 0.60f, "NEXT LEVEL", cyan)
                drawButton(canvas, 0.22f, 0.63f, 0.78f, 0.71f, "LEVEL SELECT", gold)
            }
            GameScreen.FAILED -> {
                drawGame(canvas)
                drawOverlay(canvas, "TRY AGAIN", message.ifBlank { "Reset and try a different angle." })
                drawButton(canvas, 0.22f, 0.51f, 0.78f, 0.60f, "RETRY", cyan)
                drawButton(canvas, 0.22f, 0.63f, 0.78f, 0.71f, "LEVEL SELECT", gold)
            }
            GameScreen.CAMPAIGN_COMPLETE -> {
                drawMenuBackdrop(canvas)
                drawAsset(canvas, "Backgrounds & Environments/Victory Background.png", RectF(0f, 0f, viewW, viewH), 238)
                drawAsset(canvas, "Banners & Text/Victory Banner.png", RectF(viewW * 0.12f, viewH * 0.27f, viewW * 0.88f, viewH * 0.43f), 255)
                drawText(canvas, "You conquered all five worlds.", 0.5f, 0.48f, 0.024f, Color.WHITE, Paint.Align.CENTER)
                drawButton(canvas, 0.20f, 0.57f, 0.80f, 0.66f, "PLAY AGAIN", cyan)
                drawButton(canvas, 0.20f, 0.69f, 0.80f, 0.78f, "LEVEL SELECT", gold)
            }
        }
        if (screen == GameScreen.PLAYING || flying || particles.isNotEmpty() || turretProjectiles.isNotEmpty()) postInvalidateDelayed(16L)
    }

    private fun advanceFrame() {
        val now = System.nanoTime()
        val rawDt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.025f)
        lastFrame = now
        animationTime += rawDt
        if (screen == GameScreen.PLAYING) {
            levelElapsedSeconds += rawDt
            comboWindow = (comboWindow - rawDt).coerceAtLeast(0f)
            if (comboWindow <= 0f) comboCount = 0
            teleportCooldown = (teleportCooldown - rawDt).coerceAtLeast(0f)
            if (!wallGuardMode) {
                val timeLeft = GameRules.timeLimitSeconds(levelId, difficulty) - levelElapsedSeconds
                if (timeLeft <= 5f && timeLeft > 0f && !timerWarningPlayed) {
                    timerWarningPlayed = true
                    playSfx("UI & Feedback SFX/Timer Warning.wav", ToneGenerator.TONE_PROP_BEEP)
                }
                if (levelElapsedSeconds > GameRules.timeLimitSeconds(levelId, difficulty)) { fail("TIME UP"); return }
                updateTurrets(rawDt)
                updateTurretProjectiles(rawDt)
            }
        }
        if (screen == GameScreen.PLAYING && flying) {
            val timeScale = if (slowMotion && slowMeter > 0f) 0.28f else 1f
            val dt = rawDt * timeScale
            if (slowMotion && slowMeter > 0f) slowMeter = (slowMeter - rawDt * 0.20f).coerceAtLeast(0f)
            else slowMeter = (slowMeter + rawDt * 0.075f).coerceAtMost(1f)
            flightTime += dt
            angle = (angle + 1080f * dt) % 360f
            repeat(3) { if (screen == GameScreen.PLAYING && flying) simulateStep(dt / 3f) }
            if (flying && !wallGuardMode &&
                (sqrt(velocityX * velocityX + velocityY * velocityY) < minDim * 0.055f || flightTime > 12f)) {
                flying = false; velocityX = 0f; velocityY = 0f
            }
        } else {
            if (!slowMotion) slowMeter = (slowMeter + rawDt * 0.04f).coerceAtMost(1f)
            if (screen != GameScreen.PLAYING) slowMotion = false
        }
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= rawDt; p.x += p.vx * rawDt; p.y += p.vy * rawDt
            p.vx *= 0.985f; p.vy *= 0.985f
            if (p.life <= 0f) iterator.remove()
        }
    }

    private fun updateTurrets(dt: Float) {
        if (!flying || screen != GameScreen.PLAYING) return
        enemies.forEachIndexed { index, enemy ->
            if (!enemy.alive || enemy.kind != 1) return@forEachIndexed
            enemy.fireCooldown -= dt
            if (enemy.fireCooldown <= 0f) {
                val c = enemyCenter(enemy, index)
                val sx = c.x * viewW; val sy = c.y * viewH
                val dx = bladeX * viewW - sx; val dy = bladeY * viewH - sy
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val speed = minDim * 0.22f
                turretProjectiles.add(TurretProjectile(sx, sy, dx / len * speed, dy / len * speed, 4.5f))
                enemy.fireCooldown = 2.4f + (index % 3) * 0.35f
            }
        }
    }

    private fun updateTurretProjectiles(dt: Float) {
        val iterator = turretProjectiles.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            shot.x += shot.vx * dt; shot.y += shot.vy * dt; shot.life -= dt
            if (shot.life <= 0f || shot.x < 0f || shot.x > viewW || shot.y < 0f || shot.y > viewH) {
                iterator.remove(); continue
            }
            if (flying && screen == GameScreen.PLAYING && distanceSquared(shot.x, shot.y, bladeX * viewW, bladeY * viewH) < (minDim * 0.028f) * (minDim * 0.028f)) {
                reflectFrom(bladeX * viewW - shot.x, bladeY * viewH - shot.y)
                velocityX *= 0.9f; velocityY *= 0.9f
                useBounce(); addBurst(shot.x, shot.y, pink, 8); showMessage("TURRET SHOT DEFLECTED")
                iterator.remove()
            }
        }
    }

    private fun registerTargetHit(points: Int, x: Float, y: Float, color: Int) {
        comboCount = if (comboWindow > 0f) comboCount + 1 else 1
        comboWindow = 2.25f; bestCombo = max(bestCombo, comboCount)
        score += points * comboCount
        lastImpactX = x; lastImpactY = y; impactUntil = System.nanoTime() + 320_000_000L
        addBurst(x, y, color, if (comboCount >= 3) 20 else 13)
        if (comboCount >= 2) { showMessage("COMBO x" + comboCount); playSfx("UI & Feedback SFX/Combo - Streak.wav") }
    }

    private fun enemyKindForLevel(id: Int): Int = when (id) {
        in 1..20 -> 0
        in 21..30 -> 1
        in 31..40 -> 2
        else -> 3
    }

    private fun enemyHealthForLevel(id: Int, index: Int): Int = when {
        id == 50 && index == 0 -> 5
        id >= 41 -> 2
        else -> 1
    }

    private fun enemyCenter(enemy: EnemyState, index: Int): PointF {
        val d = when (enemy.kind) {
            0 -> PointF(sin(animationTime * 1.7f + index * 1.2f) * 0.014f, cos(animationTime * 1.3f + index) * 0.012f)
            2 -> PointF(sin(animationTime * 1.25f + index * 1.4f) * if (enemy.canSplit) 0.035f else 0.02f, cos(animationTime * 1.8f + index) * 0.02f)
            3 -> PointF(sin(animationTime * 0.75f + index) * 0.009f, 0f)
            else -> PointF(0f, 0f)
        }
        return PointF((enemy.x + d.x).coerceIn(0.1f, 0.9f), (enemy.y + d.y).coerceIn(0.17f, 0.84f))
    }

    private fun formatTime(seconds: Float): String {
        val value = seconds.coerceAtLeast(0f).toInt()
        return "%02d:%02d".format(value / 60, value % 60)
    }

    private fun simulateStep(dt: Float) {
        if (wallGuardMode) {
            simulateWallGuardStep(dt)
            return
        }
        val radius = minDim * 0.022f
        val left = viewW * 0.075f + radius
        val right = viewW * 0.925f - radius
        val top = viewH * 0.145f + radius
        val bottom = viewH * 0.925f - radius
        var px = bladeX * viewW + velocityX * dt
        var py = bladeY * viewH + velocityY * dt

        for ((hazardIndex, hazard) in level.hazards.withIndex()) {
            if (hazard.type == "power_up" && hazardIndex in collectedPowerUps) continue
            val c = hazardCenter(hazard, hazardIndex)
            val hx = c.x * viewW; val hy = c.y * viewH
            val hw = hazard.width * viewW; val hh = max(hazard.height * viewH, minDim * 0.018f)
            val hit = px + radius > hx - hw / 2f && px - radius < hx + hw / 2f && py + radius > hy - hh / 2f && py - radius < hy + hh / 2f
            if (!hit) continue
            when (hazard.type) {
                "sticky_wall" -> { fail("STICKY WALL: BLADE STUCK"); return }
                "teleporter" -> {
                    if (teleportCooldown <= 0f) {
                        val dest = level.hazards.withIndex().firstOrNull { it.index != hazardIndex && it.value.type == "teleporter" }
                        if (dest != null) {
                            px = dest.value.x * viewW; py = dest.value.y * viewH; teleportCooldown = 0.85f
                            addBurst(px, py, cyan, 16); showMessage("WARP!"); playSfx("Optional - Extra Sounds/Teleport - Warp.wav"); break
                        }
                    }
                }
                "power_up" -> if (collectedPowerUps.add(hazardIndex)) {
                    bouncesLeft += 2; score += 250; addBurst(hx, hy, gold, 18)
                    showMessage("POWER-UP +2 BOUNCES"); playSfx("Optional - Extra Sounds/Power-Up Collect.wav")
                }
                else -> { fail("You hit a hazard."); return }
            }
        }

        var bounced = false
        if (px < left) {
            px = left
            velocityX = abs(velocityX) * 0.96f
            bounced = true
        } else if (px > right) {
            px = right
            velocityX = -abs(velocityX) * 0.96f
            bounced = true
        }
        if (py < top) {
            py = top
            velocityY = abs(velocityY) * 0.96f
            bounced = true
        } else if (py > bottom) {
            py = bottom
            velocityY = -abs(velocityY) * 0.96f
            bounced = true
        }
        bladeX = px / viewW
        bladeY = py / viewH
        bladeTrail.add(PointF(px, py))
        while (bladeTrail.size > 22) bladeTrail.removeAt(0)
        if (bounced) {
            useBounce()
            addBurst(px, py, cyan, 8)
            playNextRicochet()
            if (screen != GameScreen.PLAYING) return
        }

        val pendingSlimes = mutableListOf<EnemyState>()
        for ((index, enemy) in enemies.toList().withIndex()) {
            if (!enemy.alive) continue
            val c = enemyCenter(enemy, index); val ex = c.x * viewW; val ey = c.y * viewH
            val dx = px - ex; val dy = py - ey
            val hitRadius = radius + minDim * (if (enemy.kind == 3 && enemy.health > 1) 0.04f else 0.027f)
            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                enemy.health -= 1
                if (enemy.health <= 0) {
                    enemy.alive = false
                    if (enemy.kind == 2 && enemy.canSplit) {
                        pendingSlimes.add(EnemyState((enemy.x - 0.035f).coerceIn(0.1f,0.9f),(enemy.y+0.024f).coerceIn(0.2f,0.82f),true,2,1,8f,false))
                        pendingSlimes.add(EnemyState((enemy.x + 0.035f).coerceIn(0.1f,0.9f),(enemy.y+0.024f).coerceIn(0.2f,0.82f),true,2,1,8f,false))
                        showMessage("SLIME SPLIT")
                    }
                    registerTargetHit(if (enemy.kind == 3) 160 else 100, ex, ey, pink)
                    playSfx(if (index % 2 == 0) "Core Gameplay SFX/Enemy Slice (Variation 1).wav" else "Core Gameplay SFX/Enemy Slice (Variation 2).wav")
                } else {
                    score += 50; addBurst(ex, ey, gold, 10)
                    showMessage(if (levelId == 50 && index == 0) "BOSS SHIELD DAMAGED" else "SHIELD BREAK")
                    playSfx("Optional - Extra Sounds/Shield Break.wav")
                }
                reflectFrom(dx, dy); velocityX *= 0.90f; velocityY *= 0.90f
                useBounce(); haptic()
                if (screen != GameScreen.PLAYING) return
            }
        }
        enemies.addAll(pendingSlimes)
        for (crate in crates) {
            if (!crate.alive) continue
            val cx=crate.x*viewW; val cy=crate.y*viewH; val half=minDim*0.035f
            if (abs(px-cx)<half+radius && abs(py-cy)<half+radius) {
                crate.alive=false
                if (abs(px-cx)>abs(py-cy)) { velocityX=-velocityX*0.86f; velocityY*=0.94f }
                else { velocityY=-velocityY*0.86f; velocityX*=0.94f }
                registerTargetHit(75,cx,cy,gold); useBounce(); playSfx("Core Gameplay SFX/Crate Break.wav"); haptic()
                if (screen != GameScreen.PLAYING) return
            }
        }

        val sx = level.switch.x * viewW
        val sy = level.switch.y * viewH
        val switchRadius = minDim * 0.032f
        if (level.requiresSwitch && !switchActivated && distanceSquared(px, py, sx, sy) < (radius + switchRadius) * (radius + switchRadius)) {
            switchActivated = true
            addBurst(sx, sy, gold, 18)
            velocityX *= 0.96f
            velocityY *= 0.96f
            showMessage("SWITCH ACTIVATED")
            playSfx("Core Gameplay SFX/Switch Activation.wav")
            haptic()
        }

        val exitReady = GameRules.exitIsUnlocked(enemies.count { it.alive }, crates.count { it.alive }, switchActivated, level.requiresAllTargets, level.requiresSwitch)
        val ex = level.exit.x * viewW
        val ey = level.exit.y * viewH
        val exitRadius = minDim * 0.034f
        if (distanceSquared(px, py, ex, ey) < (radius + exitRadius) * (radius + exitRadius)) {
            if (exitReady) {
                playSfx("Core Gameplay SFX/Portal Enter.wav")
                completeLevel()
                return
            } else {
                val dx = px - ex
                val dy = py - ey
                if (velocityX * dx + velocityY * dy < 0f) {
                    reflectFrom(dx, dy)
                    velocityX *= 0.92f
                    velocityY *= 0.92f
                }
                showMessage("EXIT LOCKED: CLEAR TARGETS AND HIT SWITCH")
            }
        }
    }


    /**
     * Arcade mode: the blade moves automatically, targets send it back toward
     * the player, and the player moves the vertical shield along the left edge.
     * A miss at the left edge ends the run immediately.
     */
    private fun simulateWallGuardStep(dt: Float) {
        val radius = minDim * 0.022f
        val leftContact = viewW * 0.098f + radius
        val right = viewW * 0.955f - radius
        val top = viewH * 0.145f + radius
        val bottom = viewH * 0.935f - radius
        var px = bladeX * viewW + velocityX * dt
        var py = bladeY * viewH + velocityY * dt

        var wallHit = false
        if (px > right) {
            px = right
            velocityX = -abs(velocityX)
            wallHit = true
        }
        if (py < top) {
            py = top
            velocityY = abs(velocityY)
            wallHit = true
        } else if (py > bottom) {
            py = bottom
            velocityY = -abs(velocityY)
            wallHit = true
        }
        if (wallHit) {
            addBurst(px, py, cyan, 7)
            playNextRicochet()
        }

        if (px < leftContact) {
            val paddleCenter = guardPaddleY * viewH
            val paddleHalf = guardPaddleHalf()
            if (abs(py - paddleCenter) <= paddleHalf + radius) {
                px = leftContact
                val offset = ((py - paddleCenter) / (paddleHalf + radius)).coerceIn(-1f, 1f)
                val oldSpeed = sqrt(velocityX * velocityX + velocityY * velocityY)
                val speed = (oldSpeed * 1.035f).coerceIn(minDim * 0.46f, minDim * 0.90f)
                val bounceAngle = offset * 1.10f
                velocityX = abs(cos(bounceAngle) * speed).coerceAtLeast(minDim * 0.32f)
                velocityY = sin(bounceAngle) * speed
                comboCount = (comboCount + 1).coerceAtMost(99)
                bestCombo = max(bestCombo, comboCount)
                comboWindow = 2.0f
                addBurst(px, py, gold, 12)
                showMessage("WALL SAVE!")
                playNextRicochet()
                haptic()
            } else {
                bladeX = px / viewW
                bladeY = py / viewH
                fail("THE BLADE PASSED THE LEFT WALL")
                return
            }
        }

        for ((index, enemy) in enemies.withIndex()) {
            if (!enemy.alive) continue
            val center = enemyCenter(enemy, index)
            val ex = center.x * viewW
            val ey = center.y * viewH
            val hitRadius = radius + minDim * (if (enemy.kind == 3) 0.055f else 0.035f)
            if (distanceSquared(px, py, ex, ey) > hitRadius * hitRadius) continue

            val cameFromLeft = velocityX > 0f
            enemy.health -= 1
            if (enemy.health <= 0) {
                enemy.alive = false
                registerTargetHit(if (enemy.kind == 3) 250 else 100, ex, ey, pink)
                playSfx(
                    if (index % 2 == 0) "Core Gameplay SFX/Enemy Slice (Variation 1).wav"
                    else "Core Gameplay SFX/Enemy Slice (Variation 2).wav"
                )
            } else {
                score += 50
                addBurst(ex, ey, gold, 12)
                showMessage("ENEMY HIT")
                playSfx("Optional - Extra Sounds/Shield Break.wav")
            }

            val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
                .coerceIn(minDim * 0.46f, minDim * 0.90f)
            velocityX = if (cameFromLeft) -abs(velocityX) else abs(velocityX)
            velocityY += ((py - ey) / hitRadius).coerceIn(-1f, 1f) * minDim * 0.16f
            val adjustedSpeed = sqrt(velocityX * velocityX + velocityY * velocityY).coerceAtLeast(1f)
            velocityX *= speed / adjustedSpeed
            velocityY *= speed / adjustedSpeed
            px = if (cameFromLeft) ex - hitRadius else ex + hitRadius
            addBurst(ex, ey, pink, 14)
            haptic()
            break
        }

        bladeX = (px / viewW).coerceIn(0f, 1f)
        bladeY = (py / viewH).coerceIn(0f, 1f)
        bladeTrail.add(PointF(px, py))
        while (bladeTrail.size > 24) bladeTrail.removeAt(0)

        if (enemies.none { it.alive }) {
            completeLevel()
            return
        }
    }

    private fun guardPaddleHalf(): Float = viewH * when (difficulty) {
        1 -> 0.105f
        3 -> 0.065f
        else -> 0.085f
    }

    private fun clampGuardPaddleY(y: Float): Float {
        val half = guardPaddleHalf()
        val top = viewH * 0.145f + half
        val bottom = viewH * 0.935f - half
        return (y * viewH).coerceIn(top, bottom) / viewH
    }

    private fun launchWallGuardBlade() {
        guardPaddleY = clampGuardPaddleY(guardPaddleY)
        bladeX = 0.145f
        bladeY = guardPaddleY
        val speed = minDim * (0.50f + difficulty * 0.075f + min(levelId, 30) * 0.003f)
        velocityX = speed
        velocityY = speed * (sin(levelId * 1.37f) * 0.22f)
        flying = true
        aiming = false
        shotsUsed = 1
        flightTime = 0f
        bladeTrail.clear()
        addBurst(bladeX * viewW, bladeY * viewH, cyan, 8)
        lastFrame = System.nanoTime()
        playSfx("Core Gameplay SFX/Blade Launch.wav", ToneGenerator.TONE_PROP_BEEP)
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
        if (GameRules.bounceBudgetExceeded(bouncesLeft) && screen == GameScreen.PLAYING) fail("Out of bounces.")
    }

    private fun completeLevel() {
        flying = false
        aiming = false
        velocityX = 0f
        velocityY = 0f
        val nextId = levelId + 1
        val currentUnlocked = prefs.getInt("unlocked_level", 1)
        val newUnlocked = max(currentUnlocked, min(nextId, levels.size))
        val allTargetsDestroyed = enemies.none { it.alive } && crates.none { it.alive }
        earnedStars = GameRules.starsEarnedByPerformance(levelElapsedSeconds, GameRules.targetTimeSeconds(levelId, difficulty), allTargetsDestroyed, bestCombo)
        val starsKey = bestStarsKey(levelId)
        val previousBest = prefs.getInt(starsKey, 0)
        val bestShotsKey = bestShotsKey(levelId)
        val previousBestShots = prefs.getInt(bestShotsKey, Int.MAX_VALUE)
        prefs.edit()
            .putInt("unlocked_level", newUnlocked)
            .putInt(starsKey, max(previousBest, earnedStars))
            .putInt(bestShotsKey, min(previousBestShots, shotsUsed))
            .putInt("best_score_" + levelId + "_difficulty_" + difficulty, max(prefs.getInt("best_score_" + levelId + "_difficulty_" + difficulty, 0), score))
            .putInt("campaign_best_stars_difficulty_" + difficulty,
                totalBestStarsAfter(levelId, max(previousBest, earnedStars)))
            .apply()
        screen = if (levelId >= levels.size) GameScreen.CAMPAIGN_COMPLETE else GameScreen.WON
        playSfx("Core Gameplay SFX/Level Complete.wav")
        if (earnedStars > previousBest) playSfx("UI & Feedback SFX/Star - Score Earned.wav")
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
        messageUntil = Long.MAX_VALUE
        playSfx(if (reason.contains("STUCK")) "Optional - Extra Sounds/Blade Stuck.wav" else "Core Gameplay SFX/Level Fail - Death.wav", ToneGenerator.TONE_PROP_NACK)
        haptic()
    }

    private fun loadLevel(id: Int) {
        levelId = id.coerceIn(1, levels.size)
        level = levels.firstOrNull { it.id == levelId } ?: levels.first()
        if (wallGuardMode) {
            // Make every campaign stage playable in the arcade format, even
            // when its legacy puzzle JSON did not define any enemies.
            val targetCount = (3 + (levelId - 1) / 2).coerceAtMost(12)
            val points = (0 until targetCount).map { index ->
                val column = index % 4
                val row = index / 4
                PointN(0.49f + column * 0.115f, 0.235f + row * 0.215f)
            }
            level = level.copy(
                spawn = PointN(0.145f, 0.5f),
                enemies = points,
                crates = emptyList(),
                hazards = emptyList(),
                requiresAllTargets = true,
                requiresSwitch = false
            )
            guardPaddleY = 0.5f
        }
        enemies = level.enemies.mapIndexed { index, point ->
            EnemyState(point.x, point.y, true, enemyKindForLevel(levelId), enemyHealthForLevel(levelId, index), 1.2f + (index % 3) * 0.6f, true)
        }.toMutableList()
        crates = level.crates.map { CrateState(it.x, it.y) }.toMutableList()
        bladeX = if (wallGuardMode) 0.145f else level.spawn.x
        bladeY = if (wallGuardMode) guardPaddleY else level.spawn.y
        velocityX = 0f
        velocityY = 0f
        angle = 0f
        flying = false
        aiming = false
        slowMotion = false
        switchActivated = !level.requiresSwitch
        runMaxBounces = GameRules.bouncesForDifficulty(level.maxBounces, difficulty)
        bouncesLeft = runMaxBounces
        shotsUsed = 0
        earnedStars = 0
        score = 0
        levelElapsedSeconds = 0f
        comboCount = 0
        bestCombo = 0
        comboWindow = 0f
        teleportCooldown = 0f
        collectedPowerUps.clear()
        turretProjectiles.clear()
        timerWarningPlayed = false
        impactUntil = 0L
        slowMeter = 1f
        flightTime = 0f
        particles.clear()
        bladeTrail.clear()
        message = ""
        messageUntil = 0L
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
        if (wallGuardMode) launchWallGuardBlade()
        invalidate()
    }

    private fun loadLevels(ctx: Context): List<GameLevel> {
        val loaded = try {
            LevelRepository().loadAll(ctx).map { definition ->
                GameLevel(
                    definition.id,
                    definition.name,
                    definition.maxBounces,
                    PointN(definition.playerSpawn.x, definition.playerSpawn.y),
                    PointN(definition.exit.x, definition.exit.y),
                    PointN(definition.switch.x, definition.switch.y),
                    definition.enemies.map { PointN(it.x, it.y) },
                    definition.crates.map { PointN(it.x, it.y) },
                    definition.hazards.map {
                        HazardState(it.type.name.lowercase(), it.position.x, it.position.y, it.width, it.height)
                    },
                    definition.requiresAllTargets,
                    definition.requiresSwitch
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        val byId = loaded.associateBy { it.id }
        return (1..CampaignWorlds.TOTAL_LEVELS).map { id -> byId[id] ?: fallbackLevel(id) }
    }

    private fun fallbackLevel(id: Int): GameLevel {
        val world = CampaignWorlds.indexForLevel(id)
        val enemies = listOf(PointN(0.31f,0.28f),PointN(0.65f,0.27f),PointN(0.72f,0.48f),PointN(0.31f,0.52f),PointN(0.51f,0.36f),PointN(0.65f,0.64f),PointN(0.42f,0.70f),PointN(0.78f,0.37f))
        val crates = listOf(PointN(0.45f,0.55f),PointN(0.60f,0.43f),PointN(0.34f,0.66f))
        val tutorial = world == 0
        return GameLevel(id,CampaignWorlds.worldName(world)+" "+((id-1)%10+1),8+(id-1)/5,
            PointN(0.14f,0.80f),PointN(0.87f,0.18f),PointN(0.78f,0.75f),
            if(tutorial) emptyList() else enemies.take((2+id/7).coerceAtMost(enemies.size)),
            if(tutorial) emptyList() else crates.take(2+id%2),
            if(tutorial) emptyList() else listOf(HazardState("spikes",0.5f,0.90f,0.56f,0.025f)),!tutorial,!tutorial)
    }

    private fun applyTheme() {
        when (themeId.coerceIn(0, 2)) {
            1 -> {
                cyan = Color.rgb(75, 255, 150)
                pink = Color.rgb(255, 105, 75)
                gold = Color.rgb(220, 255, 90)
                dark = Color.rgb(5, 18, 18)
                muted = Color.rgb(90, 146, 128)
                grid = Color.rgb(17, 50, 47)
            }
            2 -> {
                cyan = Color.rgb(180, 130, 255)
                pink = Color.rgb(255, 90, 190)
                gold = Color.rgb(255, 190, 100)
                dark = Color.rgb(13, 7, 25)
                muted = Color.rgb(132, 112, 165)
                grid = Color.rgb(41, 25, 63)
            }
            else -> {
                cyan = Color.rgb(45, 235, 255)
                pink = Color.rgb(255, 65, 150)
                gold = Color.rgb(255, 199, 83)
                dark = Color.rgb(7, 10, 22)
                muted = Color.rgb(94, 120, 150)
                grid = Color.rgb(22, 35, 58)
            }
        }
    }

    private fun themeName(): String = when (themeId) {
        1 -> "GREEN"
        2 -> "VIOLET"
        else -> "CYAN"
    }

    private fun bestStarsKey(id: Int, forDifficulty: Int = difficulty): String =
        if (forDifficulty == 2) "best_stars_" + id
        else "best_stars_" + id + "_difficulty_" + forDifficulty

    private fun bestShotsKey(id: Int, forDifficulty: Int = difficulty): String =
        if (forDifficulty == 2) "best_shots_" + id
        else "best_shots_" + id + "_difficulty_" + forDifficulty

    private fun loadSoundAssets() {
        for (path in soundAssets) {
            try {
                context.assets.openFd(path).use { descriptor -> soundIds[path] = soundPool.load(descriptor, 1) }
            } catch (_: Exception) {
                // A missing optional sample should never prevent the game from starting.
            }
        }
    }

    private fun bitmapAsset(path: String): Bitmap? {
        bitmapAssets[path]?.let { return it }
        if (path in failedBitmapAssets) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Invalid image: $path")
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Could not decode image: $path")
            bitmapAssets[path] = bitmap
            bitmap
        } catch (_: Exception) {
            failedBitmapAssets.add(path)
            null
        }
    }

    private fun drawAsset(canvas: Canvas, path: String, rect: RectF, alpha: Int = 255) {
        val bitmap = bitmapAsset(path) ?: return
        val oldAlpha = paint.alpha
        val oldFilter = paint.isFilterBitmap
        val oldStyle = paint.style
        paint.alpha = alpha.coerceIn(0, 255)
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, null, rect, paint)
        paint.alpha = oldAlpha
        paint.isFilterBitmap = oldFilter
        paint.style = oldStyle
    }

    private fun drawAssetCentered(canvas: Canvas, path: String, x: Float, y: Float, size: Float, alpha: Int = 255) {
        drawAsset(canvas, path, RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f), alpha)
    }

    private fun playTone(toneType: Int) {
        if (soundEnabled) try { tone.startTone(toneType, 65) } catch (_: RuntimeException) { }
    }

    private fun playSfx(path: String, fallbackTone: Int = ToneGenerator.TONE_PROP_ACK) {
        if (!soundEnabled || audioLifecyclePaused) return
        val sampleId = soundIds[path]
        if (sampleId == null || sampleId !in loadedSoundIds) {
            playTone(fallbackTone)
            return
        }
        if (soundPool.play(sampleId, 0.92f, 0.92f, 1, 0, 1f) == 0) playTone(fallbackTone)
    }

    private fun playNextRicochet() {
        ricochetVariant = (ricochetVariant % 3) + 1
        playSfx("Core Gameplay SFX/Wall Ricochet (Variation $ricochetVariant).wav", ToneGenerator.TONE_PROP_BEEP)
    }

    fun pauseAudioForLifecycle() {
        audioLifecyclePaused = true
        musicPausedBySetting = true
        if (musicPrepared) try { musicPlayer?.pause() } catch (_: IllegalStateException) { }
        stopBladeIdleLoop()
    }

    fun resumeAudioForLifecycle() {
        audioLifecyclePaused = false
        invalidate()
    }

    private fun updateMusicForScreen() {
        val target = when (screen) {
            GameScreen.MENU, GameScreen.WORLDS, GameScreen.LEVELS, GameScreen.SETTINGS, GameScreen.HELP, GameScreen.DIFFICULTY ->
                "Music & Ambience/Main Menu Theme.wav"
            GameScreen.PLAYING, GameScreen.PAUSED -> "Music & Ambience/Gameplay Background.wav"
            GameScreen.WON, GameScreen.CAMPAIGN_COMPLETE -> "Music & Ambience/Victory Theme.wav"
            GameScreen.FAILED -> "Music & Ambience/Game Over Theme.wav"
        }
        if (!soundEnabled || audioLifecyclePaused) {
            if (musicPrepared) try { musicPlayer?.pause() } catch (_: IllegalStateException) { }
            musicPausedBySetting = true
            return
        }
        if (target == currentMusicPath) {
            if (musicPrepared && musicPausedBySetting) {
                try { musicPlayer?.start(); musicPausedBySetting = false } catch (_: IllegalStateException) { }
            }
            return
        }
        musicPlayer?.release()
        musicPlayer = null
        currentMusicPath = target
        musicPrepared = false
        musicPausedBySetting = false
        try {
            val player = MediaPlayer()
            musicPlayer = player
            context.assets.openFd(target).use { descriptor ->
                player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            player.isLooping = true
            player.setVolume(0.34f, 0.34f)
            player.setOnPreparedListener { prepared ->
                if (currentMusicPath == target) {
                    musicPrepared = true
                    if (soundEnabled && !audioLifecyclePaused) prepared.start() else musicPausedBySetting = true
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                if (currentMusicPath == target) {
                    failed.release(); musicPlayer = null; currentMusicPath = null; musicPrepared = false
                }
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            musicPlayer?.release(); musicPlayer = null; currentMusicPath = null; musicPrepared = false
        }
    }

    private fun updateBladeIdleLoop() {
        val shouldLoop = screen == GameScreen.PLAYING && !flying && soundEnabled && !audioLifecyclePaused
        if (!shouldLoop) { stopBladeIdleLoop(); return }
        if (bladeLoopStreamId != 0) return
        val sampleId = soundIds[bladeSpinAsset] ?: return
        if (sampleId !in loadedSoundIds) return
        bladeLoopStreamId = soundPool.play(sampleId, 0.16f, 0.16f, 0, -1, 1f)
    }

    private fun stopBladeIdleLoop() {
        if (bladeLoopStreamId != 0) {
            try { soundPool.stop(bladeLoopStreamId) } catch (_: RuntimeException) { }
            bladeLoopStreamId = 0
        }
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
        for (i in 1..5) canvas.drawLine(viewW * i / 6f, viewH * 0.02f, viewW * i / 6f, viewH * 0.98f, paint)
        for (i in 1..12) canvas.drawLine(viewW * 0.02f, viewH * i / 13f, viewW * 0.98f, viewH * i / 13f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawMenuBackdrop(canvas: Canvas) {
        drawBackground(canvas)
        val image = if (screen == GameScreen.MENU) "UI Elements/Main Menu Background.png"
            else "Backgrounds & Environments/Menu Background.png"
        drawAsset(canvas, image, RectF(0f, 0f, viewW, viewH), 235)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(62, 3, 6, 18)
        canvas.drawRect(0f, 0f, viewW, viewH, paint)
    }

    private fun drawMenu(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawAsset(canvas, "App Icon & Logo/Game Logo.png", RectF(viewW * 0.16f, viewH * 0.13f, viewW * 0.84f, viewH * 0.29f), 255)
        drawText(canvas, "BOUNCE OFF ENEMIES  •  GUARD THE LEFT WALL", 0.5f, 0.465f, 0.020f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.18f, 0.52f, 0.82f, 0.585f, "PLAY CAMPAIGN", cyan)
        drawButton(canvas, 0.18f, 0.595f, 0.82f, 0.66f, "LEVEL SELECT", gold)
        drawButton(canvas, 0.18f, 0.67f, 0.82f, 0.735f, "HELP", pink)
        drawButton(canvas, 0.18f, 0.745f, 0.82f, 0.81f, "SETTINGS", cyan)
        val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
        val bestTotal = totalBestStarsAfter(0, 0)
        drawText(canvas, "UNLOCKED  " + unlocked + "/" + levels.size + "     BEST STARS  " + bestTotal + "/" + (levels.size * 3),
            0.5f, 0.88f, 0.019f, muted, Paint.Align.CENTER)
        drawText(canvas, "DRAG UP/DOWN TO MOVE THE SHIELD  •  CATCH EVERY RETURN", 0.5f, 0.925f, 0.014f, muted, Paint.Align.CENTER)
    }
    private fun drawWorldSelect(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas,"CHOOSE YOUR WORLD",0.5f,0.285f,0.058f,Color.WHITE,Paint.Align.CENTER)
        drawText(canvas,"50 LEVELS  •  5 WORLDS  •  OFFLINE CAMPAIGN",0.5f,0.335f,0.019f,muted,Paint.Align.CENTER)
        val unlocked=prefs.getInt("unlocked_level",1).coerceIn(1,levels.size)
        for(w in 0 until CampaignWorlds.WORLD_COUNT){
            val first=CampaignWorlds.firstLevelForWorld(w); val available=unlocked>=first; val top=0.375f+w*0.088f
            val total=(first..min(first+9,levels.size)).sumOf{prefs.getInt(bestStarsKey(it),0)}
            val label=(w+1).toString().padStart(2,'0')+"  "+CampaignWorlds.worldName(w)+"  "+if(available) "$total/30 ★" else "LOCKED"
            drawButton(canvas,0.12f,top,0.88f,top+0.066f,label,if(available) cyan else muted,available)
        }
        drawButton(canvas,0.22f,0.83f,0.78f,0.90f,"BACK",gold)
        if(System.nanoTime()<messageUntil&&message.isNotBlank()) drawText(canvas,message,0.5f,0.93f,0.018f,gold,Paint.Align.CENTER)
    }

    private fun drawLevelSelect(canvas: Canvas) {
        drawText(canvas,CampaignWorlds.worldName(selectedWorld).uppercase(),0.5f,0.125f,0.055f,Color.WHITE,Paint.Align.CENTER)
        val first=CampaignWorlds.firstLevelForWorld(selectedWorld); val last=min(first+9,levels.size)
        drawText(canvas,"LEVELS $first-$last  •  UNLOCKED "+prefs.getInt("unlocked_level",1).coerceIn(1,levels.size)+"/"+levels.size,0.5f,0.165f,0.020f,cyan,Paint.Align.CENTER)
        for(mode in 1..3){
            val left=0.055f+(mode-1)*0.31f
            val label=(if(difficulty==mode) "✓ " else "")+when(mode){1->"★☆☆ EASY";2->"★★☆ NORMAL";else->"★★★ HARD"}
            drawButton(canvas,left,0.195f,left+0.28f,0.245f,label,if(difficulty==mode) gold else cyan)
        }
        val unlocked=prefs.getInt("unlocked_level",1).coerceIn(1,levels.size)
        for(local in 1..10){
            val id=first+local-1;if(id>last) continue
            val col=(local-1)%4;val row=(local-1)/4;val left=0.04f+col*0.24f;val top=0.29f+row*0.135f
            val active=id<=unlocked;val best=prefs.getInt(bestStarsKey(id),0).coerceIn(0,3)
            drawButton(canvas,left,top,left+0.22f,top+0.085f,local.toString().padStart(2,'0')+" "+"★".repeat(best)+"☆".repeat(3-best),if(active) cyan else muted,active)
        }
        drawText(canvas,"STARS = TIME + COMBO / TARGET CLEAR",0.5f,0.735f,0.014f,muted,Paint.Align.CENTER)
        drawButton(canvas,0.22f,0.83f,0.78f,0.90f,"BACK TO WORLDS",gold)
    }

    private fun drawGame(canvas: Canvas) {
        if (wallGuardMode) {
            drawWallGuardGame(canvas)
            return
        }
        drawText(canvas,"BOUNCE BLADE",0.07f,0.047f,0.025f,Color.WHITE)
        drawText(canvas,level.name.uppercase()+"  "+levelId+"/"+levels.size+"  •  "+"★".repeat(difficulty),0.48f,0.047f,0.020f,cyan,Paint.Align.CENTER)
        drawText(canvas,"BOUNCES "+bouncesLeft,0.78f,0.047f,0.019f,gold,Paint.Align.RIGHT)
        drawButton(canvas,0.83f,0.065f,0.94f,0.115f,"Ⅱ",pink)
        val timeLeft=GameRules.timeLimitSeconds(levelId,difficulty)-levelElapsedSeconds
        drawText(canvas,"SCORE "+score,0.07f,0.092f,0.018f,gold)
        drawText(canvas,"TIME "+formatTime(timeLeft),0.47f,0.092f,0.018f,if(levelElapsedSeconds<=GameRules.targetTimeSeconds(levelId,difficulty)) cyan else pink,Paint.Align.CENTER)
        drawAsset(canvas, "UI Elements/Timer Icon.png", RectF(viewW * 0.365f, viewH * 0.077f, viewW * 0.405f, viewH * 0.11f), 240)
        drawText(canvas,"COMBO x"+comboCount,0.80f,0.092f,0.018f,pink,Paint.Align.RIGHT)
        drawAsset(canvas, "UI Elements/Combo Icon.png", RectF(viewW * 0.815f, viewH * 0.077f, viewW * 0.855f, viewH * 0.11f), 240)
        val rect = RectF(viewW * 0.05f, viewH * 0.13f, viewW * 0.95f, viewH * 0.94f)
        drawAsset(canvas, "Backgrounds & Environments/Gameplay Background.png", rect, 220)
        drawAsset(canvas, "UI Elements/UI Panel.png", RectF(viewW * 0.03f, viewH * 0.012f, viewW * 0.97f, viewH * 0.125f), 105)
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
        if(level.requiresSwitch) drawSwitch(canvas)
        drawExit(canvas)
        drawTurretProjectiles(canvas)
        for(crate in crates) if(crate.alive) drawCrate(canvas,crate)
        for((index,enemy) in enemies.withIndex()) if(enemy.alive) drawEnemy(canvas,enemy,index)
        drawBladeTrail(canvas)
        drawParticles(canvas)
        val nowNanos = System.nanoTime()
        if (nowNanos < impactUntil) {
            val alpha = ((impactUntil - nowNanos) * 255L / 320_000_000L).toInt().coerceIn(0, 255)
            drawAssetCentered(canvas, "Effects & Particles/Explosion.png", lastImpactX, lastImpactY, minDim * 0.11f, alpha)
        }
        if (comboCount >= 2 && comboWindow > 0f) {
            drawAsset(canvas, "Effects & Particles/Combo Text.png", RectF(viewW * 0.34f, viewH * 0.82f, viewW * 0.66f, viewH * 0.89f), 240)
        }
        if (aiming && !flying) drawAim(canvas)
        val bladePx = bladeX * viewW
        val bladePy = bladeY * viewH
        canvas.save()
        canvas.rotate(angle, bladePx, bladePy)
        val bladeSize = minDim * 0.078f
        drawAsset(canvas, if (flying) "Gameplay Sprites/Blade.png" else "Gameplay Sprites/Blade Idle.png",
            RectF(bladePx - bladeSize / 2f, bladePy - bladeSize / 2f, bladePx + bladeSize / 2f, bladePy + bladeSize / 2f), 255)
        canvas.restore()

        val targetsLeft = enemies.count { it.alive } + crates.count { it.alive }
        val objective = when {
            level.requiresAllTargets && targetsLeft > 0 -> "TARGETS LEFT: " + targetsLeft
            level.requiresSwitch && !switchActivated -> "HIT THE GOLD SWITCH"
            else -> "EXIT OPEN  •  REACH THE PORTAL"
        }
        drawText(canvas, objective, 0.5f, 0.112f, 0.019f,
            if (switchActivated && targetsLeft == 0) cyan else muted, Paint.Align.CENTER)
        if (!flying && !aiming) {
            drawText(canvas, if (shotsUsed == 0) "DRAG BACK THEN RELEASE TO LAUNCH" else "DRAG TO PLAN YOUR NEXT SHOT",
                0.5f, 0.19f, 0.018f, cyan, Paint.Align.CENTER)
        }
        drawMeter(canvas)
        if (System.nanoTime() < messageUntil && message.isNotBlank()) {
            drawText(canvas, message, 0.5f, 0.855f, 0.019f, gold, Paint.Align.CENTER)
        }
    }


    private fun drawWallGuardGame(canvas: Canvas) {
        drawText(canvas, "BOUNCE BLADE", 0.07f, 0.047f, 0.025f, Color.WHITE)
        drawText(canvas, "LEVEL " + levelId + "/" + levels.size, 0.49f, 0.047f, 0.022f, cyan, Paint.Align.CENTER)
        drawButton(canvas, 0.83f, 0.065f, 0.94f, 0.115f, "Ⅱ", pink)
        drawText(canvas, "SCORE $score", 0.07f, 0.092f, 0.019f, gold)
        drawText(canvas, "TARGETS " + enemies.count { it.alive }, 0.50f, 0.092f, 0.019f, pink, Paint.Align.CENTER)
        drawText(canvas, "SAVE STREAK $comboCount", 0.92f, 0.092f, 0.016f, cyan, Paint.Align.RIGHT)

        val arena = RectF(viewW * 0.035f, viewH * 0.135f, viewW * 0.965f, viewH * 0.94f)
        drawAsset(canvas, "Backgrounds & Environments/Gameplay Background.png", arena, 230)
        drawAsset(canvas, "UI Elements/UI Panel.png",
            RectF(viewW * 0.025f, viewH * 0.012f, viewW * 0.975f, viewH * 0.125f), 115)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2f)
        paint.color = cyan
        paint.setShadowLayer(density * 10f, 0f, 0f, cyan)
        canvas.drawRoundRect(arena, density * 14f, density * 14f, paint)
        paint.clearShadowLayer()
        paint.color = grid
        paint.strokeWidth = max(1f, density * 0.7f)
        for (i in 1..5) {
            val x = viewW * (0.035f + 0.155f * i)
            canvas.drawLine(x, arena.top, x, arena.bottom, paint)
        }
        for (i in 1..8) {
            val y = viewH * (0.135f + 0.1f * i)
            canvas.drawLine(arena.left, y, arena.right, y, paint)
        }
        paint.style = Paint.Style.FILL

        drawBladeTrail(canvas)
        drawParticles(canvas)
        val now = System.nanoTime()
        if (now < impactUntil) {
            val alpha = ((impactUntil - now) * 255L / 320_000_000L).toInt().coerceIn(0, 255)
            drawAssetCentered(canvas, "Effects & Particles/Explosion.png", lastImpactX, lastImpactY, minDim * 0.11f, alpha)
        }
        for ((index, enemy) in enemies.withIndex()) if (enemy.alive) drawEnemy(canvas, enemy, index)

        // Player-controlled barrier occupying a short segment of the left wall.
        val paddleHalf = guardPaddleHalf()
        val paddleCenter = guardPaddleY * viewH
        val paddle = RectF(
            viewW * 0.043f,
            paddleCenter - paddleHalf,
            viewW * 0.098f,
            paddleCenter + paddleHalf
        )
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(105, Color.red(cyan), Color.green(cyan), Color.blue(cyan))
        paint.setShadowLayer(density * 13f, 0f, 0f, cyan)
        canvas.drawRoundRect(paddle, density * 8f, density * 8f, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2.5f)
        paint.color = cyan
        canvas.drawRoundRect(paddle, density * 8f, density * 8f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(
            RectF(paddle.centerX - density, paddle.top + density * 7f,
                paddle.centerX + density, paddle.bottom - density * 7f),
            density, density, paint
        )

        val bladePx = bladeX * viewW
        val bladePy = bladeY * viewH
        canvas.save()
        canvas.rotate(angle, bladePx, bladePy)
        val bladeSize = minDim * 0.078f
        drawAsset(canvas, if (flying) "Gameplay Sprites/Blade.png" else "Gameplay Sprites/Blade Idle.png",
            RectF(bladePx - bladeSize / 2f, bladePy - bladeSize / 2f,
                bladePx + bladeSize / 2f, bladePy + bladeSize / 2f), 255)
        canvas.restore()

        drawText(canvas, "DRAG UP OR DOWN TO MOVE THE LEFT SHIELD",
            0.5f, 0.965f, 0.018f, cyan, Paint.Align.CENTER)
        if (System.nanoTime() < messageUntil && message.isNotBlank()) {
            drawText(canvas, message, 0.5f, 0.885f, 0.020f, gold, Paint.Align.CENTER)
        }
    }

    private fun drawHazards(canvas: Canvas) {
        for((index,hazard) in level.hazards.withIndex()) {
            if(hazard.type=="power_up" && index in collectedPowerUps) continue
            val c=hazardCenter(hazard,index);val x=c.x*viewW;val y=c.y*viewH
            val hw=hazard.width*viewW/2f;val h=max(hazard.height*viewH,minDim*0.018f)
            when(hazard.type){
                "teleporter"->{paint.style=Paint.Style.STROKE;paint.strokeWidth=max(2f,density*2f);paint.color=cyan;paint.setShadowLayer(density*10f,0f,0f,cyan);canvas.drawCircle(x,y,max(minDim*0.025f,hw),paint);canvas.drawCircle(x,y,max(minDim*0.014f,hw*0.55f),paint);paint.clearShadowLayer();drawAssetCentered(canvas,"Gameplay Sprites/Portal.png",x,y,max(minDim*0.075f,hw*2.0f))}
                "power_up"->{paint.style=Paint.Style.FILL;paint.color=gold;paint.setShadowLayer(density*9f,0f,0f,gold);canvas.drawCircle(x,y,max(h,hw)*1.18f,paint);paint.clearShadowLayer();drawAssetCentered(canvas,"UI Elements/Star Icon.png",x,y,max(h,hw)*2.2f)}
                else->{paint.style=Paint.Style.FILL;paint.color=when(hazard.type){"sticky_wall"->Color.rgb(95,48,145);"moving_hazard"->Color.rgb(80,65,22);else->Color.rgb(80,22,51)};canvas.drawRoundRect(x-hw,y-h/2f,x+hw,y+h/2f,density*4f,density*4f,paint);paint.style=Paint.Style.STROKE;paint.color=when(hazard.type){"sticky_wall"->Color.rgb(190,105,255);"moving_hazard"->gold;else->pink};paint.strokeWidth=density*when(hazard.type){"laser"->3.5f;"sticky_wall"->4f;else->3f};paint.setShadowLayer(density*7f,0f,0f,paint.color);canvas.drawLine(x-hw,y,x+hw,y,paint);paint.clearShadowLayer();if(hazard.type.contains("spike")){paint.style=Paint.Style.FILL;paint.color=pink;val count=max(4,(hazard.width*22f).toInt());for(i in 0 until count){val sx=x-hw+2f*hw*i/count;val nx=min(x+hw,sx+max(density*6f,2f*hw/count));val p=Path();p.moveTo(sx,y+h/2f);p.lineTo((sx+nx)/2f,y-h/2f);p.lineTo(nx,y+h/2f);p.close();canvas.drawPath(p,paint)}}else if(hazard.type=="moving_hazard"){paint.style=Paint.Style.FILL;paint.color=gold;canvas.drawCircle(x,y,density*5f,paint)}}
            }
        }
        paint.style=Paint.Style.FILL
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
        paint.clearShadowLayer()
        drawAssetCentered(canvas, "Gameplay Sprites/Switch.png", x, y, radius * 2.25f)
        drawText(canvas, if (switchActivated) "ON" else "SWITCH", x / viewW, (y + radius + density * 15f) / viewH,
            0.015f, if (switchActivated) cyan else gold, Paint.Align.CENTER)
        paint.style = Paint.Style.FILL
    }

    private fun drawExit(canvas: Canvas) {
        val x = level.exit.x * viewW
        val y = level.exit.y * viewH
        val ready = GameRules.exitIsUnlocked(enemies.count { it.alive }, crates.count { it.alive }, switchActivated, level.requiresAllTargets, level.requiresSwitch)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 3f)
        paint.color = if (ready) cyan else muted
        paint.setShadowLayer(if (ready) density * 15f else density * 3f, 0f, 0f, paint.color)
        canvas.drawOval(x - minDim * 0.038f, y - minDim * 0.052f, x + minDim * 0.038f, y + minDim * 0.052f, paint)
        paint.clearShadowLayer()
        drawAssetCentered(canvas, "Gameplay Sprites/Portal.png", x, y, minDim * 0.12f, if (ready) 255 else 150)
        drawText(canvas, if (ready) "EXIT" else "LOCKED", x / viewW, (y + minDim * 0.065f) / viewH, 0.014f,
            if (ready) cyan else muted, Paint.Align.CENTER)
        paint.style = Paint.Style.FILL
    }

    private fun drawEnemy(canvas: Canvas, enemy: EnemyState, index: Int) {
        val c = enemyCenter(enemy, index)
        val x = c.x * viewW; val y = c.y * viewH
        val r = minDim * when (enemy.kind) { 1 -> 0.033f; 2 -> if (enemy.canSplit) 0.031f else 0.020f; 3 -> 0.052f; else -> 0.030f }
        val path = when (enemy.kind) {
            1 -> "Gameplay Sprites/Enemy Turret.png"
            2 -> "Gameplay Sprites/Enemy Slime.png"
            3 -> "Gameplay Sprites/Enemy Turret.png"
            else -> "Gameplay Sprites/Enemy Drone.png"
        }
        val drawSize = if (enemy.kind == 3) r * 3.2f else r * 2.35f
        if (enemy.kind == 3) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = max(2f, density * 2f); paint.color = if (enemy.health > 1) gold else cyan
            paint.setShadowLayer(density * 9f, 0f, 0f, paint.color)
            canvas.drawCircle(x, y, drawSize * 0.61f, paint)
            paint.clearShadowLayer()
        }
        drawAssetCentered(canvas, path, x, y, drawSize)
        if (enemy.kind == 3 && enemy.health > 1) drawText(canvas, enemy.health.toString(), x / viewW,
            (y + drawSize * 0.63f) / viewH, 0.017f, gold, Paint.Align.CENTER)
        paint.style = Paint.Style.FILL
    }

    private fun drawCrate(canvas: Canvas, crate: CrateState) {
        val x = crate.x * viewW; val y = crate.y * viewH
        val r = minDim * 0.034f
        drawAsset(canvas, "Gameplay Sprites/Crate.png", RectF(x - r * 1.18f, y - r * 1.18f, x + r * 1.18f, y + r * 1.18f), 255)
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
        drawTrajectoryPreview(canvas, bladeX * viewW, bladeY * viewH, dx / magnitude, dy / magnitude)
        drawText(canvas, "RELEASE TO LAUNCH", 0.5f, 0.19f, 0.019f, cyan, Paint.Align.CENTER)
    }

    /** Projects a launch with the same wall limits as the live simulation. */
    private fun drawTrajectoryPreview(canvas: Canvas, startX: Float, startY: Float, ux: Float, uy: Float) {
        val radius = minDim * 0.022f
        val left = viewW * 0.075f + radius
        val right = viewW * 0.925f - radius
        val top = viewH * 0.145f + radius
        val bottom = viewH * 0.925f - radius
        val stride = minDim * 0.024f
        var x = startX
        var y = startY
        var dirX = ux
        var dirY = uy
        var previousX = x
        var previousY = y
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = max(1.5f, density * 2f)
        for (i in 0 until 48) {
            x += dirX * stride
            y += dirY * stride
            var wallHit = false
            if (x < left) { x = left; dirX = abs(dirX); wallHit = true }
            else if (x > right) { x = right; dirX = -abs(dirX); wallHit = true }
            if (y < top) { y = top; dirY = abs(dirY); wallHit = true }
            else if (y > bottom) { y = bottom; dirY = -abs(dirY); wallHit = true }
            val enemyHit = enemies.withIndex().any { (enemyIndex, enemy) ->
                val c=enemyCenter(enemy,enemyIndex)
                enemy.alive && distanceSquared(x,y,c.x*viewW,c.y*viewH)<(radius+minDim*0.027f)*(radius+minDim*0.027f)
            }
            val crateHit = crates.any {
                it.alive && abs(x - it.x * viewW) < radius + minDim * 0.035f &&
                    abs(y - it.y * viewH) < radius + minDim * 0.035f
            }
            val hazardHit = level.hazards.withIndex().any { (hazardIndex, hazard) ->
                val center = hazardCenter(hazard, hazardIndex)
                val hx = center.x * viewW
                val hy = center.y * viewH
                val halfWidth = hazard.width * viewW / 2f
                val halfHeight = max(hazard.height * viewH, minDim * 0.018f) / 2f
                x + radius > hx - halfWidth && x - radius < hx + halfWidth &&
                    y + radius > hy - halfHeight && y - radius < hy + halfHeight
            }
            val collision = enemyHit || crateHit || hazardHit
            val alpha = (140 - i * 2).coerceAtLeast(30)
            paint.color = when {
                hazardHit || enemyHit -> Color.argb(alpha, 255, 65, 150)
                crateHit || wallHit -> Color.argb(alpha, 255, 199, 83)
                else -> Color.argb(alpha, 45, 235, 255)
            }
            canvas.drawLine(previousX, previousY, x, y, paint)
            if (i % 3 == 0) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, density * (if (collision) 3f else 2f), paint)
                paint.style = Paint.Style.STROKE
            }
            previousX = x
            previousY = y
            if (collision) break
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawBladeTrail(canvas: Canvas) {
        if (bladeTrail.size < 2) return
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 1 until bladeTrail.size) {
            val alpha = (35 + 175 * i / bladeTrail.size).coerceIn(0, 210)
            paint.color = Color.argb(alpha, Color.red(cyan), Color.green(cyan), Color.blue(cyan))
            paint.strokeWidth = density * (1f + 4f * i / bladeTrail.size)
            val a = bladeTrail[i - 1]
            val b = bladeTrail[i]
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
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

    private fun drawTurretProjectiles(canvas: Canvas) {
        paint.style=Paint.Style.FILL
        for(s in turretProjectiles){paint.color=pink;paint.setShadowLayer(density*6f,0f,0f,pink);canvas.drawCircle(s.x,s.y,minDim*0.009f,paint);paint.clearShadowLayer()}
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
        if (screen == GameScreen.WON) drawAsset(canvas, "Backgrounds & Environments/Victory Background.png", RectF(0f, 0f, viewW, viewH), 238)
        else if (screen == GameScreen.FAILED) drawAsset(canvas, "Backgrounds & Environments/Game Over Background.png", RectF(0f, 0f, viewW, viewH), 238)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(228, 3, 6, 18)
        canvas.drawRoundRect(viewW * 0.08f, viewH * 0.30f, viewW * 0.92f, viewH * 0.75f, density * 18f, density * 18f, paint)
        when (screen) {
            GameScreen.WON -> drawAsset(canvas, "Banners & Text/Level Complete Banner.png", RectF(viewW * 0.20f, viewH * 0.315f, viewW * 0.80f, viewH * 0.395f), 255)
            GameScreen.FAILED -> drawAsset(canvas, "Banners & Text/Game Over Banner.png", RectF(viewW * 0.20f, viewH * 0.315f, viewW * 0.80f, viewH * 0.395f), 255)
            else -> drawText(canvas, title, 0.5f, 0.365f, 0.046f, if (title.contains("CLEAR")) cyan else pink, Paint.Align.CENTER)
        }
        drawText(canvas, subtitle, 0.5f, 0.415f, 0.018f, Color.WHITE, Paint.Align.CENTER)
        if (screen == GameScreen.WON) {
            drawAssetCentered(canvas, "UI Elements/Star Icon.png", viewW * 0.35f, viewH * 0.465f, minDim * 0.04f)
            drawText(canvas, "★".repeat(earnedStars) + "☆".repeat(3 - earnedStars), 0.5f, 0.465f, 0.050f, gold, Paint.Align.CENTER)
            drawText(canvas, "SCORE " + score + " • TIME " + formatTime(levelElapsedSeconds) + " • BEST COMBO x" + bestCombo, 0.5f, 0.490f, 0.014f, muted, Paint.Align.CENTER)
            val bestShots = prefs.getInt(bestShotsKey(levelId), shotsUsed)
            drawText(canvas, "BEST: " + prefs.getInt(bestStarsKey(levelId), earnedStars) + "/3 STARS • FEWEST SHOTS: " + bestShots, 0.5f, 0.510f, 0.013f, muted, Paint.Align.CENTER)
        }
    }

    private fun drawSettings(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "SETTINGS", 0.5f, 0.29f, 0.07f, Color.WHITE, Paint.Align.CENTER)
        drawButton(canvas, 0.15f, 0.39f, 0.85f, 0.46f, "SOUND: " + if (soundEnabled) "ON" else "OFF", cyan)
        drawButton(canvas, 0.15f, 0.48f, 0.85f, 0.55f, "VIBRATION: " + if (vibrationEnabled) "ON" else "OFF", gold)
        drawButton(canvas, 0.15f, 0.57f, 0.85f, 0.64f, "THEME: " + themeName(), pink)
        drawButton(canvas, 0.15f, 0.69f, 0.85f, 0.76f, "BACK", cyan)
        drawText(canvas, "Tap THEME to cycle through Cyan, Green and Violet.", 0.5f, 0.83f, 0.018f, muted, Paint.Align.CENTER)
        drawText(canvas, "Settings are saved on this device.", 0.5f, 0.87f, 0.018f, muted, Paint.Align.CENTER)
    }

    private fun drawDifficulty(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "CHOOSE DIFFICULTY", 0.5f, 0.30f, 0.055f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "Harder modes use a faster blade and a smaller shield.", 0.5f, 0.36f, 0.018f, muted, Paint.Align.CENTER)
        val labels = arrayOf("★☆☆  EASY  •  BIG SHIELD", "★★☆  NORMAL", "★★★  HARD  •  SMALL SHIELD")
        val accents = intArrayOf(cyan, gold, pink)
        for (mode in 1..3) {
            val top = 0.405f + (mode - 1) * 0.11f
            val label = (if (difficulty == mode) "✓  " else "") + labels[mode - 1]
            drawButton(canvas, 0.13f, top, 0.87f, top + 0.078f, label, accents[mode - 1])
        }
        drawButton(canvas, 0.22f, 0.755f, 0.78f, 0.83f, "BACK", gold)
    }

    private fun drawHelp(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "HELP & CONTROLS", 0.5f, 0.29f, 0.055f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "1. The blade launches automatically from the left.", 0.12f, 0.385f, 0.018f, cyan)
        drawText(canvas, "2. Drag anywhere up/down to move your shield.", 0.12f, 0.445f, 0.018f, Color.WHITE)
        drawText(canvas, "3. The blade bounces back after it hits a target.", 0.12f, 0.505f, 0.017f, Color.WHITE)
        drawText(canvas, "4. Catch it on the left or you lose immediately.", 0.12f, 0.565f, 0.018f, pink)
        drawText(canvas, "5. Hit every enemy to clear the level.", 0.12f, 0.625f, 0.018f, Color.WHITE)
        drawText(canvas, "Easy gives a larger shield; Hard gives a smaller one.", 0.12f, 0.685f, 0.016f, gold)
        drawText(canvas, "Tip: move early to meet the returning blade.", 0.5f, 0.735f, 0.017f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.22f, 0.79f, 0.78f, 0.86f, "BACK", cyan)
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
        paint.color = if (enabled) Color.argb(142, 12, 23, 42) else Color.argb(100, 15, 21, 34)
        canvas.drawRoundRect(rect, density * 11f, density * 11f, paint)
        drawAsset(canvas, "UI Elements/Button.png", rect, if (enabled) 155 else 90)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, density * 1.5f)
        paint.color = if (enabled) color else muted
        if (enabled) paint.setShadowLayer(density * 7f, 0f, 0f, color)
        canvas.drawRoundRect(rect, density * 11f, density * 11f, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        val iconPath = when {
            label == "Ⅱ" -> "UI Elements/Pause Icon.png"
            label.contains("BACK") -> "UI Elements/Back Icon.png"
            label.startsWith("PLAY") || label == "RESUME" || label.startsWith("NEXT LEVEL") -> "UI Elements/Play Icon.png"
            label.startsWith("RESTART") || label == "RETRY" -> "UI Elements/Restart Icon.png"
            label.contains("LEVEL SELECT") || label == "MAIN MENU" -> "UI Elements/Home Icon.png"
            else -> null
        }
        if (iconPath != null) {
            val iconSize = min(rect.height() * 0.56f, minDim * 0.042f)
            val iconX = if (label == "Ⅱ") rect.centerX() else rect.left + iconSize * 0.82f
            drawAssetCentered(canvas, iconPath, iconX, rect.centerY(), iconSize, if (enabled) 255 else 120)
            if (label == "Ⅱ") return
        }
        val shift = if (iconPath == null) 0f else minDim * 0.018f
        drawText(canvas, label, (left + right) / 2f + shift / viewW, (top + bottom) / 2f + 0.010f,
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

    private fun hazardCenter(hazard: HazardState, index: Int): PointF {
        if (!hazard.type.contains("moving")) return PointF(hazard.x, hazard.y)
        val allowedMin = hazard.width / 2f + 0.06f
        val allowedMax = 0.94f - hazard.width / 2f
        val drift = sin(animationTime * 1.7f + index * 1.3f) * min(0.15f, hazard.width * 0.35f)
        return PointF((hazard.x + drift).coerceIn(allowedMin, max(allowedMin, allowedMax)), hazard.y)
    }

    private fun totalBestStarsAfter(updatedLevelId: Int, updatedValue: Int): Int =
        (1..levels.size).sumOf { id ->
            if (id == updatedLevelId) updatedValue else prefs.getInt(bestStarsKey(id), 0)
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
                if (screen != GameScreen.PLAYING) playSfx("UI & Feedback SFX/Button Click.wav")
                when (screen) {
                    GameScreen.MENU -> {
                        when {
                            inRect(nx, ny, 0.18f, 0.52f, 0.82f, 0.585f) -> screen = GameScreen.DIFFICULTY
                            inRect(nx, ny, 0.18f, 0.595f, 0.82f, 0.66f) -> screen = GameScreen.WORLDS
                            inRect(nx, ny, 0.18f, 0.67f, 0.82f, 0.735f) -> screen = GameScreen.HELP
                            inRect(nx, ny, 0.18f, 0.745f, 0.82f, 0.81f) -> screen = GameScreen.SETTINGS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.WORLDS -> {
                        if(inRect(nx,ny,0.22f,0.83f,0.78f,0.90f)) screen=GameScreen.MENU else {
                            val unlocked=prefs.getInt("unlocked_level",1).coerceIn(1,levels.size)
                            for(w in 0 until CampaignWorlds.WORLD_COUNT){val top=0.375f+w*0.088f;if(inRect(nx,ny,0.12f,top,0.88f,top+0.066f)){if(unlocked>=CampaignWorlds.firstLevelForWorld(w)){selectedWorld=w;screen=GameScreen.LEVELS}else showMessage("CLEAR EARLIER WORLDS FIRST");break}}
                        };invalidate();return true
                    }
                    GameScreen.LEVELS -> {
                        if(inRect(nx,ny,0.22f,0.83f,0.78f,0.90f)) screen=GameScreen.WORLDS else {
                            var changed=false
                            for(mode in 1..3){val left=0.055f+(mode-1)*0.31f;if(inRect(nx,ny,left,0.195f,left+0.28f,0.245f)){difficulty=mode;prefs.edit().putInt("difficulty_stars",difficulty).apply();changed=true;break}}
                            if(!changed){val first=CampaignWorlds.firstLevelForWorld(selectedWorld);for(local in 1..10){val id=first+local-1;if(id>levels.size)continue;val col=(local-1)%4;val row=(local-1)/4;val left=0.04f+col*0.24f;val top=0.29f+row*0.135f;if(inRect(nx,ny,left,top,left+0.22f,top+0.085f)){val unlocked=prefs.getInt("unlocked_level",1).coerceIn(1,levels.size);if(id<=unlocked)startLevel(id)else showMessage("LOCKED");break}}}
                        };invalidate();return true
                    }
                    GameScreen.SETTINGS -> {
                        when {
                            inRect(nx, ny, 0.15f, 0.39f, 0.85f, 0.46f) -> {
                                soundEnabled = !soundEnabled
                                prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
                            }
                            inRect(nx, ny, 0.15f, 0.48f, 0.85f, 0.55f) -> {
                                vibrationEnabled = !vibrationEnabled
                                prefs.edit().putBoolean("vibration_enabled", vibrationEnabled).apply()
                            }
                            inRect(nx, ny, 0.15f, 0.57f, 0.85f, 0.64f) -> {
                                themeId = (themeId + 1) % 3
                                prefs.edit().putInt("theme_id", themeId).apply()
                                applyTheme()
                            }
                            inRect(nx, ny, 0.15f, 0.69f, 0.85f, 0.76f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.DIFFICULTY -> {
                        when {
                            inRect(nx, ny, 0.13f, 0.405f, 0.87f, 0.483f) -> {
                                difficulty = 1
                                prefs.edit().putInt("difficulty_stars", difficulty).apply()
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.13f, 0.515f, 0.87f, 0.593f) -> {
                                difficulty = 2
                                prefs.edit().putInt("difficulty_stars", difficulty).apply()
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.13f, 0.625f, 0.87f, 0.703f) -> {
                                difficulty = 3
                                prefs.edit().putInt("difficulty_stars", difficulty).apply()
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.22f, 0.755f, 0.78f, 0.83f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.HELP -> {
                        if (inRect(nx, ny, 0.22f, 0.79f, 0.78f, 0.86f)) screen = GameScreen.MENU
                        invalidate()
                        return true
                    }
                    GameScreen.PAUSED -> {
                        when {
                            inRect(nx, ny, 0.24f, 0.43f, 0.76f, 0.51f) -> {
                                screen = GameScreen.PLAYING
                                playSfx("UI & Feedback SFX/Unpause.wav")
                                lastFrame = System.nanoTime()
                            }
                            inRect(nx, ny, 0.24f, 0.54f, 0.76f, 0.62f) -> {
                                startLevel(levelId)
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
                            inRect(nx, ny, 0.22f, 0.63f, 0.78f, 0.71f) -> { selectedWorld=CampaignWorlds.indexForLevel(levelId);screen=GameScreen.LEVELS }
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.FAILED -> {
                        when {
                            inRect(nx, ny, 0.22f, 0.51f, 0.78f, 0.60f) -> startLevel(levelId)
                            inRect(nx, ny, 0.22f, 0.63f, 0.78f, 0.71f) -> { selectedWorld=CampaignWorlds.indexForLevel(levelId);screen=GameScreen.LEVELS }
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.CAMPAIGN_COMPLETE -> {
                        when {
                            inRect(nx, ny, 0.20f, 0.57f, 0.80f, 0.66f) -> startLevel(1)
                            inRect(nx, ny, 0.20f, 0.69f, 0.80f, 0.78f) -> { selectedWorld=CampaignWorlds.indexForLevel(levelId);screen=GameScreen.LEVELS }
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.PLAYING -> {
                        if (inRect(nx, ny, 0.81f, 0.065f, 0.94f, 0.115f)) {
                            screen = GameScreen.PAUSED
                            slowMotion = false
                            playSfx("UI & Feedback SFX/Pause.wav")
                            invalidate()
                            return true
                        }
                        if (wallGuardMode) {
                            guardPaddleY = clampGuardPaddleY(ny)
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
                if (wallGuardMode && screen == GameScreen.PLAYING) {
                    guardPaddleY = clampGuardPaddleY(ny)
                    invalidate()
                    return true
                }
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
                        shotsUsed += 1
                        bladeTrail.clear()
                        addBurst(bladeX * viewW, bladeY * viewH, cyan, 7)
                        flying = true
                        flightTime = 0f
                        lastFrame = System.nanoTime()
                        playSfx("Core Gameplay SFX/Blade Launch.wav", ToneGenerator.TONE_PROP_BEEP)
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
    }
}

