package com.angmarx.bounceblade

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContentValues
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
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import java.io.File
import java.io.FileOutputStream
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
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
    var x: Float, var y: Float, var alive: Boolean = true, val kind: Int = 0,
    var health: Int = 1, var fireCooldown: Float = 1.6f, val canSplit: Boolean = true,
    var jumpTime: Float = 0f, var jumpCooldown: Float = 1.4f
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
private data class PickupState(val type: String, val x: Float, val y: Float, var active: Boolean = true)
private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Int,
    val radius: Float
)
private enum class GameScreen { MENU, WORLDS, LEVELS, PLAYING, PAUSED, SETTINGS, HELP, DIFFICULTY, SHOP, FEATURES, MODES, CHALLENGES, ACHIEVEMENTS, STATS, CUSTOMIZE, EDITOR, ACCESSIBILITY, JUKEBOX, EXTRAS, LOCAL_SCORES, REPLAYS, PHOTO_MODE, SEASONAL, ONLINE_FEATURES, WON, FAILED, CAMPAIGN_COMPLETE }

private data class ShopItem(
    val id: String,
    val title: String,
    val description: String,
    val baseCost: Int,
    val maxLevel: Int,
    val iconPath: String
)

private class GameView(context: Context) : View(context) {
    private val prefs = context.getSharedPreferences("bounce_blade_save", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val minDim get() = min(viewW, viewH)
    private val wallGuardMode = true
    private var guardPaddleY = 0.5f
    private var secondGuardPaddleY = 0.5f
    private var primaryPointerId = -1
    private var secondPointerId = -1
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
    private var shopReturnScreen = GameScreen.MENU
    private var gameMode = prefs.getString("game_mode", "campaign") ?: "campaign"
    private var bladeSkin = prefs.getInt("blade_skin", 0).coerceIn(0, 5)
    private var shieldSkin = prefs.getInt("shield_skin", 0).coerceIn(0, 5)
    private var enemySkin = prefs.getInt("enemy_skin", 0).coerceIn(0, 3)
    private var highContrast = prefs.getBoolean("high_contrast", false)
    private var colorblindMode = prefs.getBoolean("colorblind_mode", false)
    private var hapticLevel = prefs.getInt("haptic_level", 2).coerceIn(0, 3)
    private var keyMoveUp = prefs.getInt("key_move_up", KeyEvent.KEYCODE_DPAD_UP)
    private var keyMoveDown = prefs.getInt("key_move_down", KeyEvent.KEYCODE_DPAD_DOWN)
    private var keyPause = prefs.getInt("key_pause", KeyEvent.KEYCODE_SPACE)
    private var playerName = prefs.getString("player_name", "BLADE RUNNER") ?: "BLADE RUNNER"
    private var traceTimer = 0f
    private val runTrace = mutableListOf<PointF>()
    private var reducedMotion = prefs.getBoolean("reduced_motion", false)
    private var leftHandedMode = prefs.getBoolean("left_handed", false)
    private var oneHandedMode = prefs.getBoolean("one_handed_mode", false)
    private var persianLanguage = prefs.getBoolean("persian_language", false)
    private var largeUi = prefs.getBoolean("large_ui", false)
    private var batterySaver = prefs.getBoolean("battery_saver", false)
    private var dynamicDifficulty = prefs.getBoolean("dynamic_difficulty", true)
    private var jukeboxTrackId = prefs.getInt("jukebox_track", 0).coerceIn(0, 3)
    private var editorTargetCount = prefs.getInt("editor_target_count", 6).coerceIn(1, 12)
    private var editorHazardId = prefs.getInt("editor_hazard", 0).coerceIn(0, 5)
    private val pickups = mutableListOf<PickupState>()
    private val activeEffects = mutableMapOf<String, Float>()
    private var extraLifeUsed = false
    private var multiCatchCharges = 0
    private var hasLevelKey = false
    private val musicTracks = listOf(
        "Music & Ambience/Main Menu Theme.wav",
        "Music & Ambience/Gameplay Background.wav",
        "Music & Ambience/Victory Theme.wav",
        "Music & Ambience/Game Over Theme.wav"
    )
    private val editorHazards = listOf("NONE", "SPIKES", "LASER", "MOVING HAZARD", "PORTAL PAIR", "STICKY WALL")
    private var touchingAbility = false
    private var novaCooldownRemaining = 0f
    private var novaFlashUntil = 0L
    private var novaFlashX = 0f
    private var novaFlashY = 0f
    private var novaFlashRadius = 0f
    private var lastCoinsEarned = 0
    private val shopItems = listOf(
        ShopItem("nova", "NOVA PULSE", "Shockwave around the blade", 350, 1, "Effects & Particles/Explosion.png"),
        ShopItem("overdrive", "BLADE OVERDRIVE", "Faster blade rebounds", 300, 2, "Gameplay Sprites/Blade.png"),
        ShopItem("shield_mod", "SHIELD EXTENDER", "A wider safety shield", 250, 2, "UI Elements/UI Panel.png"),
        ShopItem("pulse_core", "PULSE CORE", "Wider pulse, shorter cooldown", 250, 2, "UI Elements/Combo Icon.png"),
        ShopItem("coin_magnet", "COIN MAGNET", "More coins per cleared level", 400, 2, "UI Elements/Star Icon.png")
    )
    private val shopCardSlots = listOf(
        floatArrayOf(0.035f, 0.255f, 0.322f, 0.465f),
        floatArrayOf(0.350f, 0.255f, 0.650f, 0.465f),
        floatArrayOf(0.678f, 0.255f, 0.965f, 0.465f),
        floatArrayOf(0.195f, 0.505f, 0.490f, 0.715f),
        floatArrayOf(0.510f, 0.505f, 0.805f, 0.715f)
    )
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
        isFocusableInTouchMode = true
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Bounce Blade game. Main menu."
        requestFocus()
    }

    fun handleBack(): Boolean {
        playSfx("UI & Feedback SFX/Button Back.wav")
        when (screen) {
            GameScreen.PLAYING -> screen = GameScreen.PAUSED
            GameScreen.PAUSED, GameScreen.SETTINGS, GameScreen.HELP, GameScreen.DIFFICULTY, GameScreen.FEATURES, GameScreen.MODES, GameScreen.CHALLENGES, GameScreen.ACHIEVEMENTS, GameScreen.STATS, GameScreen.CUSTOMIZE, GameScreen.EDITOR, GameScreen.ACCESSIBILITY, GameScreen.JUKEBOX, GameScreen.EXTRAS, GameScreen.LOCAL_SCORES, GameScreen.REPLAYS, GameScreen.PHOTO_MODE, GameScreen.SEASONAL, GameScreen.ONLINE_FEATURES -> screen = if (screen == GameScreen.FEATURES) GameScreen.MENU else if (screen == GameScreen.EXTRAS) GameScreen.FEATURES else GameScreen.EXTRAS
            GameScreen.SHOP -> {
                screen = shopReturnScreen
                if (screen == GameScreen.PLAYING) lastFrame = System.nanoTime()
            }
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
        contentDescription = when (screen) {
            GameScreen.MENU -> "Bounce Blade main menu. Campaign progress " + prefs.getInt("unlocked_level", 1) + " of " + levels.size + "."
            GameScreen.WORLDS -> "World selection. Choose an unlocked world."
            GameScreen.LEVELS -> "Stage selection for " + CampaignWorlds.worldName(selectedWorld) + "."
            GameScreen.PLAYING -> "Playing stage " + levelId + ". Score " + score + ". Drag vertically to move the shield."
            GameScreen.PAUSED -> "Game paused. Resume, restart, or return to the menu."
            GameScreen.WON -> "Stage complete. Score " + score + ". Stars earned " + earnedStars + "."
            GameScreen.FAILED -> "Run ended. " + message
            else -> "Bounce Blade " + screen.name.lowercase().replace('_', ' ') + "."
        }
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
                drawButton(canvas, 0.18f, 0.425f, 0.82f, 0.515f, "RESUME", cyan)
                drawButton(canvas, 0.18f, 0.535f, 0.82f, 0.625f, "RESTART LEVEL", gold)
                drawButton(canvas, 0.18f, 0.645f, 0.82f, 0.735f, "MAIN MENU", pink)
            }
            GameScreen.SETTINGS -> drawSettings(canvas)
            GameScreen.HELP -> drawHelp(canvas)
            GameScreen.DIFFICULTY -> drawDifficulty(canvas)
            GameScreen.SHOP -> drawShop(canvas)
            GameScreen.FEATURES -> drawFeatures(canvas)
            GameScreen.MODES -> drawModes(canvas)
            GameScreen.CHALLENGES -> drawChallenges(canvas)
            GameScreen.ACHIEVEMENTS -> drawAchievements(canvas)
            GameScreen.STATS -> drawStats(canvas)
            GameScreen.CUSTOMIZE -> drawCustomize(canvas)
            GameScreen.EDITOR -> drawEditor(canvas)
            GameScreen.ACCESSIBILITY -> drawAccessibility(canvas)
            GameScreen.JUKEBOX -> drawJukebox(canvas)
            GameScreen.EXTRAS -> drawExtras(canvas)
            GameScreen.LOCAL_SCORES -> drawLocalScores(canvas)
            GameScreen.REPLAYS -> drawReplays(canvas)
            GameScreen.PHOTO_MODE -> drawPhotoMode(canvas)
            GameScreen.SEASONAL -> drawSeasonal(canvas)
            GameScreen.ONLINE_FEATURES -> drawOnlineFeatures(canvas)
            GameScreen.WON -> {
                drawGame(canvas)
                drawOverlay(canvas, "LEVEL CLEARED", "Nice shot! The exit is open.")
                drawButton(canvas, 0.18f, 0.50f, 0.82f, 0.60f, "NEXT LEVEL", cyan)
                drawButton(canvas, 0.18f, 0.625f, 0.82f, 0.725f, "LEVEL SELECT", gold)
            }
            GameScreen.FAILED -> {
                drawGame(canvas)
                drawOverlay(canvas, "TRY AGAIN", message.ifBlank { "Reset and try a different angle." })
                drawButton(canvas, 0.18f, 0.50f, 0.82f, 0.60f, "RETRY", cyan)
                drawButton(canvas, 0.18f, 0.625f, 0.82f, 0.725f, "LEVEL SELECT", gold)
            }
            GameScreen.CAMPAIGN_COMPLETE -> {
                drawMenuBackdrop(canvas)
                drawAssetCover(canvas, "Backgrounds & Environments/Victory Background.png", RectF(0f, 0f, viewW, viewH), 238)
                drawAsset(canvas, "Banners & Text/Victory Banner.png", RectF(viewW * 0.12f, viewH * 0.27f, viewW * 0.88f, viewH * 0.43f), 255)
                drawText(canvas, "You conquered all ten worlds.", 0.5f, 0.48f, 0.024f, Color.WHITE, Paint.Align.CENTER)
                drawButton(canvas, 0.15f, 0.565f, 0.85f, 0.665f, "PLAY AGAIN", cyan)
                drawButton(canvas, 0.15f, 0.680f, 0.85f, 0.780f, "LEVEL SELECT", gold)
            }
        }
        if (screen == GameScreen.PLAYING || flying || particles.isNotEmpty() || turretProjectiles.isNotEmpty() || screen == GameScreen.JUKEBOX) postInvalidateDelayed(frameDelayMillis())
    }

    private fun frameDelayMillis(): Long {
        if (batterySaver) return 33L
        val refreshRate = display?.refreshRate ?: 60f
        return (1000f / refreshRate.coerceIn(30f, 144f)).toLong().coerceIn(6L, 33L)
    }

    private fun advanceFrame() {
        val now = System.nanoTime()
        val rawDt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.025f)
        lastFrame = now
        animationTime += rawDt
        if (screen == GameScreen.PLAYING) {
            traceTimer += rawDt
            if (traceTimer >= 0.06f && runTrace.size < 1000) {
                runTrace.add(PointF(bladeX.coerceIn(0f, 1f), bladeY.coerceIn(0f, 1f)))
                traceTimer = 0f
            }
        }
        if (screen == GameScreen.PLAYING) {
            val effectIterator = activeEffects.entries.iterator()
            while (effectIterator.hasNext()) {
                val entry = effectIterator.next()
                entry.setValue((entry.value - rawDt).coerceAtLeast(0f))
                if (entry.value <= 0f) effectIterator.remove()
            }
            levelElapsedSeconds += rawDt
            if (gameMode == "time_attack" && levelElapsedSeconds >= 60f) {
                fail("TIME ATTACK OVER — 60 SECONDS")
                return
            }
            comboWindow = (comboWindow - rawDt).coerceAtLeast(0f)
            if (comboWindow <= 0f) comboCount = 0
            teleportCooldown = (teleportCooldown - rawDt).coerceAtLeast(0f)
            novaCooldownRemaining = (novaCooldownRemaining - rawDt).coerceAtLeast(0f)
            if (wallGuardMode) updateHostileEnemies(rawDt)
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
            val timeScale = if ((slowMotion && slowMeter > 0f) || (activeEffects["slow_time"] ?: 0f) > 0f) 0.28f else 1f
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
        if (bestCombo > prefs.getInt("all_time_best_combo", 0)) prefs.edit().putInt("all_time_best_combo", bestCombo).apply()
        val multiplier = (if ((activeEffects["score_multiplier"] ?: 0f) > 0f) 2 else 1) * (if (comboCount >= 5) 2 else 1)
        score += points * comboCount * multiplier
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

    private fun enemyKindForCampaignLevel(id: Int, index: Int): Int {
        if (id <= 50) return enemyKindForLevel(id)
        val roster = when (id) {
            in 51..60 -> intArrayOf(8, 4, 0, 3)
            in 61..70 -> intArrayOf(5, 0, 8, 1)
            in 71..80 -> intArrayOf(6, 0, 2, 3)
            in 81..90 -> intArrayOf(7, 1, 5, 8)
            else -> intArrayOf(8, 4, 5, 6, 7)
        }
        return roster[index % roster.size]
    }

    private fun enemyHealthForLevel(id: Int, index: Int): Int = when {
        id in 91..100 -> 5 + (index % 3)
        id in 51..90 -> if (index % 4 == 0) 4 else 2
        id in 21..30 || id >= 41 -> 3
        else -> 1
    }

    private fun enemyCenter(enemy: EnemyState, index: Int): PointF {
        val d = when (enemy.kind) {
            0 -> PointF(sin(animationTime * 1.7f + index * 1.2f) * 0.014f, cos(animationTime * 1.3f + index) * 0.012f)
            2 -> PointF(sin(animationTime * 1.25f + index * 1.4f) * if (enemy.canSplit) 0.035f else 0.02f, cos(animationTime * 1.8f + index) * 0.02f)
            3 -> PointF(sin(animationTime * 0.75f + index) * 0.009f, 0f)
            else -> PointF(0f, 0f)
        }
        val jumpProgress = if (enemy.kind == 2 && enemy.jumpTime > 0f)
            (1f - enemy.jumpTime / 0.58f).coerceIn(0f, 1f) else 0f
        val jumpArc = if (enemy.kind == 2 && enemy.jumpTime > 0f)
            -sin(jumpProgress * 3.1415926f) * 0.075f else 0f
        return PointF(
            (enemy.x + d.x).coerceIn(0.1f, 0.9f),
            (enemy.y + d.y + jumpArc).coerceIn(0.17f, 0.84f)
        )
    }

    /**
     * Enemy AI for the active left-wall arcade mode.
     * Drones pursue the shield quickly, slimes make repeated lunging jumps,
     * and turrets advance slowly. Letting a hostile reach the left wall burns
     * the run, so the blade must destroy it before it breaks through.
     */
    private fun updateHostileEnemies(dt: Float) {
        if (screen != GameScreen.PLAYING || !wallGuardMode || dt <= 0f) return
        val breachX = 0.108f
        val targetY = guardPaddleY.coerceIn(0.18f, 0.82f)
        val adaptiveScale = if (dynamicDifficulty) 1f + min(prefs.getInt("total_clears", 0), 60) * 0.0025f else 1f
        val difficultyScale = (1f + (difficulty - 1) * 0.18f) * adaptiveScale

        for ((index, enemy) in enemies.withIndex()) {
            if (!enemy.alive) continue
            when (enemy.kind) {
                0 -> {
                    // Drones fly directly toward the player's shield and adjust
                    // their flight path to follow its current height.
                    val droneSpeed = (0.022f + min(levelId, 20) * 0.00045f) * difficultyScale
                    enemy.x -= droneSpeed * dt
                    val verticalStep = droneSpeed * 0.78f * dt
                    enemy.y += (targetY - enemy.y).coerceIn(-verticalStep, verticalStep)
                }
                2 -> {
                    // Slimes pause briefly, then leap left while homing vertically.
                    if (enemy.jumpTime <= 0f) {
                        enemy.jumpCooldown -= dt
                        if (enemy.jumpCooldown <= 0f) {
                            enemy.jumpTime = 0.58f
                            enemy.jumpCooldown = 1.20f + (index % 3) * 0.16f
                        }
                    }
                    if (enemy.jumpTime > 0f) {
                        val jumpSpeed = (0.19f + min(levelId, 40) * 0.001f) * difficultyScale
                        enemy.x -= jumpSpeed * dt
                        val verticalStep = jumpSpeed * 0.72f * dt
                        enemy.y += (targetY - enemy.y).coerceIn(-verticalStep, verticalStep)
                        enemy.jumpTime = (enemy.jumpTime - dt).coerceAtLeast(0f)
                    }
                }
                1, 3, 7, 8 -> {
                    // Turrets, snipers and armored guards trade speed for durability.
                    val turretSpeed = when (enemy.kind) { 1 -> 0.014f; 7 -> 0.008f; 8 -> 0.0075f; else -> 0.011f }
                    enemy.x -= turretSpeed * difficultyScale * dt
                    val verticalStep = turretSpeed * (if (enemy.kind == 7) 1.2f else 0.52f) * dt
                    if (enemy.kind == 7) {
                        enemy.y += sin(animationTime * 1.6f + index) * 0.012f * dt
                    } else enemy.y += (targetY - enemy.y).coerceIn(-verticalStep, verticalStep)
                }
                4 -> {
                    // Healers move slowly and periodically restore a nearby ally.
                    enemy.x -= 0.009f * difficultyScale * dt
                    enemy.y += (targetY - enemy.y).coerceIn(-0.01f * dt, 0.01f * dt)
                    enemy.fireCooldown -= dt
                    if (enemy.fireCooldown <= 0f) {
                        val ally = enemies.filter { it.alive && it !== enemy }
                            .minByOrNull { distanceSquared(it.x, it.y, enemy.x, enemy.y) }
                        if (ally != null && distanceSquared(ally.x, ally.y, enemy.x, enemy.y) < 0.18f * 0.18f) {
                            ally.health = min(ally.health + 1, 8)
                            addBurst(ally.x * viewW, ally.y * viewH, Color.rgb(90, 255, 150), 5)
                            showMessage("HEALER RESTORED AN ENEMY")
                        }
                        enemy.fireCooldown = 2.6f
                    }
                }
                5 -> {
                    // Explosive enemies make a fast, direct approach to the shield.
                    enemy.x -= 0.026f * difficultyScale * dt
                    val verticalStep = 0.025f * difficultyScale * dt
                    enemy.y += (targetY - enemy.y).coerceIn(-verticalStep, verticalStep)
                }
                6 -> {
                    // Teleporters periodically blink between arena coordinates.
                    enemy.fireCooldown -= dt
                    if (enemy.fireCooldown <= 0f && enemy.x < 0.84f) {
                        enemy.x = (0.52f + ((index * 17 + levelId * 3) % 31) / 100f).coerceIn(0.52f, 0.83f)
                        enemy.y = (0.22f + ((index * 11 + levelId) % 56) / 100f).coerceIn(0.22f, 0.78f)
                        enemy.fireCooldown = 2.1f
                        addBurst(enemy.x * viewW, enemy.y * viewH, cyan, 8)
                    }
                    enemy.x -= 0.010f * difficultyScale * dt
                    enemy.y += (targetY - enemy.y).coerceIn(-0.009f * dt, 0.009f * dt)
                }
            }

            if (enemy.x <= breachX) {
                enemy.x = breachX
                enemy.alive = false
                val impactX = enemy.x * viewW
                val impactY = enemy.y * viewH
                addBurst(impactX, impactY, Color.rgb(255, 92, 28), 30)
                val reason = when (enemy.kind) {
                    0 -> "DRONE HIT THE WALL — YOU BURNED"
                    2 -> "SLIME REACHED THE WALL — YOU BURNED"
                    4 -> "HEALER REACHED THE WALL — YOU BURNED"
                    5 -> "EXPLOSIVE BREACHED THE WALL — YOU BURNED"
                    6 -> "TELEPORTER REACHED THE WALL — YOU BURNED"
                    7 -> "SNIPER BREACHED THE WALL — YOU BURNED"
                    else -> "TURRET BREACHED THE WALL — YOU BURNED"
                }
                showMessage(reason)
                fail(reason)
                return
            }
        }
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
        val rightContact = viewW * 0.902f - radius
        val leftWall = viewW * 0.045f + radius
        val rightWall = viewW * 0.955f - radius
        val top = viewH * 0.145f + radius
        val bottom = viewH * 0.935f - radius
        var px = bladeX * viewW + velocityX * dt
        var py = bladeY * viewH + velocityY * dt

        var wallHit = false
        if (!leftHandedMode && px > rightWall) {
            px = rightWall
            velocityX = -abs(velocityX)
            wallHit = true
        } else if (leftHandedMode && px < leftWall) {
            px = leftWall
            velocityX = abs(velocityX)
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

        for ((hazardIndex, hazard) in level.hazards.withIndex()) {
            if (hazardIndex in collectedPowerUps &&
                hazard.type in setOf("power_up", "breakable_wall", "switch", "key", "door")) continue
            val center = hazardCenter(hazard, hazardIndex)
            val hx = center.x * viewW
            val hy = center.y * viewH
            val halfW = max(hazard.width * viewW * 0.5f, radius)
            val halfH = max(hazard.height * viewH * 0.5f, radius)
            val hit = if (hazard.type == "rotating_laser") {
                val angle = animationTime * 1.35f + hazardIndex * 0.4f
                val lineX = cos(angle) * halfW
                val lineY = sin(angle) * halfW
                val edge = radius + max(density * 2f, hazard.height * viewH)
                pointToSegmentDistanceSquared(px, py, hx - lineX, hy - lineY, hx + lineX, hy + lineY) <= edge * edge
            } else {
                px + radius >= hx - halfW && px - radius <= hx + halfW &&
                    py + radius >= hy - halfH && py - radius <= hy + halfH
            }
            if (!hit) continue
            when (hazard.type) {
                "teleporter" -> {
                    if (teleportCooldown <= 0f) {
                        val destination = level.hazards.withIndex().firstOrNull {
                            it.index != hazardIndex && it.value.type == "teleporter"
                        }
                        if (destination != null) {
                            px = destination.value.x * viewW
                            py = destination.value.y * viewH
                            teleportCooldown = 0.85f
                            addBurst(px, py, cyan, 12)
                            playSfx("Optional - Extra Sounds/Teleport - Warp.wav")
                            showMessage("PORTAL WARP")
                        }
                    }
                }
                "power_up" -> {
                    if (collectedPowerUps.add(hazardIndex)) {
                        bouncesLeft += 2
                        score += 250
                        activeEffects["score_multiplier"] = 8f
                        addBurst(hx, hy, gold, 16)
                        playSfx("Optional - Extra Sounds/Power-Up Collect.wav")
                        showMessage("POWER-UP • +2 RETURNS")
                    }
                }
                "switch" -> {
                    collectedPowerUps.add(hazardIndex)
                    switchActivated = true
                    addBurst(hx, hy, gold, 12)
                    playSfx("Core Gameplay SFX/Switch Activation.wav")
                    showMessage("SWITCH ACTIVATED")
                }
                "key" -> {
                    collectedPowerUps.add(hazardIndex)
                    hasLevelKey = true
                    addBurst(hx, hy, gold, 12)
                    playSfx("Optional - Extra Sounds/Power-Up Collect.wav")
                    showMessage("KEY COLLECTED")
                }
                "door" -> {
                    if (hasLevelKey) {
                        collectedPowerUps.add(hazardIndex)
                        addBurst(hx, hy, cyan, 14)
                        showMessage("DOOR UNLOCKED")
                    } else {
                        velocityX = -velocityX * 0.82f
                        velocityY = -velocityY * 0.82f
                        showMessage("DOOR LOCKED • FIND THE KEY")
                    }
                }
                "breakable_wall" -> {
                    collectedPowerUps.add(hazardIndex)
                    addBurst(hx, hy, gold, 18)
                    playSfx("Core Gameplay SFX/Crate Break.wav")
                    showMessage("WALL BROKEN")
                }
                "gravity_well" -> {
                    val dx = hx - px
                    val dy = hy - py
                    val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    velocityX += dx / distance * minDim * 0.20f * dt
                    velocityY += dy / distance * minDim * 0.20f * dt
                    showMessage("GRAVITY WELL PULL")
                }
                "conveyor_belt" -> {
                    velocityX += (if (px < hx) -1f else 1f) * minDim * 0.05f * dt
                    showMessage("CONVEYOR BELT")
                }
                "rotating_laser" -> {
                    fail("ROTATING LASER HIT")
                    return
                }
                "sticky_wall" -> {
                    fail("STICKY WALL — BLADE STUCK")
                    return
                }
                else -> {
                    fail(if (hazard.type.contains("spike")) "SPIKES HIT" else "HAZARD HIT")
                    return
                }
            }
            if (screen != GameScreen.PLAYING) return
        }

        if ((!leftHandedMode && px < leftContact) || (leftHandedMode && px > rightContact)) {
            val paddleCenter = guardPaddleY * viewH
            val secondCenter = secondGuardPaddleY * viewH
            val paddleHalf = guardPaddleHalf()
            val shieldContact = if (leftHandedMode) rightContact else leftContact
            val firstCatch = abs(py - paddleCenter) <= paddleHalf + radius
            val secondCatch = gameMode == "local_coop" && abs(py - secondCenter) <= paddleHalf + radius
            val autoCatch = !firstCatch && !secondCatch && multiCatchCharges > 0
            if (firstCatch || secondCatch || autoCatch) {
                if (autoCatch) {
                    multiCatchCharges--
                    showMessage("MULTI-CATCH SAVE • " + multiCatchCharges + " LEFT")
                }
                px = shieldContact
                val catchCenter = if (!firstCatch && secondCatch) secondCenter else if (autoCatch) py else paddleCenter
                val offset = ((py - catchCenter) / (paddleHalf + radius)).coerceIn(-1f, 1f)
                if (secondCatch && !firstCatch) showMessage("PLAYER 2 SAVE!")
                val oldSpeed = sqrt(velocityX * velocityX + velocityY * velocityY)
                val speed = (oldSpeed * 1.035f).coerceIn(
                    minDim * 0.46f * bladeSpeedScale(),
                    minDim * 0.90f * bladeSpeedScale()
                )
                val bounceAngle = offset * 1.10f
                velocityX = (if (leftHandedMode) -1f else 1f) * abs(cos(bounceAngle) * speed).coerceAtLeast(minDim * 0.32f)
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

        val pendingSpawns = mutableListOf<EnemyState>()
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
                registerTargetHit(if (enemy.kind == 3) 500 else if (enemy.kind == 5) 300 else 100, ex, ey, pink)
                if (enemy.kind == 2 && enemy.canSplit) {
                    pendingSpawns.add(EnemyState((enemy.x - 0.03f).coerceIn(0.12f, 0.90f), (enemy.y - 0.035f).coerceIn(0.18f, 0.82f), true, 2, 1, 8f, false))
                    pendingSpawns.add(EnemyState((enemy.x + 0.03f).coerceIn(0.12f, 0.90f), (enemy.y + 0.035f).coerceIn(0.18f, 0.82f), true, 2, 1, 8f, false))
                }
                if (enemy.kind == 5) {
                    enemies.filter { it.alive && it !== enemy && distanceSquared(it.x, it.y, enemy.x, enemy.y) < 0.13f * 0.13f }
                        .forEach { nearby ->
                            nearby.health -= 2
                            if (nearby.health <= 0) nearby.alive = false
                        }
                    addBurst(ex, ey, Color.rgb(255, 95, 25), 22)
                }
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
                .coerceIn(
                    minDim * 0.46f * bladeSpeedScale(),
                    minDim * 0.90f * bladeSpeedScale()
                )
            val piercing = (activeEffects["piercing"] ?: 0f) > 0f
            if (!piercing) velocityX = if (cameFromLeft) -abs(velocityX) else abs(velocityX)
            velocityY += ((py - ey) / hitRadius).coerceIn(-1f, 1f) * minDim * 0.16f
            val adjustedSpeed = sqrt(velocityX * velocityX + velocityY * velocityY).coerceAtLeast(1f)
            velocityX *= speed / adjustedSpeed
            velocityY *= speed / adjustedSpeed
            px = if (piercing) {
                if (cameFromLeft) ex + hitRadius else ex - hitRadius
            } else {
                if (cameFromLeft) ex - hitRadius else ex + hitRadius
            }
            addBurst(ex, ey, pink, 14)
            haptic()
            break
        }

        enemies.addAll(pendingSpawns)
        bladeX = (px / viewW).coerceIn(0f, 1f)
        bladeY = (py / viewH).coerceIn(0f, 1f)
        bladeTrail.add(PointF(px, py))
        while (bladeTrail.size > 24) bladeTrail.removeAt(0)

        for (pickup in pickups) {
            if (!pickup.active) continue
            val cx = pickup.x * viewW
            val cy = pickup.y * viewH
            val pickupRadius = radius + minDim * 0.026f
            if (distanceSquared(px, py, cx, cy) > pickupRadius * pickupRadius) continue
            pickup.active = false
            when (pickup.type) {
                "shield_expand" -> activeEffects["shield_expand"] = 12f
                "slow_time" -> activeEffects["slow_time"] = 8f
                "score_multiplier" -> activeEffects["score_multiplier"] = 12f
                "multi_catch" -> multiCatchCharges = (multiCatchCharges + 2).coerceAtMost(6)
                "piercing" -> activeEffects["piercing"] = 8f
                "extra_life" -> { activeEffects["extra_life"] = 30f; extraLifeUsed = false }
                "blade_speed" -> activeEffects["blade_speed"] = 10f
                "magnet" -> { score += 200; prefs.edit().putInt("coins", coinBalance() + 25).apply() }
            }
            if (pickup.type == "slow_time") { velocityX *= 0.72f; velocityY *= 0.72f }
            if (pickup.type == "blade_speed") { velocityX *= 1.2f; velocityY *= 1.2f }
            addBurst(cx, cy, gold, 18)
            showMessage("POWER-UP: " + pickup.type.replace('_', ' ').uppercase())
            playSfx("Optional - Extra Sounds/Power-Up Collect.wav")
        }

        if (enemies.none { it.alive }) {
            completeLevel()
            return
        }
    }

    private fun upgradeLevel(id: String): Int = prefs.getInt("upgrade_" + id, 0).coerceAtLeast(0)

    private fun coinBalance(): Int = prefs.getInt("coins", 0).coerceAtLeast(0)

    private fun bladeSpeedScale(): Float = 1f + upgradeLevel("overdrive").coerceAtMost(2) * 0.12f

    private fun guardPaddleHalf(): Float = viewH * when (difficulty) {
        1 -> 0.105f
        3 -> 0.065f
        else -> 0.085f
    } * (1f + upgradeLevel("shield_mod").coerceAtMost(2) * 0.14f) * (if ((activeEffects["shield_expand"] ?: 0f) > 0f) 1.55f else 1f)

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
        val speed = minDim * (0.50f + difficulty * 0.075f + min(levelId, 30) * 0.003f) * bladeSpeedScale()
        velocityX = speed
        velocityY = speed * (sin(levelId * 1.37f) * 0.045f)
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

    private fun openShop(returnTo: GameScreen) {
        shopReturnScreen = returnTo
        screen = GameScreen.SHOP
        slowMotion = false
        touchingAbility = false
        lastFrame = System.nanoTime()
        invalidate()
    }

    private fun nextShopCost(item: ShopItem, currentLevel: Int): Int =
        item.baseCost * (currentLevel + 1)

    private fun purchaseShopItem(index: Int) {
        if (index !in shopItems.indices) return
        val item = shopItems[index]
        val currentLevel = upgradeLevel(item.id).coerceAtMost(item.maxLevel)
        if (item.id == "pulse_core" && upgradeLevel("nova") == 0) {
            showMessage("UNLOCK NOVA PULSE FIRST")
            return
        }
        if (currentLevel >= item.maxLevel) {
            showMessage("UPGRADE ALREADY MAXED")
            return
        }
        val cost = nextShopCost(item, currentLevel)
        val balance = coinBalance()
        if (balance < cost) {
            showMessage("NEED " + (cost - balance) + " MORE COINS")
            playTone(ToneGenerator.TONE_PROP_NACK)
            return
        }
        prefs.edit()
            .putInt("coins", balance - cost)
            .putInt("upgrade_" + item.id, currentLevel + 1)
            .apply()
        showMessage(item.title + " UPGRADED!")
        addBurst(viewW * 0.5f, viewH * 0.48f, when (index % 3) { 0 -> cyan; 1 -> gold; else -> pink }, 22)
        playSfx("Optional - Extra Sounds/Power-Up Collect.wav")
        haptic()
        invalidate()
    }

    private fun activateNovaPulse() {
        if (upgradeLevel("nova") == 0) {
            openShop(GameScreen.PLAYING)
            return
        }
        if (novaCooldownRemaining > 0f) {
            showMessage("NOVA RECHARGING: " + (novaCooldownRemaining + 0.99f).toInt() + "s")
            return
        }
        val coreLevel = upgradeLevel("pulse_core").coerceAtMost(2)
        val centerX = bladeX * viewW
        val centerY = bladeY * viewH
        val radius = minDim * (0.18f + coreLevel * 0.045f)
        val radiusSquared = radius * radius
        val pendingSlimes = mutableListOf<EnemyState>()
        var hits = 0

        for ((index, enemy) in enemies.toList().withIndex()) {
            if (!enemy.alive) continue
            val center = enemyCenter(enemy, index)
            val ex = center.x * viewW
            val ey = center.y * viewH
            if (distanceSquared(centerX, centerY, ex, ey) > radiusSquared) continue
            hits += 1
            enemy.health -= 1
            addBurst(ex, ey, if (enemy.health <= 0) cyan else gold, 12)
            if (enemy.health <= 0) {
                enemy.alive = false
                if (enemy.kind == 2 && enemy.canSplit) {
                    pendingSlimes.add(
                        EnemyState((enemy.x - 0.035f).coerceIn(0.11f, 0.9f),
                            (enemy.y - 0.025f).coerceIn(0.2f, 0.82f), true, 2, 1, 8f, false)
                    )
                    pendingSlimes.add(
                        EnemyState((enemy.x + 0.035f).coerceIn(0.11f, 0.9f),
                            (enemy.y + 0.025f).coerceIn(0.2f, 0.82f), true, 2, 1, 8f, false)
                    )
                }
                registerTargetHit(if (enemy.kind == 3) 250 else 100, ex, ey, cyan)
                playSfx("Core Gameplay SFX/Enemy Slice (Variation 1).wav")
            } else {
                score += 35
                showMessage("NOVA DAMAGED ENEMY")
            }
        }

        if (hits == 0) {
            showMessage("NO ENEMIES IN NOVA RANGE")
            return
        }
        enemies.addAll(pendingSlimes)
        novaCooldownRemaining = max(3f, 8f - coreLevel * 1.75f)
        novaFlashX = centerX
        novaFlashY = centerY
        novaFlashRadius = radius
        novaFlashUntil = System.nanoTime() + 420_000_000L
        addBurst(centerX, centerY, cyan, 34)
        showMessage("NOVA PULSE! " + hits + " ENEMY HIT")
        playSfx("Optional - Extra Sounds/Shield Break.wav")
        haptic()
        invalidate()
    }

    private fun drawNovaFlash(canvas: Canvas) {
        val remaining = novaFlashUntil - System.nanoTime()
        if (remaining <= 0L) return
        val progress = (1f - remaining.toFloat() / 420_000_000f).coerceIn(0f, 1f)
        val radius = novaFlashRadius * (0.45f + progress * 0.75f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * (4.5f - progress * 2f)
        paint.color = Color.argb((220f * (1f - progress)).toInt().coerceIn(0, 220),
            Color.red(cyan), Color.green(cyan), Color.blue(cyan))
        paint.setShadowLayer(density * 12f, 0f, 0f, cyan)
        canvas.drawCircle(novaFlashX, novaFlashY, radius, paint)
        canvas.drawCircle(novaFlashX, novaFlashY, radius * 0.72f, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
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
        val baseCoinReward = 80 + levelId * 8 + earnedStars * 30 + min(bestCombo, 12) * 5
        val coinMultiplier = 1f + upgradeLevel("coin_magnet").coerceAtMost(2) * 0.25f
        lastCoinsEarned = (baseCoinReward * coinMultiplier).toInt()
        val updatedCoinBalance = coinBalance() + lastCoinsEarned
        val starsKey = bestStarsKey(levelId)
        val previousBest = prefs.getInt(starsKey, 0)
        val bestShotsKey = bestShotsKey(levelId)
        val previousBestShots = prefs.getInt(bestShotsKey, Int.MAX_VALUE)
        prefs.edit()
            .putInt("total_clears", prefs.getInt("total_clears", 0) + 1)
            .putLong("total_play_seconds", prefs.getLong("total_play_seconds", 0L) + levelElapsedSeconds.toLong())
            .putInt("unlocked_level", newUnlocked)
            .putInt("coins", updatedCoinBalance)
            .putInt(starsKey, max(previousBest, earnedStars))
            .putInt(bestShotsKey, min(previousBestShots, shotsUsed))
            .putInt("best_score_" + levelId + "_difficulty_" + difficulty, max(prefs.getInt("best_score_" + levelId + "_difficulty_" + difficulty, 0), score))
            .putInt("campaign_best_stars_difficulty_" + difficulty,
                totalBestStarsAfter(levelId, max(previousBest, earnedStars)))
            .apply()
        if (gameMode == "daily") prefs.edit().putInt("daily_best_score", max(prefs.getInt("daily_best_score", 0), score)).apply()
        if (gameMode == "weekly") prefs.edit().putInt("weekly_best_score", max(prefs.getInt("weekly_best_score", 0), score)).apply()
        saveReplayTrace()
        if (gameMode == "speedrun") prefs.edit().putFloat("best_speedrun_seconds", min(prefs.getFloat("best_speedrun_seconds", Float.MAX_VALUE), levelElapsedSeconds)).apply()
        screen = if (levelId >= levels.size) GameScreen.CAMPAIGN_COMPLETE else GameScreen.WON
        playSfx("Core Gameplay SFX/Level Complete.wav")
        if (earnedStars > previousBest) playSfx("UI & Feedback SFX/Star - Score Earned.wav")
        haptic()
    }

    private fun fail(reason: String) {
        if (screen != GameScreen.PLAYING) return
        if (gameMode == "zen" || gameMode == "practice") {
            flying = false
            aiming = false
            velocityX = 0f
            velocityY = 0f
            bladeX = 0.145f
            bladeY = guardPaddleY
            showMessage("PRACTICE RESET • NO RUN LOST")
            launchWallGuardBlade()
            return
        }
        if (!extraLifeUsed && (activeEffects["extra_life"] ?: 0f) > 0f) {
            extraLifeUsed = true
            activeEffects.remove("extra_life")
            enemies.filter { it.alive && it.x < 0.18f }.forEach { it.x = 0.78f; it.y = guardPaddleY }
            flying = false
            launchWallGuardBlade()
            showMessage("EXTRA LIFE USED")
            return
        }
        saveReplayTrace()
        prefs.edit().putInt("total_fails", prefs.getInt("total_fails", 0) + 1).apply()
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
            val targetCount = (if (gameMode == "editor_test") editorTargetCount else if (gameMode == "boss_rush") 3 else (3 + (levelId - 1) / 2)).coerceAtMost(12)
            val points = (0 until targetCount).map { index ->
                val column = index % 4
                val row = index / 4
                val y = when (row) {
                    0 -> 0.5f
                    1 -> if (column % 2 == 0) 0.32f else 0.68f
                    else -> if (column % 2 == 0) 0.25f else 0.75f
                }
                PointN(0.49f + column * 0.115f, y)
            }
            level = level.copy(
                spawn = PointN(0.145f, 0.5f),
                enemies = points,
                crates = emptyList(),
                hazards = level.hazards,
                requiresAllTargets = true,
                requiresSwitch = false
            )
            guardPaddleY = 0.5f
            secondGuardPaddleY = 0.5f
            secondPointerId = -1
            primaryPointerId = -1
        }
        if (gameMode == "editor_test") {
            val draftHazards = when (editorHazardId) {
                1 -> listOf(HazardState("spikes", 0.50f, 0.86f, 0.62f, 0.035f))
                2 -> listOf(HazardState("laser", 0.50f, 0.48f, 0.64f, 0.018f))
                3 -> listOf(HazardState("moving_hazard", 0.50f, 0.62f, 0.38f, 0.03f))
                4 -> listOf(
                    HazardState("teleporter", 0.22f, 0.34f, 0.075f, 0.075f),
                    HazardState("teleporter", 0.78f, 0.66f, 0.075f, 0.075f)
                )
                5 -> listOf(HazardState("sticky_wall", 0.75f, 0.53f, 0.085f, 0.21f))
                else -> emptyList()
            }
            level = level.copy(hazards = draftHazards)
        }
        if (gameMode == "procedural") {
            val hazardRandom = kotlin.random.Random(levelId * 997 + prefs.getInt("procedural_seed", 23) + 41)
            val generatedHazards = mutableListOf<HazardState>()
            val hazardOptions = listOf("spikes", "moving_spikes", "laser", "rotating_laser", "moving_hazard", "sticky_wall", "gravity_well", "conveyor_belt", "breakable_wall", "power_up")
            repeat(2) { index ->
                val type = hazardOptions[hazardRandom.nextInt(hazardOptions.size)]
                generatedHazards.add(
                    HazardState(type,
                        0.32f + hazardRandom.nextFloat() * 0.40f,
                        0.28f + hazardRandom.nextFloat() * 0.48f,
                        when (type) { "spikes", "laser", "moving_hazard" -> 0.28f + hazardRandom.nextFloat() * 0.28f; else -> 0.05f + hazardRandom.nextFloat() * 0.06f },
                        when (type) { "sticky_wall" -> 0.14f; "laser" -> 0.018f; "spikes", "moving_hazard" -> 0.025f; else -> 0.055f }
                    )
                )
            }
            if (hazardRandom.nextBoolean()) {
                generatedHazards.add(HazardState("teleporter", 0.22f, 0.35f, 0.075f, 0.075f))
                generatedHazards.add(HazardState("teleporter", 0.78f, 0.67f, 0.075f, 0.075f))
            }
            level = level.copy(hazards = generatedHazards)
        }

        val activeEnemyPoints = when {
            gameMode == "procedural" -> {
                val seeded = kotlin.random.Random(levelId * 739 + prefs.getInt("procedural_seed", 23))
                (0 until (3 + seeded.nextInt(10))).map {
                    PointN(0.38f + seeded.nextFloat() * 0.52f, 0.20f + seeded.nextFloat() * 0.62f)
                }
            }
            gameMode == "editor_test" -> (0 until editorTargetCount).map { i ->
                PointN(0.42f + (i % 4) * 0.13f, 0.22f + (i / 4) * 0.22f)
            }
            else -> level.enemies
        }
        enemies = activeEnemyPoints.mapIndexed { index, point ->
            val bossRush = gameMode == "boss_rush"
            val finalBoss = levelId == levels.size && index == 0
            val kind = if (bossRush || finalBoss) 3 else enemyKindForCampaignLevel(levelId, index)
            EnemyState(point.x, point.y, true, kind,
                if (bossRush) 10 + index * 2 else if (finalBoss) 18 else enemyHealthForLevel(levelId, index),
                1.2f + (index % 3) * 0.6f, true)
        }.toMutableList()
        pickups.clear()
        activeEffects.clear()
        extraLifeUsed = false
        multiCatchCharges = 0
        hasLevelKey = false
        val pickupTypes = listOf("shield_expand", "slow_time", "multi_catch", "score_multiplier", "extra_life", "blade_speed", "magnet", "piercing")
        val pickupCount = when {
            gameMode == "editor_test" -> 0
            gameMode == "procedural" -> 2
            levelId % 2 == 0 -> 1
            else -> 0
        }
        repeat(pickupCount) { i ->
            val type = if (gameMode == "editor_test" && editorHazardId > 0)
                when (editorHazardId) { 1 -> "score_multiplier"; 2 -> "slow_time"; 3 -> "blade_speed"; 4 -> "magnet"; else -> "shield_expand" }
            else pickupTypes[(levelId + i) % pickupTypes.size]
            pickups.add(PickupState(type, 0.885f, if (i % 2 == 0) 0.22f else 0.79f))
        }
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
        novaCooldownRemaining = 0f
        lastCoinsEarned = 0
        touchingAbility = false
        lastFrame = System.nanoTime()
    }

    private fun startLevel(id: Int) {
        val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
        if (id > unlocked) {
            showMessage("CLEAR PREVIOUS LEVELS TO UNLOCK THIS")
            return
        }
        prefs.edit().putInt("total_runs", prefs.getInt("total_runs", 0) + 1).apply()
        runTrace.clear()
        traceTimer = 0f
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
                cyan = Color.rgb(75, 255, 150); pink = Color.rgb(255, 105, 75)
                gold = Color.rgb(220, 255, 90); dark = Color.rgb(5, 18, 18)
                muted = Color.rgb(90, 146, 128); grid = Color.rgb(17, 50, 47)
            }
            2 -> {
                cyan = Color.rgb(180, 130, 255); pink = Color.rgb(255, 90, 190)
                gold = Color.rgb(255, 190, 100); dark = Color.rgb(13, 7, 25)
                muted = Color.rgb(132, 112, 165); grid = Color.rgb(41, 25, 63)
            }
            else -> {
                cyan = Color.rgb(45, 235, 255); pink = Color.rgb(255, 65, 150)
                gold = Color.rgb(255, 199, 83); dark = Color.rgb(7, 10, 22)
                muted = Color.rgb(94, 120, 150); grid = Color.rgb(22, 35, 58)
            }
        }
        if (colorblindMode) {
            cyan = Color.rgb(0, 114, 178)
            pink = Color.rgb(230, 159, 0)
            gold = Color.rgb(240, 228, 66)
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
            val decoded = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Could not decode image: $path")
            // Crop transparent margins in memory before drawing. The original PNGs
            // stay untouched in the repository and APK, avoiding PNG re-encoding bloat.
            val bitmap = trimTransparentPadding(decoded)
            if (bitmap !== decoded && !decoded.isRecycled) decoded.recycle()
            bitmapAssets[path] = bitmap
            bitmap
        } catch (_: Exception) {
            failedBitmapAssets.add(path)
            null
        }
    }

    private fun trimTransparentPadding(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 2 || height < 2 || !source.hasAlpha()) return source

        return try {
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (index in pixels.indices) {
                val alpha = (pixels[index] ushr 24) and 0xff
                if (alpha > 12) {
                    val x = index % width
                    val y = index / width
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < minX || maxY < minY) return source

            // Leave a slim margin for antialiasing and soft glows.
            val margin = max(1, (max(width, height) * 0.02f).toInt())
            val left = (minX - margin).coerceAtLeast(0)
            val top = (minY - margin).coerceAtLeast(0)
            val right = (maxX + margin + 1).coerceAtMost(width)
            val bottom = (maxY + margin + 1).coerceAtMost(height)
            if (left == 0 && top == 0 && right == width && bottom == height) return source

            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        } catch (_: RuntimeException) {
            source
        }
    }

    // Keep every sprite's original aspect ratio. Ordinary UI/game assets are fitted
    // inside their layout bounds, never stretched to fill them.
    private fun fitAssetRect(bitmap: Bitmap, bounds: RectF): RectF {
        if (bitmap.width <= 0 || bitmap.height <= 0 || bounds.width() <= 0f || bounds.height() <= 0f) {
            return RectF(bounds)
        }
        val scale = min(bounds.width() / bitmap.width.toFloat(), bounds.height() / bitmap.height.toFloat())
        val fittedWidth = bitmap.width * scale
        val fittedHeight = bitmap.height * scale
        return RectF(
            bounds.centerX() - fittedWidth / 2f,
            bounds.centerY() - fittedHeight / 2f,
            bounds.centerX() + fittedWidth / 2f,
            bounds.centerY() + fittedHeight / 2f
        )
    }

    private fun drawAsset(canvas: Canvas, path: String, rect: RectF, alpha: Int = 255) {
        val bitmap = bitmapAsset(path) ?: return
        val oldAlpha = paint.alpha
        val oldFilter = paint.isFilterBitmap
        val oldStyle = paint.style
        paint.alpha = alpha.coerceIn(0, 255)
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, null, fitAssetRect(bitmap, rect), paint)
        paint.alpha = oldAlpha
        paint.isFilterBitmap = oldFilter
        paint.style = oldStyle
    }

    // Large scenery backgrounds fill their viewport by cropping the overflow rather
    // than distorting the source artwork.
    private fun drawAssetCover(canvas: Canvas, path: String, rect: RectF, alpha: Int = 255) {
        val bitmap = bitmapAsset(path) ?: return
        if (bitmap.width <= 0 || bitmap.height <= 0 || rect.width() <= 0f || rect.height() <= 0f) return
        val scale = max(rect.width() / bitmap.width.toFloat(), rect.height() / bitmap.height.toFloat())
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val target = RectF(
            rect.centerX() - scaledWidth / 2f,
            rect.centerY() - scaledHeight / 2f,
            rect.centerX() + scaledWidth / 2f,
            rect.centerY() + scaledHeight / 2f
        )
        val oldAlpha = paint.alpha
        val oldFilter = paint.isFilterBitmap
        val oldStyle = paint.style
        paint.alpha = alpha.coerceIn(0, 255)
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawBitmap(bitmap, null, target, paint)
        canvas.restore()
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
            GameScreen.MENU, GameScreen.WORLDS, GameScreen.LEVELS, GameScreen.SETTINGS, GameScreen.HELP, GameScreen.DIFFICULTY, GameScreen.SHOP, GameScreen.FEATURES, GameScreen.MODES, GameScreen.CHALLENGES, GameScreen.ACHIEVEMENTS, GameScreen.STATS, GameScreen.CUSTOMIZE, GameScreen.EDITOR, GameScreen.ACCESSIBILITY, GameScreen.EXTRAS, GameScreen.LOCAL_SCORES, GameScreen.REPLAYS, GameScreen.SEASONAL, GameScreen.ONLINE_FEATURES ->
                "Music & Ambience/Main Menu Theme.wav"
            GameScreen.PLAYING, GameScreen.PAUSED -> "Music & Ambience/Gameplay Background.wav"
            GameScreen.WON, GameScreen.CAMPAIGN_COMPLETE -> "Music & Ambience/Victory Theme.wav"
            GameScreen.FAILED -> "Music & Ambience/Game Over Theme.wav"
            GameScreen.JUKEBOX -> musicTracks[jukeboxTrackId]
            GameScreen.PHOTO_MODE -> "Music & Ambience/Gameplay Background.wav"
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
        drawAssetCover(canvas, image, RectF(0f, 0f, viewW, viewH), 235)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(62, 3, 6, 18)
        canvas.drawRect(0f, 0f, viewW, viewH, paint)
    }

    private fun drawMenu(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawAsset(canvas, "App Icon & Logo/Game Logo.png", RectF(viewW * 0.16f, viewH * 0.13f, viewW * 0.84f, viewH * 0.29f), 255)
        drawText(canvas, "BOUNCE OFF ENEMIES  •  GUARD THE LEFT WALL", 0.5f, 0.435f, 0.020f, muted, Paint.Align.CENTER)
        drawText(canvas, "◈ " + coinBalance(), 0.91f, 0.105f, 0.026f, gold, Paint.Align.RIGHT)
        drawButton(canvas, 0.10f, 0.456f, 0.90f, 0.523f, "PLAY CAMPAIGN", cyan)
        drawButton(canvas, 0.10f, 0.532f, 0.90f, 0.599f, "LEVEL SELECT", gold)
        drawButton(canvas, 0.10f, 0.608f, 0.90f, 0.675f, "MORE FEATURES", pink)
        drawButton(canvas, 0.10f, 0.684f, 0.90f, 0.751f, "BLADE SHOP", gold)
        drawButton(canvas, 0.10f, 0.760f, 0.90f, 0.827f, "SETTINGS", cyan)
        val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
        val bestTotal = totalBestStarsAfter(0, 0)
        drawText(canvas, "UNLOCKED  " + unlocked + "/" + levels.size + "     BEST STARS  " + bestTotal + "/" + (levels.size * 3),
            0.5f, 0.86f, 0.019f, muted, Paint.Align.CENTER)
        drawText(canvas, "COLLECT COINS • UPGRADE YOUR BLADE • SURVIVE EVERY WAVE", 0.5f, 0.91f, 0.014f, muted, Paint.Align.CENTER)
    }
    private fun drawShop(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "BLADE FORGE", 0.5f, 0.135f, 0.064f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "UPGRADE YOUR EDGE. TURN EVERY CLEAR INTO COINS.", 0.5f, 0.192f, 0.018f, cyan, Paint.Align.CENTER)
        drawAssetCentered(canvas, "UI Elements/Star Icon.png", viewW * 0.42f, viewH * 0.225f, minDim * 0.035f)
        drawText(canvas, "COINS  " + coinBalance(), 0.53f, 0.233f, 0.024f, gold, Paint.Align.LEFT)

        val accents = intArrayOf(cyan, pink, gold, cyan, pink)
        shopItems.forEachIndexed { index, item ->
            val slot = shopCardSlots[index]
            val left = slot[0]; val top = slot[1]; val right = slot[2]; val bottom = slot[3]
            val rect = RectF(left * viewW, top * viewH, right * viewW, bottom * viewH)
            val accent = accents[index]
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(205, 7, 17, 35)
            canvas.drawRoundRect(rect, density * 12f, density * 12f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1.5f, density * 1.5f)
            paint.color = Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent))
            paint.setShadowLayer(density * 7f, 0f, 0f, accent)
            canvas.drawRoundRect(rect, density * 12f, density * 12f, paint)
            paint.clearShadowLayer()
            paint.style = Paint.Style.FILL

            val current = upgradeLevel(item.id).coerceAtMost(item.maxLevel)
            val lockedByNova = item.id == "pulse_core" && upgradeLevel("nova") == 0
            drawText(canvas, item.title, (left + right) / 2f, top + 0.047f, 0.020f, Color.WHITE, Paint.Align.CENTER)
            drawAssetCentered(canvas, item.iconPath, rect.centerX(), viewH * (top + 0.096f), minDim * 0.052f, if (lockedByNova) 105 else 255)
            drawText(canvas, item.description, (left + right) / 2f, top + 0.142f, 0.014f, muted, Paint.Align.CENTER)

            val status = when {
                lockedByNova -> "UNLOCK NOVA FIRST"
                current >= item.maxLevel -> "MAX LEVEL  •  " + current
                else -> "LV " + current + " → " + (current + 1) + "  •  " + nextShopCost(item, current) + " COINS"
            }
            drawText(canvas, status, (left + right) / 2f, top + 0.183f, 0.014f,
                if (lockedByNova) muted else if (current >= item.maxLevel) cyan else accent, Paint.Align.CENTER)
            if (lockedByNova) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(70, 0, 0, 0)
                canvas.drawRoundRect(rect, density * 12f, density * 12f, paint)
            }
        }
        drawButton(canvas, 0.24f, 0.805f, 0.76f, 0.895f,
            if (shopReturnScreen == GameScreen.PLAYING) "BACK TO BATTLE" else "BACK TO MENU", cyan)
        drawText(canvas, "PERMANENT UPGRADES ARE SAVED ON THIS DEVICE", 0.5f, 0.945f, 0.014f, muted, Paint.Align.CENTER)
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
            drawButton(canvas,0.10f,top,0.90f,top+0.076f,label,if(available) cyan else muted,available)
        }
        drawButton(canvas,0.20f,0.83f,0.80f,0.915f,"BACK",gold)
        if(System.nanoTime()<messageUntil&&message.isNotBlank()) drawText(canvas,message,0.5f,0.93f,0.018f,gold,Paint.Align.CENTER)
    }

    private fun drawLevelSelect(canvas: Canvas) {
        drawText(canvas,CampaignWorlds.worldName(selectedWorld).uppercase(),0.5f,0.125f,0.055f,Color.WHITE,Paint.Align.CENTER)
        val first=CampaignWorlds.firstLevelForWorld(selectedWorld); val last=min(first+9,levels.size)
        drawText(canvas,"LEVELS $first-$last  •  UNLOCKED "+prefs.getInt("unlocked_level",1).coerceIn(1,levels.size)+"/"+levels.size,0.5f,0.165f,0.020f,cyan,Paint.Align.CENTER)
        for(mode in 1..3){
            val left=0.035f+(mode-1)*0.315f
            val label=(if(difficulty==mode) "✓ " else "")+when(mode){1->"★☆☆ EASY";2->"★★☆ NORMAL";else->"★★★ HARD"}
            drawButton(canvas,left,0.195f,left+0.30f,0.260f,label,if(difficulty==mode) gold else cyan)
        }
        val unlocked=prefs.getInt("unlocked_level",1).coerceIn(1,levels.size)
        for(local in 1..10){
            val id=first+local-1;if(id>last) continue
            val col=(local-1)%4;val row=(local-1)/4;val left=0.035f+col*0.24f;val top=0.29f+row*0.135f
            val active=id<=unlocked;val best=prefs.getInt(bestStarsKey(id),0).coerceIn(0,3)
            drawButton(canvas,left,top,left+0.23f,top+0.095f,local.toString().padStart(2,'0')+" "+"★".repeat(best)+"☆".repeat(3-best),if(active) cyan else muted,active)
        }
        drawText(canvas,"STARS = TIME + COMBO / TARGET CLEAR",0.5f,0.735f,0.014f,muted,Paint.Align.CENTER)
        drawButton(canvas,0.20f,0.83f,0.80f,0.915f,"BACK TO WORLDS",gold)
    }

    private fun drawGame(canvas: Canvas) {
        if (wallGuardMode) {
            drawWallGuardGame(canvas)
            return
        }
        drawText(canvas,"BOUNCE BLADE",0.07f,0.047f,0.025f,Color.WHITE)
        drawText(canvas,level.name.uppercase()+"  "+levelId+"/"+levels.size+"  •  "+"★".repeat(difficulty),0.48f,0.047f,0.020f,cyan,Paint.Align.CENTER)
        drawText(canvas,"BOUNCES "+bouncesLeft,0.78f,0.047f,0.019f,gold,Paint.Align.RIGHT)
        drawButton(canvas,0.825f,0.027f,0.97f,0.110f,"Ⅱ",pink)
        val timeLeft=GameRules.timeLimitSeconds(levelId,difficulty)-levelElapsedSeconds
        drawText(canvas,"SCORE "+score,0.07f,0.092f,0.018f,gold)
        drawText(canvas,"TIME "+formatTime(timeLeft),0.47f,0.092f,0.018f,if(levelElapsedSeconds<=GameRules.targetTimeSeconds(levelId,difficulty)) cyan else pink,Paint.Align.CENTER)
        drawAsset(canvas, "UI Elements/Timer Icon.png", RectF(viewW * 0.365f, viewH * 0.077f, viewW * 0.405f, viewH * 0.11f), 240)
        drawText(canvas,"COMBO x"+comboCount,0.775f,0.092f,0.018f,pink,Paint.Align.RIGHT)
        drawAsset(canvas, "UI Elements/Combo Icon.png", RectF(viewW * 0.785f, viewH * 0.077f, viewW * 0.815f, viewH * 0.11f), 240)
        val rect = RectF(viewW * 0.05f, viewH * 0.13f, viewW * 0.95f, viewH * 0.94f)
        drawAssetCover(canvas, "Backgrounds & Environments/Gameplay Background.png", rect, 220)
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
        for (pickup in pickups) if (pickup.active) {
            val px = pickup.x * viewW
            val py = pickup.y * viewH
            paint.style = Paint.Style.FILL
            paint.color = gold
            paint.setShadowLayer(density * 12f, 0f, 0f, gold)
            canvas.drawCircle(px, py, minDim * 0.024f, paint)
            paint.clearShadowLayer()
            paint.color = dark
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = minDim * 0.023f
            canvas.drawText(when (pickup.type) {
                "shield_expand" -> "S"; "slow_time" -> "T"; "multi_catch" -> "M"; "piercing" -> "P"
                "score_multiplier" -> "2x"; "extra_life" -> "+"; "blade_speed" -> "B"; else -> "C"
            }, px, py + paint.textSize * 0.34f, paint)
        }
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
        if (leftHandedMode) drawButton(canvas, 0.03f, 0.027f, 0.175f, 0.110f, "Ⅱ", pink)
        else drawButton(canvas, 0.825f, 0.027f, 0.97f, 0.110f, "Ⅱ", pink)
        drawText(canvas, "SCORE $score", 0.07f, 0.092f, 0.019f, gold)
        drawText(canvas, "TARGETS " + enemies.count { it.alive }, 0.50f, 0.092f, 0.019f, pink, Paint.Align.CENTER)
        drawText(canvas, "SAVE STREAK $comboCount", 0.78f, 0.092f, 0.016f, cyan, Paint.Align.RIGHT)

        val arena = RectF(viewW * 0.035f, viewH * 0.135f, viewW * 0.965f, viewH * 0.94f)
        drawAssetCover(canvas, "Backgrounds & Environments/Gameplay Background.png", arena, 230)
        drawAsset(canvas, "UI Elements/UI Panel.png",
            RectF(viewW * 0.025f, viewH * 0.012f, viewW * 0.975f, viewH * 0.125f), 115)
        drawEnvironmentEffects(canvas)
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

        drawHazards(canvas)
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
        val paddle = if (leftHandedMode) RectF(
            viewW * 0.902f, paddleCenter - paddleHalf, viewW * 0.957f, paddleCenter + paddleHalf
        ) else RectF(
            viewW * 0.043f, paddleCenter - paddleHalf, viewW * 0.098f, paddleCenter + paddleHalf
        )
        paint.style = Paint.Style.FILL
        val shieldColor = shieldSkinColor()
        paint.color = Color.argb(105, Color.red(shieldColor), Color.green(shieldColor), Color.blue(shieldColor))
        paint.setShadowLayer(density * 13f, 0f, 0f, shieldColor)
        canvas.drawRoundRect(paddle, density * 8f, density * 8f, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, density * 2.5f)
        paint.color = shieldSkinColor()
        canvas.drawRoundRect(paddle, density * 8f, density * 8f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(
            RectF(paddle.centerX() - density, paddle.top + density * 7f,
                paddle.centerX() + density, paddle.bottom - density * 7f),
            density, density, paint
        )
        if (gameMode == "local_coop") {
            val secondCenter = secondGuardPaddleY * viewH
            val secondPaddle = if (leftHandedMode) RectF(viewW * 0.902f, secondCenter - paddleHalf,
                viewW * 0.957f, secondCenter + paddleHalf)
            else RectF(viewW * 0.043f, secondCenter - paddleHalf, viewW * 0.098f, secondCenter + paddleHalf)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(if (secondPointerId >= 0) 145 else 70, Color.red(pink), Color.green(pink), Color.blue(pink))
            paint.setShadowLayer(density * 9f, 0f, 0f, pink)
            canvas.drawRoundRect(secondPaddle, density * 8f, density * 8f, paint)
            paint.clearShadowLayer()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, density * 2f)
            paint.color = pink
            canvas.drawRoundRect(secondPaddle, density * 8f, density * 8f, paint)
            paint.style = Paint.Style.FILL
            drawText(canvas, "P2", if (leftHandedMode) 0.88f else 0.12f,
                (secondCenter - paddleHalf - density * 3f) / viewH, 0.014f, pink, Paint.Align.CENTER)
        }

        val bladePx = bladeX * viewW
        val bladePy = bladeY * viewH
        canvas.save()
        canvas.rotate(angle, bladePx, bladePy)
        val bladeSize = minDim * 0.078f
        drawAsset(canvas, if (flying) "Gameplay Sprites/Blade.png" else "Gameplay Sprites/Blade Idle.png",
            RectF(bladePx - bladeSize / 2f, bladePy - bladeSize / 2f,
                bladePx + bladeSize / 2f, bladePy + bladeSize / 2f), 255)
        canvas.restore()
        drawNovaFlash(canvas)

        drawText(canvas, "DRAG UP OR DOWN TO MOVE THE LEFT SHIELD",
            0.5f, 0.965f, 0.018f, cyan, Paint.Align.CENTER)
        if (screen == GameScreen.PLAYING) {
            val novaLabel = when {
                upgradeLevel("nova") == 0 -> "BLADE SHOP"
                novaCooldownRemaining <= 0f -> "NOVA READY"
                else -> "NOVA " + (novaCooldownRemaining + 0.99f).toInt() + "s"
            }
            if (leftHandedMode) drawButton(canvas, 0.02f, 0.790f, 0.225f, 0.935f, novaLabel, gold, true)
            else drawButton(canvas, 0.775f, 0.790f, 0.98f, 0.935f, novaLabel, gold, true)
        }
        if (System.nanoTime() < messageUntil && message.isNotBlank()) {
            drawText(canvas, message, 0.5f, 0.885f, 0.020f, gold, Paint.Align.CENTER)
        }
    }


    private fun drawEnvironmentEffects(canvas: Canvas) {
        val world = CampaignWorlds.indexForLevel(levelId)
        paint.style = Paint.Style.FILL
        when (world) {
            5 -> {
                paint.color = Color.argb(22, 100, 38, 150)
                canvas.drawRect(viewW * 0.035f, viewH * 0.135f, viewW * 0.965f, viewH * 0.94f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, density)
                paint.color = Color.argb(70, 177, 105, 255)
                for (i in 0..5) {
                    val x = viewW * (0.12f + i * 0.15f)
                    val pulse = 0.018f + abs(sin(animationTime * 0.7f + i)) * 0.03f
                    canvas.drawCircle(x, viewH * (0.3f + (i % 3) * 0.22f), minDim * pulse, paint)
                }
            }
            6 -> {
                paint.color = Color.argb(28, 250, 80, 12)
                canvas.drawRect(viewW * 0.035f, viewH * 0.82f, viewW * 0.965f, viewH * 0.94f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, density)
                paint.color = Color.argb(115, 255, 155, 50)
                for (i in 0..8) {
                    val x = viewW * (0.08f + i * 0.105f)
                    val sway = sin(animationTime * 1.1f + i * 0.8f) * minDim * 0.018f
                    canvas.drawLine(x, viewH * 0.90f, x + sway, viewH * (0.72f - (i % 3) * 0.04f), paint)
                }
            }
            7 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, density)
                for (i in 0..2) {
                    val cx = viewW * (0.32f + i * 0.28f)
                    val cy = viewH * (0.33f + (i % 2) * 0.34f)
                    val radius = minDim * (0.018f + ((animationTime * 0.05f + i * 0.02f) % 0.045f))
                    paint.color = Color.argb(65, 70, 180, 255)
                    canvas.drawCircle(cx, cy, radius, paint)
                    canvas.drawCircle(cx, cy, radius * 1.6f, paint)
                }
            }
            8 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, density)
                paint.color = Color.argb(80, 170, 210, 255)
                for (i in 0..17) {
                    val x = viewW * (((i * 53 + 17) % 997) / 997f)
                    val phase = (animationTime * (0.35f + (i % 4) * 0.06f) + i * 0.071f) % 1f
                    val y = viewH * (0.14f + phase * 0.77f)
                    val drift = minDim * 0.018f
                    canvas.drawLine(x, y, x - drift, y + minDim * 0.025f, paint)
                }
            }
            9 -> {
                paint.style = Paint.Style.FILL
                for (i in 0..13) {
                    val x = viewW * (((i * 73 + 11) % 991) / 991f)
                    val y = viewH * (0.16f + ((i * 43 + 7) % 771) / 1000f)
                    val alpha = (45 + 45 * (0.5f + 0.5f * sin(animationTime * 2f + i))).toInt().coerceIn(25, 90)
                    paint.color = Color.argb(alpha, 255, 223, 133)
                    canvas.drawCircle(x, y, max(1.2f * density, minDim * 0.002f), paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawHazards(canvas: Canvas) {
        for((index,hazard) in level.hazards.withIndex()) {
            if(index in collectedPowerUps && hazard.type in setOf("power_up","breakable_wall","switch","key","door")) continue
            val c=hazardCenter(hazard,index);val x=c.x*viewW;val y=c.y*viewH
            val hw=hazard.width*viewW/2f;val h=max(hazard.height*viewH,minDim*0.018f)
            when(hazard.type){
                "teleporter"->{paint.style=Paint.Style.STROKE;paint.strokeWidth=max(2f,density*2f);paint.color=cyan;paint.setShadowLayer(density*10f,0f,0f,cyan);canvas.drawCircle(x,y,max(minDim*0.025f,hw),paint);canvas.drawCircle(x,y,max(minDim*0.014f,hw*0.55f),paint);paint.clearShadowLayer();drawAssetCentered(canvas,"Gameplay Sprites/Portal.png",x,y,max(minDim*0.075f,hw*2.0f))}
                "power_up"->{paint.style=Paint.Style.FILL;paint.color=gold;paint.setShadowLayer(density*9f,0f,0f,gold);canvas.drawCircle(x,y,max(h,hw)*1.18f,paint);paint.clearShadowLayer();drawAssetCentered(canvas,"UI Elements/Star Icon.png",x,y,max(h,hw)*2.2f)}
                "rotating_laser"->{val theta=animationTime*1.35f+index*0.4f;val dx=cos(theta)*hw;val dy=sin(theta)*hw;paint.style=Paint.Style.STROKE;paint.strokeWidth=max(3f,density*3f);paint.color=pink;paint.setShadowLayer(density*10f,0f,0f,pink);canvas.drawLine(x-dx,y-dy,x+dx,y+dy,paint);paint.clearShadowLayer()}
                "gravity_well"->{paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1.5f,density*1.5f);paint.color=Color.rgb(100,135,255);paint.setShadowLayer(density*9f,0f,0f,paint.color);canvas.drawCircle(x,y,max(hw,minDim*0.035f),paint);canvas.drawCircle(x,y,max(hw*1.6f,minDim*0.055f),paint);canvas.drawCircle(x,y,max(hw*2.1f,minDim*0.075f),paint);paint.clearShadowLayer()}
                "conveyor_belt"->{paint.style=Paint.Style.FILL;paint.color=Color.rgb(45,70,95);canvas.drawRoundRect(x-hw,y-h,x+hw,y+h,density*5f,density*5f,paint);paint.color=cyan;for(i in 0..4){val ax=x-hw+(2f*hw)*(i+0.5f)/5f;canvas.drawLine(ax-density*3f,y-density*3f,ax+density*3f,y+density*3f,paint)}}
                "switch"->{paint.style=Paint.Style.STROKE;paint.strokeWidth=max(2f,density*2f);paint.color=gold;canvas.drawCircle(x,y,max(minDim*0.025f,hw),paint);drawAssetCentered(canvas,"Gameplay Sprites/Switch.png",x,y,minDim*0.07f)}
                "key"->{paint.style=Paint.Style.STROKE;paint.strokeWidth=max(2f,density*2f);paint.color=gold;paint.setShadowLayer(density*6f,0f,0f,gold);canvas.drawCircle(x,y,max(minDim*0.015f,hw*0.4f),paint);canvas.drawLine(x+hw*0.3f,y,x+hw*1.8f,y,paint);canvas.drawLine(x+hw*1.25f,y,x+hw*1.25f,y+h,paint);paint.clearShadowLayer()}
                "door"->{paint.style=Paint.Style.FILL;paint.color=if(hasLevelKey) cyan else Color.rgb(115,65,70);canvas.drawRoundRect(x-hw,y-h*1.6f,x+hw,y+h*1.6f,density*4f,density*4f,paint);paint.color=gold;canvas.drawCircle(x+hw*0.5f,y,density*2f,paint)}
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
        val r = minDim * when (enemy.kind) {
            1, 7 -> 0.033f
            2 -> if (enemy.canSplit) 0.031f else 0.020f
            3 -> 0.052f
            5 -> 0.038f
            6 -> 0.034f
            8 -> 0.040f
            else -> 0.030f
        }
        val path = when (enemy.kind) {
            1, 7, 8 -> "Gameplay Sprites/Enemy Turret.png"
            2 -> "Gameplay Sprites/Enemy Slime.png"
            3 -> "Gameplay Sprites/Enemy Turret.png"
            6 -> "Gameplay Sprites/Portal.png"
            else -> "Gameplay Sprites/Enemy Drone.png"
        }
        val drawSize = when (enemy.kind) { 3 -> r * 3.2f; 5 -> r * 2.7f; 6 -> r * 2.8f; 8 -> r * 2.6f; else -> r * 2.35f }
        val isTurret = enemy.kind == 1 || enemy.kind == 3 || enemy.kind == 7 || enemy.kind == 8
        if (enemySkin > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, density * 2.2f)
            paint.color = enemySkinColor()
            paint.setShadowLayer(density * 8f, 0f, 0f, enemySkinColor())
            canvas.drawCircle(x, y, drawSize * 0.68f, paint)
            paint.clearShadowLayer()
        }
        if (isTurret) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, density * 2f)
            paint.color = if (enemy.health > 1) gold else cyan
            paint.setShadowLayer(density * 9f, 0f, 0f, paint.color)
            canvas.drawCircle(x, y, drawSize * 0.61f, paint)
            paint.clearShadowLayer()
        }
        drawAssetCentered(canvas, path, x, y, drawSize)
        if (isTurret) drawText(
            canvas,
            enemy.health.toString(),
            x / viewW,
            (y + drawSize * 0.63f) / viewH,
            0.017f,
            if (enemy.health > 1) gold else cyan,
            Paint.Align.CENTER
        )
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
        paint.color = bladeSkinColor()
        paint.setShadowLayer(density * 15f, 0f, 0f, bladeSkinColor())
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
        else if (screen == GameScreen.FAILED) drawAssetCover(canvas, "Backgrounds & Environments/Game Over Background.png", RectF(0f, 0f, viewW, viewH), 238)
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
            drawText(canvas, "BEST: " + prefs.getInt(bestStarsKey(levelId), earnedStars) + "/3 STARS • SHOTS: " + bestShots + " • +" + lastCoinsEarned + " COINS",
                0.5f, 0.510f, 0.013f, gold, Paint.Align.CENTER)
        }
    }


    private fun bladeSkinColor(): Int = when (bladeSkin) {
        1 -> Color.rgb(255, 105, 120)
        2 -> Color.rgb(118, 255, 138)
        3 -> Color.rgb(255, 214, 90)
        4 -> Color.rgb(190, 118, 255)
        5 -> Color.rgb(255, 255, 255)
        else -> cyan
    }

    private fun shieldSkinColor(): Int = when (shieldSkin) {
        1 -> Color.rgb(255, 112, 112)
        2 -> Color.rgb(116, 255, 145)
        3 -> Color.rgb(255, 219, 90)
        4 -> Color.rgb(184, 128, 255)
        5 -> Color.rgb(255, 255, 255)
        else -> cyan
    }

    private fun enemySkinColor(): Int = when (enemySkin) {
        1 -> Color.rgb(255, 88, 90)
        2 -> Color.rgb(170, 110, 255)
        3 -> Color.rgb(255, 210, 80)
        else -> cyan
    }

    private fun selectMode(mode: String, startImmediately: Boolean = true) {
        gameMode = mode
        prefs.edit().putString("game_mode", mode).apply()
        if (startImmediately) {
            val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
            val startId = if (mode == "boss_rush" && unlocked >= 10) (unlocked / 10 * 10).coerceIn(10, levels.size) else unlocked
            startLevel(startId)
        } else screen = GameScreen.MENU
    }

    private fun startChallenge(weekly: Boolean) {
        val unlocked = prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)
        val calendar = java.util.Calendar.getInstance()
        val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val seed = if (weekly) day / 7 + calendar.get(java.util.Calendar.YEAR) * 3 else day + calendar.get(java.util.Calendar.YEAR)
        val stage = (seed % unlocked) + 1
        gameMode = if (weekly) "weekly" else "daily"
        prefs.edit().putString("game_mode", gameMode).apply()
        startLevel(stage)
    }

    private fun saveEditorDraft(): String {
        val hazard = editorHazards[editorHazardId]
        val draft = "{\"name\":\"My Arena\",\"targets\":" + editorTargetCount +
            ",\"hazard\":\"" + hazard + "\",\"seed\":" + prefs.getInt("procedural_seed", 23) + "}"
        prefs.edit().putString("custom_level_draft", draft).putInt("editor_target_count", editorTargetCount)
            .putInt("editor_hazard", editorHazardId).apply()
        return draft
    }

    private fun showEditorSharingDialog() {
        AlertDialog.Builder(context)
            .setTitle("Custom level draft")
            .setItems(arrayOf("Share / export draft", "Import draft from clipboard")) { _, choice ->
                if (choice == 0) shareEditorDraft() else importEditorDraftFromClipboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importEditorDraftFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            val data = org.json.JSONObject(text)
            editorTargetCount = data.optInt("targets", 6).coerceIn(1, 12)
            editorHazardId = editorHazards.indexOf(data.optString("hazard", "NONE").uppercase()).coerceAtLeast(0)
            val seed = data.optInt("seed", 23)
            prefs.edit().putString("custom_level_draft", data.toString())
                .putInt("editor_target_count", editorTargetCount).putInt("editor_hazard", editorHazardId)
                .putInt("procedural_seed", seed).apply()
            showMessage("CUSTOM DRAFT IMPORTED")
            invalidate()
        } catch (_: Exception) {
            showMessage("COPY A BOUNCE BLADE DRAFT BEFORE IMPORTING")
        }
    }

    private fun shareEditorDraft() {
        val draft = saveEditorDraft()
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Bounce Blade custom arena")
                putExtra(android.content.Intent.EXTRA_TEXT, draft)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share custom arena"))
        } catch (_: Exception) {
            showMessage("DRAFT SAVED LOCALLY")
        }
    }

    private fun drawFeatures(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "FEATURE HUB", 0.5f, 0.16f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "OFFLINE MODES • PROGRESSION • PERSONALIZATION", 0.5f, 0.205f, 0.017f, cyan, Paint.Align.CENTER)
        val items = listOf("GAME MODES", "DAILY / WEEKLY CHALLENGE", "ACHIEVEMENTS", "PROFILE & STATS",
            "BLADE / SHIELD COSMETICS", "LEVEL LAB & SHARE", "ACCESSIBILITY & AUDIO", "EXTRAS / SCORES / REPLAY")
        items.forEachIndexed { i, label ->
            val top = 0.235f + i * 0.065f
            drawButton(canvas, 0.12f, top, 0.88f, top + 0.058f, label, if (i % 2 == 0) cyan else gold)
        }
        drawButton(canvas, 0.24f, 0.785f, 0.76f, 0.865f, "BACK", pink)
    }

    private fun drawModes(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "GAME MODES", 0.5f, 0.16f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "ACTIVE: " + gameMode.replace('_', ' ').uppercase(), 0.5f, 0.205f, 0.020f, gold, Paint.Align.CENTER)
        val modes = listOf(
            "campaign" to "CAMPAIGN • 100 STAGES",
            "endless" to "ENDLESS • LOOP FOREVER",
            "time_attack" to "TIME ATTACK • 60 SECONDS",
            "zen" to "ZEN / PRACTICE • NO RUN LOST",
            "boss_rush" to "BOSS RUSH • ARMORED TARGETS",
            "speedrun" to "SPEEDRUN • TRACK YOUR TIME",
            "procedural" to "PROCEDURAL • RANDOM ARENA",
            "local_coop" to "LOCAL CO-OP • 2 TOUCHES"
        )
        modes.forEachIndexed { i, pair ->
            val top = 0.245f + i * 0.067f
            val label = (if (gameMode == pair.first) "✓  " else "") + pair.second
            drawButton(canvas, 0.10f, top, 0.90f, top + 0.067f, label, if (i % 2 == 0) cyan else gold)
        }
        drawButton(canvas, 0.24f, 0.80f, 0.76f, 0.88f, "BACK", pink)
    }

    private fun drawChallenges(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        val calendar = java.util.Calendar.getInstance()
        val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        drawText(canvas, "CHALLENGES", 0.5f, 0.22f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "TODAY'S ROTATION  #" + (day % 50 + 1), 0.5f, 0.30f, 0.022f, cyan, Paint.Align.CENTER)
        drawButton(canvas, 0.15f, 0.365f, 0.85f, 0.455f, "START DAILY CHALLENGE", gold)
        drawText(canvas, "BEST DAILY SCORE  " + prefs.getInt("daily_best_score", 0), 0.5f, 0.485f, 0.019f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.15f, 0.525f, 0.85f, 0.615f, "START WEEKLY CHALLENGE", pink)
        drawText(canvas, "BEST WEEKLY SCORE  " + prefs.getInt("weekly_best_score", 0), 0.5f, 0.645f, 0.019f, muted, Paint.Align.CENTER)
        drawText(canvas, "Challenges are seeded locally; no server is required.", 0.5f, 0.71f, 0.016f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.24f, 0.79f, 0.76f, 0.875f, "BACK", cyan)
    }

    private fun achievementLines(): List<Pair<String, Boolean>> = listOf(
        "FIRST CLEAR" to (prefs.getInt("total_clears", 0) >= 1),
        "TEN CLEARS" to (prefs.getInt("total_clears", 0) >= 10),
        "FIFTY CLEARS" to (prefs.getInt("total_clears", 0) >= 50),
        "STAR COLLECTOR" to (totalBestStarsAfter(0, 0) >= 50),
        "COMBO MASTER" to (bestCombo >= 5 || prefs.getInt("all_time_best_combo", 0) >= 5),
        "BLADE INVESTOR" to (shopItems.any { upgradeLevel(it.id) > 0 }),
        "WORLD CONQUEROR" to (prefs.getInt("unlocked_level", 1) >= levels.size)
    )

    private fun drawAchievements(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "ACHIEVEMENTS", 0.5f, 0.19f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        achievementLines().forEachIndexed { i, entry ->
            val y = 0.30f + i * 0.065f
            drawText(canvas, if (entry.second) "✓  " + entry.first else "◇  " + entry.first,
                0.18f, y, 0.023f, if (entry.second) gold else muted)
            drawText(canvas, if (entry.second) "UNLOCKED" else "LOCKED", 0.82f, y, 0.017f,
                if (entry.second) cyan else muted, Paint.Align.RIGHT)
        }
        drawText(canvas, "Achievements and best times are stored on this device.", 0.5f, 0.785f, 0.016f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.24f, 0.82f, 0.76f, 0.90f, "BACK", cyan)
    }

    private fun drawStats(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        val seconds = prefs.getLong("total_play_seconds", 0L)
        drawText(canvas, playerName, 0.5f, 0.18f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "BLADE RUNNER  •  OFFLINE PROFILE", 0.5f, 0.245f, 0.021f, cyan, Paint.Align.CENTER)
        val lines = listOf(
            "RUNS STARTED  " + prefs.getInt("total_runs", 0),
            "STAGES CLEARED  " + prefs.getInt("total_clears", 0),
            "FAILED RUNS  " + prefs.getInt("total_fails", 0),
            "TOTAL PLAY TIME  " + formatTime(seconds.toFloat()),
            "BEST STARS  " + totalBestStarsAfter(0, 0) + "/" + (levels.size * 3),
            "COINS  " + coinBalance() + "    BEST COMBO  " + max(bestCombo, prefs.getInt("all_time_best_combo", 0))
        )
        lines.forEachIndexed { i, text -> drawText(canvas, text, 0.18f, 0.34f + i * 0.065f, 0.021f, if (i % 2 == 0) Color.WHITE else muted) }
        drawText(canvas, "Cloud profile sync needs a configured online service.", 0.5f, 0.77f, 0.015f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.24f, 0.77f, 0.76f, 0.835f, "EDIT PROFILE NAME", gold)
        drawButton(canvas, 0.24f, 0.85f, 0.76f, 0.915f, "BACK", cyan)
    }

    private fun drawCustomize(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "BLADE & SHIELD LAB", 0.5f, 0.20f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "THEME  " + themeName(), 0.5f, 0.29f, 0.023f, cyan, Paint.Align.CENTER)
        drawButton(canvas, 0.14f, 0.33f, 0.86f, 0.415f, "CYCLE THEME", cyan)
        val nextBlade = (bladeSkin + 1) % 6
        val bladeUnlocked = nextBlade == 0 || prefs.getBoolean("blade_skin_unlocked_" + nextBlade, false)
        drawText(canvas, "BLADE SKIN  " + (bladeSkin + 1) + "/6", 0.5f, 0.455f, 0.021f, bladeSkinColor(), Paint.Align.CENTER)
        drawButton(canvas, 0.14f, 0.475f, 0.86f, 0.545f, if (bladeUnlocked) "NEXT BLADE SKIN" else "UNLOCK BLADE SKIN • 150 COINS", gold)
        val nextShield = (shieldSkin + 1) % 6
        val shieldUnlocked = nextShield == 0 || prefs.getBoolean("shield_skin_unlocked_" + nextShield, false)
        drawText(canvas, "SHIELD SKIN  " + (shieldSkin + 1) + "/6", 0.5f, 0.585f, 0.021f, shieldSkinColor(), Paint.Align.CENTER)
        drawButton(canvas, 0.14f, 0.605f, 0.86f, 0.675f, if (shieldUnlocked) "NEXT SHIELD SKIN" else "UNLOCK SHIELD SKIN • 150 COINS", pink)
        drawText(canvas, "ENEMY STYLE  " + (enemySkin + 1) + "/4", 0.5f, 0.715f, 0.021f, enemySkinColor(), Paint.Align.CENTER)
        val nextEnemy = (enemySkin + 1) % 4
        val enemyUnlocked = nextEnemy == 0 || prefs.getBoolean("enemy_skin_unlocked_" + nextEnemy, false)
        drawButton(canvas, 0.14f, 0.735f, 0.86f, 0.80f, if (enemyUnlocked) "NEXT ENEMY STYLE" else "UNLOCK ENEMY STYLE • 200 COINS", cyan)
        drawButton(canvas, 0.24f, 0.845f, 0.76f, 0.915f, "BACK", cyan)
    }

    private fun drawEditor(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "LEVEL LAB", 0.5f, 0.18f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "CREATE A LOCAL ARENA DRAFT", 0.5f, 0.245f, 0.020f, cyan, Paint.Align.CENTER)
        drawButton(canvas, 0.15f, 0.29f, 0.85f, 0.365f, "TARGET COUNT: " + editorTargetCount + " (TAP TO CYCLE)", gold)
        drawButton(canvas, 0.15f, 0.39f, 0.85f, 0.465f, "HAZARD: " + editorHazards[editorHazardId], pink)
        drawButton(canvas, 0.15f, 0.50f, 0.85f, 0.575f, "SAVE DRAFT", cyan)
        drawButton(canvas, 0.15f, 0.605f, 0.85f, 0.68f, "IMPORT / SHARE DRAFT", gold)
        drawButton(canvas, 0.15f, 0.71f, 0.85f, 0.785f, "PLAY TEST", pink)
        drawText(canvas, "Saved draft: " + if (prefs.contains("custom_level_draft")) "YES" else "NO", 0.5f, 0.815f, 0.017f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.24f, 0.85f, 0.76f, 0.925f, "BACK", cyan)
    }

    private fun drawAccessibility(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "ACCESSIBILITY & PERFORMANCE", 0.5f, 0.15f, 0.048f, Color.WHITE, Paint.Align.CENTER)
        val options = listOf(
            "HIGH CONTRAST: " + if (highContrast) "ON" else "OFF",
            "COLORBLIND PALETTE: " + if (colorblindMode) "ON" else "OFF",
            "REDUCED MOTION: " + if (reducedMotion) "ON" else "OFF",
            "LEFT-HANDED CONTROL: " + if (leftHandedMode) "ON" else "OFF",
            "ONE-HANDED MODE: " + if (oneHandedMode) "ON" else "OFF",
            "LARGE UI: " + if (largeUi) "ON" else "OFF",
            "BATTERY SAVER: " + if (batterySaver) "ON" else "OFF",
            "DYNAMIC DIFFICULTY: " + if (dynamicDifficulty) "ON" else "OFF",
            "VIBRATION: " + if (vibrationEnabled) "ON" else "OFF"
        )
        options.forEachIndexed { i, label ->
            val top = 0.185f + i * 0.046f
            drawButton(canvas, 0.12f, top, 0.88f, top + 0.042f, label, if (i % 2 == 0) cyan else gold)
        }
        drawButton(canvas, 0.12f, 0.61f, 0.48f, 0.655f, "HAPTIC LEVEL: " + hapticLevel, pink)
        drawButton(canvas, 0.52f, 0.61f, 0.88f, 0.655f, "LANGUAGE: " + if (persianLanguage) "فارسی" else "ENGLISH", gold)
        drawButton(canvas, 0.12f, 0.665f, 0.48f, 0.71f, "JUKEBOX", pink)
        drawButton(canvas, 0.52f, 0.665f, 0.88f, 0.71f, "HELP", cyan)
        drawButton(canvas, 0.24f, 0.72f, 0.76f, 0.775f, "BACK TO FEATURES", gold)
    }

    private fun drawJukebox(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "SOUNDTRACK PLAYER", 0.5f, 0.22f, 0.060f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "TRACK " + (jukeboxTrackId + 1) + "/4", 0.5f, 0.34f, 0.025f, gold, Paint.Align.CENTER)
        drawText(canvas, musicTracks[jukeboxTrackId].substringAfterLast('/').removeSuffix(".wav"), 0.5f, 0.42f, 0.024f, cyan, Paint.Align.CENTER)
        drawButton(canvas, 0.15f, 0.50f, 0.85f, 0.59f, "NEXT TRACK", pink)
        drawButton(canvas, 0.15f, 0.615f, 0.85f, 0.705f, "SOUND: " + if (soundEnabled) "ON" else "OFF", gold)
        drawText(canvas, "The selected track previews while this screen is open.", 0.5f, 0.75f, 0.016f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.24f, 0.81f, 0.76f, 0.89f, "BACK", cyan)
    }


    private fun saveReplayTrace() {
        if (runTrace.size < 2) return
        val encoded = runTrace.joinToString(";") { point -> point.x.toString() + "," + point.y.toString() }
        prefs.edit().putString("replay_trace_last", encoded)
            .putString("replay_trace_" + levelId, encoded)
            .putInt("replay_last_level", levelId).apply()
    }

    private fun readReplayTrace(): List<PointF> {
        return prefs.getString("replay_trace_last", "").orEmpty().split(';').mapNotNull { token ->
            val coords = token.split(',')
            if (coords.size != 2) null else {
                val x = coords[0].toFloatOrNull()
                val y = coords[1].toFloatOrNull()
                if (x == null || y == null) null else PointF(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
            }
        }
    }

    private fun shareReplayTrace() {
        val trace = prefs.getString("replay_trace_last", "").orEmpty()
        if (trace.isBlank()) { showMessage("CLEAR A LEVEL TO CREATE A REPLAY"); return }
        val payload = "Bounce Blade replay • level " + prefs.getInt("replay_last_level", 1) + "\n" + trace
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Bounce Blade replay trace")
                putExtra(android.content.Intent.EXTRA_TEXT, payload)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Export replay"))
        } catch (_: Exception) { showMessage("REPLAY IS SAVED LOCALLY") }
    }

    private fun savePhoto(): Boolean {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        return try {
            drawWallGuardGame(Canvas(bitmap))
            val fileName = "BounceBlade_" + System.currentTimeMillis() + ".png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Bounce Blade")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                val output = context.contentResolver.openOutputStream(uri) ?: return false
                output.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "BounceBlade")
                if (!directory.exists() && !directory.mkdirs()) return false
                FileOutputStream(File(directory, fileName)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
        } catch (_: Exception) { false } finally { bitmap.recycle() }
    }

    private fun drawExtras(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "EXTRAS & COMMUNITY", 0.5f, 0.19f, 0.056f, Color.WHITE, Paint.Align.CENTER)
        val items = listOf("LOCAL LEADERBOARD", "REPLAY / GHOST TRACE", "PHOTO MODE", "SEASONAL EVENT / PASS", "ONLINE SERVICES / CLOUD")
        items.forEachIndexed { i, label ->
            val top = 0.26f + i * 0.092f
            drawButton(canvas, 0.12f, top, 0.88f, top + 0.072f, label, if (i % 2 == 0) cyan else gold)
        }
        drawButton(canvas, 0.24f, 0.76f, 0.76f, 0.845f, "BACK", pink)
    }

    private fun drawLocalScores(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "LOCAL LEADERBOARD", 0.5f, 0.18f, 0.055f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, "OFFLINE • DEVICE ONLY • " + difficultyLabel(), 0.5f, 0.235f, 0.018f, cyan, Paint.Align.CENTER)
        val scores = (1..levels.size).map { id ->
            Triple(id, prefs.getInt("best_score_" + id + "_difficulty_" + difficulty, 0), prefs.getInt(bestStarsKey(id), 0))
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8)
        if (scores.isEmpty()) drawText(canvas, "FINISH A STAGE TO SET YOUR FIRST SCORE.", 0.5f, 0.47f, 0.024f, muted, Paint.Align.CENTER)
        else scores.forEachIndexed { index, row ->
            val y = 0.31f + index * 0.052f
            drawText(canvas, (index + 1).toString().padStart(2, '0') + "   STAGE " + row.first, 0.15f, y, 0.020f, Color.WHITE)
            drawText(canvas, row.second.toString() + " PTS", 0.63f, y, 0.020f, gold, Paint.Align.RIGHT)
            drawText(canvas, "★".repeat(row.third), 0.84f, y, 0.020f, cyan, Paint.Align.RIGHT)
        }
        drawButton(canvas, 0.24f, 0.82f, 0.76f, 0.90f, "BACK", cyan)
    }

    private fun difficultyLabel(): String = when (difficulty) { 1 -> "EASY"; 3 -> "HARD"; else -> "NORMAL" }

    private fun drawReplays(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "REPLAY / GHOST TRACE", 0.5f, 0.16f, 0.052f, Color.WHITE, Paint.Align.CENTER)
        val points = readReplayTrace()
        val replayLevel = prefs.getInt("replay_last_level", 0)
        drawText(canvas, if (points.size >= 2) "LAST RUN • STAGE " + replayLevel + " • " + points.size + " TRACE POINTS" else "NO REPLAY SAVED YET",
            0.5f, 0.215f, 0.017f, cyan, Paint.Align.CENTER)
        val area = RectF(viewW * 0.08f, viewH * 0.27f, viewW * 0.92f, viewH * 0.70f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, density * 1.5f)
        paint.color = Color.argb(190, Color.red(cyan), Color.green(cyan), Color.blue(cyan))
        canvas.drawRoundRect(area, density * 12f, density * 12f, paint)
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = area.left + point.x * area.width()
                val y = area.top + point.y * area.height()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = gold
            paint.strokeWidth = max(2.5f, density * 2.5f)
            canvas.drawPath(path, paint)
            val end = points.last()
            canvas.drawCircle(area.left + end.x * area.width(), area.top + end.y * area.height(), density * 4f, paint)
        }
        paint.style = Paint.Style.FILL
        drawButton(canvas, 0.10f, 0.745f, 0.48f, 0.82f, "EXPORT TRACE", gold)
        drawButton(canvas, 0.52f, 0.745f, 0.90f, 0.82f, "BACK", cyan)
        drawText(canvas, "Ghost path is saved locally; export shares the trace data.", 0.5f, 0.865f, 0.015f, muted, Paint.Align.CENTER)
    }

    private fun drawPhotoMode(canvas: Canvas) {
        drawWallGuardGame(canvas)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(205, 3, 6, 18)
        canvas.drawRoundRect(viewW * 0.22f, viewH * 0.68f, viewW * 0.78f, viewH * 0.94f, density * 14f, density * 14f, paint)
        drawText(canvas, "PHOTO MODE", 0.5f, 0.725f, 0.032f, Color.WHITE, Paint.Align.CENTER)
        drawButton(canvas, 0.25f, 0.755f, 0.49f, 0.835f, "SAVE PHOTO", gold)
        drawButton(canvas, 0.51f, 0.755f, 0.75f, 0.835f, "BACK", cyan)
        drawText(canvas, "Saves a PNG in Pictures / Bounce Blade.", 0.5f, 0.885f, 0.014f, muted, Paint.Align.CENTER)
    }

    private fun drawSeasonal(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val season = when (month) { 11, 0, 1 -> "FROST CIRCUIT"; 2, 3, 4 -> "NEON BLOOM"; 5, 6, 7 -> "SOLAR RUSH"; else -> "CRIMSON HARVEST" }
        val clears = prefs.getInt("total_clears", 0)
        val claimed = prefs.getBoolean("season_reward_claimed", false)
        drawText(canvas, "SEASONAL EVENT", 0.5f, 0.20f, 0.058f, Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, season, 0.5f, 0.29f, 0.035f, gold, Paint.Align.CENTER)
        drawText(canvas, "CLEAR STAGES TO ADVANCE THE LOCAL SEASON PASS", 0.5f, 0.37f, 0.018f, cyan, Paint.Align.CENTER)
        drawText(canvas, "PASS LEVEL  " + (clears / 5) + "   •   TOTAL CLEARS  " + clears, 0.5f, 0.445f, 0.023f, Color.WHITE, Paint.Align.CENTER)
        drawButton(canvas, 0.15f, 0.52f, 0.85f, 0.61f, "PLAY EVENT STAGE", pink)
        drawButton(canvas, 0.15f, 0.635f, 0.85f, 0.725f, if (claimed) "REWARD CLAIMED" else "CLAIM 100 BONUS COINS (5 CLEARS)", gold, !claimed && clears >= 5)
        drawButton(canvas, 0.24f, 0.80f, 0.76f, 0.88f, "BACK", cyan)
    }

    private fun drawOnlineFeatures(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "ONLINE / PLATFORM SERVICES", 0.5f, 0.17f, 0.050f, Color.WHITE, Paint.Align.CENTER)
        val lines = listOf(
            "ONLINE LEADERBOARD      NEEDS SERVER",
            "DAILY CHALLENGE SERVER   NEEDS SERVER",
            "ONLINE MULTIPLAYER       NEEDS SERVER",
            "CLOUD SAVE / DEVICE SYNC NEEDS AUTH",
            "CROSS-PLATFORM SAVES     NEEDS BACKEND",
            "ADS / IN-APP PURCHASES   NEEDS STORE SETUP",
            "ANALYTICS / CRASH REPORT NEEDS SDK",
            "BETA CHANNEL / ASSET PACKS NEEDS CONFIG"
        )
        lines.forEachIndexed { i, line -> drawText(canvas, line, 0.10f, 0.265f + i * 0.060f, 0.016f, if (i % 2 == 0) gold else muted) }
        drawText(canvas, "These services need credentials, policy configuration and backend infrastructure.", 0.5f, 0.72f, 0.014f, pink, Paint.Align.CENTER)
        drawButton(canvas, 0.24f, 0.80f, 0.76f, 0.88f, "BACK", cyan)
    }

    private fun drawSettings(canvas: Canvas) {
        drawMenuBackdrop(canvas)
        drawText(canvas, "SETTINGS", 0.5f, 0.29f, 0.07f, Color.WHITE, Paint.Align.CENTER)
        drawButton(canvas, 0.12f, 0.38f, 0.88f, 0.465f, "SOUND: " + if (soundEnabled) "ON" else "OFF", cyan)
        drawButton(canvas, 0.12f, 0.475f, 0.88f, 0.560f, "VIBRATION: " + if (vibrationEnabled) "ON" else "OFF", gold)
        drawButton(canvas, 0.12f, 0.570f, 0.88f, 0.655f, "THEME: " + themeName(), pink)
        drawButton(canvas, 0.12f, 0.680f, 0.88f, 0.765f, "BACK", cyan)
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
            drawButton(canvas, 0.10f, top, 0.90f, top + 0.090f, label, accents[mode - 1])
        }
        drawButton(canvas, 0.20f, 0.755f, 0.80f, 0.845f, "BACK", gold)
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
        drawText(canvas, "Keyboard, gamepad and custom key bindings are supported.", 0.5f, 0.735f, 0.016f, muted, Paint.Align.CENTER)
        drawButton(canvas, 0.10f, 0.78f, 0.48f, 0.87f, "REMAP CONTROLS", gold)
        drawButton(canvas, 0.52f, 0.78f, 0.90f, 0.87f, "BACK", cyan)
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
        paint.color = if (highContrast && color == muted) Color.WHITE else color
        paint.textSize = (size * minDim * (if (largeUi) 1.16f else 1f)).coerceAtLeast(8f * density)
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
        // Use the actual button artwork as-is. Its visible size is constrained by
        // the layout rectangle while its original proportions are preserved.
        val hitRect = RectF(left * viewW, top * viewH, right * viewW, bottom * viewH)
        // Slightly enlarge the button artwork without stretching or changing hit bounds.
        val buttonExpansion = if (
            (right - left) < 0.24f && label != "Ⅱ" &&
            !label.contains("NOVA") && label != "BLADE SHOP"
        ) 0.015f else 0.055f
        val artBounds = RectF(
            hitRect.left - hitRect.width() * buttonExpansion,
            hitRect.top - hitRect.height() * buttonExpansion,
            hitRect.right + hitRect.width() * buttonExpansion,
            hitRect.bottom + hitRect.height() * buttonExpansion
        )
        val buttonBitmap = bitmapAsset("UI Elements/Button.png")
        val visualRect = buttonBitmap?.let { fitAssetRect(it, artBounds) } ?: artBounds
        drawAsset(canvas, "UI Elements/Button.png", artBounds, if (enabled) 255 else 125)

        val iconPath = when {
            label == "Ⅱ" -> "UI Elements/Pause Icon.png"
            label.contains("BACK") -> "UI Elements/Back Icon.png"
            label.startsWith("PLAY") || label == "RESUME" || label.startsWith("NEXT LEVEL") -> "UI Elements/Play Icon.png"
            label.startsWith("RESTART") || label == "RETRY" -> "UI Elements/Restart Icon.png"
            label.contains("LEVEL SELECT") || label == "MAIN MENU" -> "UI Elements/Home Icon.png"
            label.contains("SHOP") -> "UI Elements/Star Icon.png"
            else -> null
        }
        if (iconPath != null) {
            val iconSize = min(visualRect.height() * 0.62f, minDim * 0.060f)
            val iconX = if (label == "Ⅱ") visualRect.centerX() else visualRect.left + iconSize * 0.82f
            drawAssetCentered(canvas, iconPath, iconX, visualRect.centerY(), iconSize, if (enabled) 255 else 120)
            if (label == "Ⅱ") return
        }
        val shift = if (iconPath == null) 0f else minDim * 0.018f
        drawText(
            canvas,
            label,
            (visualRect.centerX() + shift) / viewW,
            visualRect.centerY() / viewH + 0.010f,
            if (label.length > 18) 0.022f else 0.028f,
            if (enabled) color else muted,
            Paint.Align.CENTER
        )
    }

    private fun addBurst(x: Float, y: Float, color: Int, count: Int) {
        val seed = (x.toInt() * 31 + y.toInt() * 17 + count * 13)
        val particleCount = if (reducedMotion) (count / 4).coerceAtLeast(1) else count
        for (i in 0 until particleCount) {
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

    private fun pointToSegmentDistanceSquared(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.0001f) return distanceSquared(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lengthSquared).coerceIn(0f, 1f)
        return distanceSquared(px, py, x1 + t * dx, y1 + t * dy)
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
        if (importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_YES) announceForAccessibility(text)
    }

    private fun haptic() {
        if (!vibrationEnabled || hapticLevel == 0) return
        val feedback = when (hapticLevel) { 1 -> HapticFeedbackConstants.CLOCK_TICK; 2 -> HapticFeedbackConstants.VIRTUAL_KEY; else -> HapticFeedbackConstants.LONG_PRESS }
        performHapticFeedback(feedback)
    }

    private fun showControlMappingDialog() {
        val actions = arrayOf("Move shield up", "Move shield down", "Pause / resume")
        AlertDialog.Builder(context).setTitle("Remap controls").setItems(actions) { _, action ->
            val labels = arrayOf("Arrow Up", "W", "I", "Arrow Down", "S", "K", "Space", "P", "Enter")
            val codes = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_I,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_K,
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ENTER)
            AlertDialog.Builder(context).setTitle("Choose a key").setItems(labels) { _, selection ->
                val code = codes[selection]
                when (action) {
                    0 -> keyMoveUp = code
                    1 -> keyMoveDown = code
                    else -> keyPause = code
                }
                prefs.edit().putInt("key_move_up", keyMoveUp).putInt("key_move_down", keyMoveDown)
                    .putInt("key_pause", keyPause).apply()
                showMessage("KEY BINDING SAVED")
            }.show()
        }.setNegativeButton("Cancel", null).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (screen == GameScreen.PLAYING && wallGuardMode) {
            when (keyCode) {
                keyMoveUp, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_I -> {
                    guardPaddleY = clampGuardPaddleY(guardPaddleY - 0.035f)
                    invalidate()
                    return true
                }
                keyMoveDown, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_K -> {
                    guardPaddleY = clampGuardPaddleY(guardPaddleY + 0.035f)
                    invalidate()
                    return true
                }
                keyPause, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_P -> {
                    screen = GameScreen.PAUSED
                    slowMotion = false
                    invalidate()
                    return true
                }
            }
        } else if (screen == GameScreen.PAUSED &&
            (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER)) {
            screen = GameScreen.PLAYING
            lastFrame = System.nanoTime()
            invalidate()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE && screen == GameScreen.PLAYING && wallGuardMode) {
            val axisY = event.getAxisValue(MotionEvent.AXIS_Y)
            if (abs(axisY) > 0.12f) {
                guardPaddleY = clampGuardPaddleY(guardPaddleY + axisY * 0.025f)
                invalidate()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val nx = event.x / viewW
        val ny = event.y / viewH
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (screen == GameScreen.PLAYING && gameMode == "local_coop") {
                    val index = event.actionIndex
                    secondPointerId = event.getPointerId(index)
                    secondGuardPaddleY = clampGuardPaddleY(event.getY(index) / viewH)
                    slowMotion = false
                } else slowMotion = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                if (liftedId == secondPointerId) secondPointerId = -1
                if (liftedId == primaryPointerId) primaryPointerId = -1
                slowMotion = gameMode != "local_coop" && event.pointerCount > 2
                invalidate()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                primaryPointerId = event.getPointerId(0)
                if (screen != GameScreen.PLAYING) playSfx("UI & Feedback SFX/Button Click.wav")
                when (screen) {
                    GameScreen.MENU -> {
                        when {
                            inRect(nx, ny, 0.10f, 0.456f, 0.90f, 0.523f) -> screen = GameScreen.DIFFICULTY
                            inRect(nx, ny, 0.10f, 0.532f, 0.90f, 0.599f) -> screen = GameScreen.WORLDS
                            inRect(nx, ny, 0.10f, 0.608f, 0.90f, 0.675f) -> screen = GameScreen.FEATURES
                            inRect(nx, ny, 0.10f, 0.684f, 0.90f, 0.751f) -> openShop(GameScreen.MENU)
                            inRect(nx, ny, 0.10f, 0.760f, 0.90f, 0.827f) -> screen = GameScreen.SETTINGS
                        }
                        invalidate()
                        return true
                    }

                    GameScreen.FEATURES -> {
                        when {
                            inRect(nx, ny, 0.12f, 0.235f, 0.88f, 0.293f) -> screen = GameScreen.MODES
                            inRect(nx, ny, 0.12f, 0.300f, 0.88f, 0.358f) -> screen = GameScreen.CHALLENGES
                            inRect(nx, ny, 0.12f, 0.365f, 0.88f, 0.423f) -> screen = GameScreen.ACHIEVEMENTS
                            inRect(nx, ny, 0.12f, 0.430f, 0.88f, 0.488f) -> screen = GameScreen.STATS
                            inRect(nx, ny, 0.12f, 0.495f, 0.88f, 0.553f) -> screen = GameScreen.CUSTOMIZE
                            inRect(nx, ny, 0.12f, 0.560f, 0.88f, 0.618f) -> screen = GameScreen.EDITOR
                            inRect(nx, ny, 0.12f, 0.625f, 0.88f, 0.683f) -> screen = GameScreen.ACCESSIBILITY
                            inRect(nx, ny, 0.12f, 0.690f, 0.88f, 0.748f) -> screen = GameScreen.EXTRAS
                            inRect(nx, ny, 0.24f, 0.785f, 0.76f, 0.865f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.MODES -> {
                        val modes = listOf("campaign", "endless", "time_attack", "zen", "boss_rush", "speedrun", "procedural", "local_coop")
                        val i = modes.indices.firstOrNull { inRect(nx, ny, 0.10f, 0.245f + it * 0.067f, 0.90f, 0.312f + it * 0.067f) }
                        if (i != null) selectMode(modes[i]) else if (inRect(nx, ny, 0.24f, 0.80f, 0.76f, 0.88f)) screen = GameScreen.FEATURES
                        invalidate()
                        return true
                    }
                    GameScreen.CHALLENGES -> {
                        when {
                            inRect(nx, ny, 0.15f, 0.365f, 0.85f, 0.455f) -> startChallenge(false)
                            inRect(nx, ny, 0.15f, 0.525f, 0.85f, 0.615f) -> startChallenge(true)
                            inRect(nx, ny, 0.24f, 0.79f, 0.76f, 0.875f) -> screen = GameScreen.FEATURES
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.ACHIEVEMENTS -> {
                        if (inRect(nx, ny, 0.24f, 0.82f, 0.76f, 0.90f)) screen = GameScreen.FEATURES
                        invalidate()
                        return true
                    }
                    GameScreen.STATS -> {
                        when {
                            inRect(nx, ny, 0.24f, 0.77f, 0.76f, 0.835f) -> {
                                val field = android.widget.EditText(context).apply {
                                    setText(playerName)
                                    setSingleLine(true)
                                    hint = "Player name"
                                }
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Edit local profile")
                                    .setView(field)
                                    .setPositiveButton("Save") { _, _ ->
                                        playerName = field.text.toString().trim().take(24).ifBlank { "BLADE RUNNER" }
                                        prefs.edit().putString("player_name", playerName).apply()
                                        invalidate()
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            inRect(nx, ny, 0.24f, 0.85f, 0.76f, 0.915f) -> screen = GameScreen.FEATURES
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.CUSTOMIZE -> {
                        when {
                            inRect(nx, ny, 0.14f, 0.33f, 0.86f, 0.415f) -> {
                                themeId = (themeId + 1) % 3
                                prefs.edit().putInt("theme_id", themeId).apply()
                                applyTheme()
                            }
                            inRect(nx, ny, 0.14f, 0.475f, 0.86f, 0.545f) -> {
                                val next = (bladeSkin + 1) % 6
                                val key = "blade_skin_unlocked_" + next
                                if (next == 0 || prefs.getBoolean(key, false)) bladeSkin = next
                                else if (coinBalance() >= 150) {
                                    prefs.edit().putInt("coins", coinBalance() - 150).putBoolean(key, true).apply()
                                    bladeSkin = next
                                    showMessage("BLADE SKIN UNLOCKED")
                                } else showMessage("NEED 150 COINS")
                                prefs.edit().putInt("blade_skin", bladeSkin).apply()
                            }
                            inRect(nx, ny, 0.14f, 0.605f, 0.86f, 0.675f) -> {
                                val next = (shieldSkin + 1) % 6
                                val key = "shield_skin_unlocked_" + next
                                if (next == 0 || prefs.getBoolean(key, false)) shieldSkin = next
                                else if (coinBalance() >= 150) {
                                    prefs.edit().putInt("coins", coinBalance() - 150).putBoolean(key, true).apply()
                                    shieldSkin = next
                                    showMessage("SHIELD SKIN UNLOCKED")
                                } else showMessage("NEED 150 COINS")
                                prefs.edit().putInt("shield_skin", shieldSkin).apply()
                            }
                            inRect(nx, ny, 0.14f, 0.735f, 0.86f, 0.80f) -> {
                                val next = (enemySkin + 1) % 4
                                val key = "enemy_skin_unlocked_" + next
                                if (next == 0 || prefs.getBoolean(key, false)) enemySkin = next
                                else if (coinBalance() >= 200) {
                                    prefs.edit().putInt("coins", coinBalance() - 200).putBoolean(key, true).apply()
                                    enemySkin = next
                                    showMessage("ENEMY STYLE UNLOCKED")
                                } else showMessage("NEED 200 COINS")
                                prefs.edit().putInt("enemy_skin", enemySkin).apply()
                            }
                            inRect(nx, ny, 0.24f, 0.845f, 0.76f, 0.915f) -> screen = GameScreen.FEATURES
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.EDITOR -> {
                        when {
                            inRect(nx, ny, 0.15f, 0.29f, 0.85f, 0.365f) -> editorTargetCount = if (editorTargetCount >= 12) 1 else editorTargetCount + 1
                            inRect(nx, ny, 0.15f, 0.39f, 0.85f, 0.465f) -> editorHazardId = (editorHazardId + 1) % editorHazards.size
                            inRect(nx, ny, 0.15f, 0.50f, 0.85f, 0.575f) -> { saveEditorDraft(); showMessage("CUSTOM ARENA SAVED") }
                            inRect(nx, ny, 0.15f, 0.605f, 0.85f, 0.68f) -> showEditorSharingDialog()
                            inRect(nx, ny, 0.15f, 0.71f, 0.85f, 0.785f) -> { gameMode = "editor_test"; prefs.edit().putString("game_mode", gameMode).apply(); startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size)) }
                            inRect(nx, ny, 0.24f, 0.85f, 0.76f, 0.925f) -> screen = GameScreen.FEATURES
                        }
                        prefs.edit().putInt("editor_target_count", editorTargetCount).putInt("editor_hazard", editorHazardId).apply()
                        invalidate()
                        return true
                    }
                    GameScreen.ACCESSIBILITY -> {
                        when {
                            inRect(nx, ny, 0.12f, 0.185f, 0.88f, 0.227f) -> highContrast = !highContrast
                            inRect(nx, ny, 0.12f, 0.231f, 0.88f, 0.273f) -> { colorblindMode = !colorblindMode; applyTheme() }
                            inRect(nx, ny, 0.12f, 0.277f, 0.88f, 0.319f) -> reducedMotion = !reducedMotion
                            inRect(nx, ny, 0.12f, 0.323f, 0.88f, 0.365f) -> leftHandedMode = !leftHandedMode
                            inRect(nx, ny, 0.12f, 0.369f, 0.88f, 0.411f) -> oneHandedMode = !oneHandedMode
                            inRect(nx, ny, 0.12f, 0.415f, 0.88f, 0.457f) -> largeUi = !largeUi
                            inRect(nx, ny, 0.12f, 0.461f, 0.88f, 0.503f) -> batterySaver = !batterySaver
                            inRect(nx, ny, 0.12f, 0.507f, 0.88f, 0.549f) -> dynamicDifficulty = !dynamicDifficulty
                            inRect(nx, ny, 0.12f, 0.553f, 0.88f, 0.595f) -> vibrationEnabled = !vibrationEnabled
                            inRect(nx, ny, 0.12f, 0.61f, 0.48f, 0.655f) -> hapticLevel = (hapticLevel + 1) % 4
                            inRect(nx, ny, 0.52f, 0.61f, 0.88f, 0.655f) -> { persianLanguage = !persianLanguage; prefs.edit().putBoolean("persian_language", persianLanguage).apply() }
                            inRect(nx, ny, 0.12f, 0.665f, 0.48f, 0.71f) -> screen = GameScreen.JUKEBOX
                            inRect(nx, ny, 0.52f, 0.665f, 0.88f, 0.71f) -> screen = GameScreen.HELP
                            inRect(nx, ny, 0.24f, 0.72f, 0.76f, 0.775f) -> screen = GameScreen.FEATURES
                        }
                        prefs.edit().putBoolean("high_contrast", highContrast).putBoolean("colorblind_mode", colorblindMode)
                            .putInt("haptic_level", hapticLevel).putBoolean("reduced_motion", reducedMotion)
                            .putBoolean("left_handed", leftHandedMode).putBoolean("one_handed_mode", oneHandedMode)
                            .putBoolean("large_ui", largeUi).putBoolean("battery_saver", batterySaver)
                            .putBoolean("dynamic_difficulty", dynamicDifficulty).putBoolean("vibration_enabled", vibrationEnabled).apply()
                        invalidate()
                        return true
                    }
                    GameScreen.JUKEBOX -> {
                        when {
                            inRect(nx, ny, 0.15f, 0.50f, 0.85f, 0.59f) -> {
                                jukeboxTrackId = (jukeboxTrackId + 1) % musicTracks.size
                                prefs.edit().putInt("jukebox_track", jukeboxTrackId).apply()
                                currentMusicPath = null
                            }
                            inRect(nx, ny, 0.15f, 0.615f, 0.85f, 0.705f) -> {
                                soundEnabled = !soundEnabled
                                prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
                            }
                            inRect(nx, ny, 0.24f, 0.81f, 0.76f, 0.89f) -> screen = GameScreen.ACCESSIBILITY
                        }
                        invalidate()
                        return true
                    }

                    GameScreen.EXTRAS -> {
                        when {
                            inRect(nx, ny, 0.12f, 0.26f, 0.88f, 0.332f) -> screen = GameScreen.LOCAL_SCORES
                            inRect(nx, ny, 0.12f, 0.352f, 0.88f, 0.424f) -> screen = GameScreen.REPLAYS
                            inRect(nx, ny, 0.12f, 0.444f, 0.88f, 0.516f) -> screen = GameScreen.PHOTO_MODE
                            inRect(nx, ny, 0.12f, 0.536f, 0.88f, 0.608f) -> screen = GameScreen.SEASONAL
                            inRect(nx, ny, 0.12f, 0.628f, 0.88f, 0.700f) -> screen = GameScreen.ONLINE_FEATURES
                            inRect(nx, ny, 0.24f, 0.76f, 0.76f, 0.845f) -> screen = GameScreen.FEATURES
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.LOCAL_SCORES -> {
                        if (inRect(nx, ny, 0.24f, 0.82f, 0.76f, 0.90f)) screen = GameScreen.EXTRAS
                        invalidate()
                        return true
                    }
                    GameScreen.REPLAYS -> {
                        when {
                            inRect(nx, ny, 0.10f, 0.745f, 0.48f, 0.82f) -> shareReplayTrace()
                            inRect(nx, ny, 0.52f, 0.745f, 0.90f, 0.82f) -> screen = GameScreen.EXTRAS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.PHOTO_MODE -> {
                        when {
                            inRect(nx, ny, 0.25f, 0.755f, 0.49f, 0.835f) -> showMessage(if (savePhoto()) "PHOTO SAVED" else "COULD NOT SAVE PHOTO")
                            inRect(nx, ny, 0.51f, 0.755f, 0.75f, 0.835f) -> screen = GameScreen.EXTRAS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.SEASONAL -> {
                        when {
                            inRect(nx, ny, 0.15f, 0.52f, 0.85f, 0.61f) -> {
                                gameMode = "seasonal"
                                prefs.edit().putString("game_mode", gameMode).apply()
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.15f, 0.635f, 0.85f, 0.725f) -> {
                                if (!prefs.getBoolean("season_reward_claimed", false) && prefs.getInt("total_clears", 0) >= 5) {
                                    prefs.edit().putBoolean("season_reward_claimed", true).putInt("coins", coinBalance() + 100).apply()
                                    showMessage("100 SEASON COINS CLAIMED")
                                }
                            }
                            inRect(nx, ny, 0.24f, 0.80f, 0.76f, 0.88f) -> screen = GameScreen.EXTRAS
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.ONLINE_FEATURES -> {
                        if (inRect(nx, ny, 0.24f, 0.80f, 0.76f, 0.88f)) screen = GameScreen.EXTRAS
                        invalidate()
                        return true
                    }
                    GameScreen.SHOP -> {
                        if (inRect(nx, ny, 0.24f, 0.805f, 0.76f, 0.895f)) {
                            screen = shopReturnScreen
                            if (screen == GameScreen.PLAYING) lastFrame = System.nanoTime()
                        } else {
                            val itemIndex = shopCardSlots.indices.firstOrNull { index ->
                                val slot = shopCardSlots[index]
                                inRect(nx, ny, slot[0], slot[1], slot[2], slot[3])
                            }
                            if (itemIndex != null) purchaseShopItem(itemIndex)
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.WORLDS -> {
                        if(inRect(nx,ny,0.20f,0.83f,0.80f,0.915f)) screen=GameScreen.MENU else {
                            val unlocked=prefs.getInt("unlocked_level",1).coerceIn(1,levels.size)
                            for(w in 0 until CampaignWorlds.WORLD_COUNT){val top=0.375f+w*0.088f;if(inRect(nx,ny,0.10f,top,0.90f,top+0.076f)){if(unlocked>=CampaignWorlds.firstLevelForWorld(w)){selectedWorld=w;screen=GameScreen.LEVELS}else showMessage("CLEAR EARLIER WORLDS FIRST");break}}
                        };invalidate();return true
                    }
                    GameScreen.LEVELS -> {
                        if(inRect(nx,ny,0.20f,0.83f,0.80f,0.915f)) screen=GameScreen.WORLDS else {
                            var changed=false
                            for(mode in 1..3){val left=0.035f+(mode-1)*0.315f;if(inRect(nx,ny,left,0.195f,left+0.30f,0.260f)){difficulty=mode;prefs.edit().putInt("difficulty_stars",difficulty).apply();changed=true;break}}
                            if(!changed){val first=CampaignWorlds.firstLevelForWorld(selectedWorld);for(local in 1..10){val id=first+local-1;if(id>levels.size)continue;val col=(local-1)%4;val row=(local-1)/4;val left=0.035f+col*0.24f;val top=0.29f+row*0.135f;if(inRect(nx,ny,left,top,left+0.23f,top+0.095f)){val unlocked=prefs.getInt("unlocked_level",1).coerceIn(1,levels.size);if(id<=unlocked)startLevel(id)else showMessage("LOCKED");break}}}
                        };invalidate();return true
                    }
                    GameScreen.SETTINGS -> {
                        when {
                            inRect(nx, ny, 0.12f, 0.38f, 0.88f, 0.465f) -> {
                                soundEnabled = !soundEnabled
                                prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
                            }
                            inRect(nx, ny, 0.12f, 0.475f, 0.88f, 0.560f) -> {
                                vibrationEnabled = !vibrationEnabled
                                prefs.edit().putBoolean("vibration_enabled", vibrationEnabled).apply()
                            }
                            inRect(nx, ny, 0.12f, 0.570f, 0.88f, 0.655f) -> {
                                themeId = (themeId + 1) % 3
                                prefs.edit().putInt("theme_id", themeId).apply()
                                applyTheme()
                            }
                            inRect(nx, ny, 0.12f, 0.680f, 0.88f, 0.765f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.DIFFICULTY -> {
                        when {
                            inRect(nx, ny, 0.10f, 0.405f, 0.90f, 0.495f) -> {
                                difficulty = 1
                                prefs.edit().putInt("difficulty_stars", difficulty).apply()
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.10f, 0.515f, 0.90f, 0.605f) -> {
                                difficulty = 2
                                prefs.edit().putInt("difficulty_stars", difficulty).apply()
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.10f, 0.625f, 0.90f, 0.715f) -> {
                                difficulty = 3
                                prefs.edit().putInt("difficulty_stars", difficulty).apply()
                                startLevel(prefs.getInt("unlocked_level", 1).coerceIn(1, levels.size))
                            }
                            inRect(nx, ny, 0.20f, 0.755f, 0.80f, 0.845f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.HELP -> {
                        when {
                            inRect(nx, ny, 0.10f, 0.78f, 0.48f, 0.87f) -> showControlMappingDialog()
                            inRect(nx, ny, 0.52f, 0.78f, 0.90f, 0.87f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.PAUSED -> {
                        when {
                            inRect(nx, ny, 0.18f, 0.425f, 0.82f, 0.515f) -> {
                                screen = GameScreen.PLAYING
                                playSfx("UI & Feedback SFX/Unpause.wav")
                                lastFrame = System.nanoTime()
                            }
                            inRect(nx, ny, 0.18f, 0.535f, 0.82f, 0.625f) -> {
                                startLevel(levelId)
                            }
                            inRect(nx, ny, 0.18f, 0.645f, 0.82f, 0.735f) -> screen = GameScreen.MENU
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.WON -> {
                        when {
                            inRect(nx, ny, 0.18f, 0.50f, 0.82f, 0.60f) -> {
                                if (levelId >= levels.size && gameMode !in setOf("endless", "procedural")) screen = GameScreen.CAMPAIGN_COMPLETE
                                else startLevel(if (levelId >= levels.size) 1 else levelId + 1)
                            }
                            inRect(nx, ny, 0.18f, 0.625f, 0.82f, 0.725f) -> { selectedWorld=CampaignWorlds.indexForLevel(levelId);screen=GameScreen.LEVELS }
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.FAILED -> {
                        when {
                            inRect(nx, ny, 0.18f, 0.50f, 0.82f, 0.60f) -> startLevel(levelId)
                            inRect(nx, ny, 0.18f, 0.625f, 0.82f, 0.725f) -> { selectedWorld=CampaignWorlds.indexForLevel(levelId);screen=GameScreen.LEVELS }
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.CAMPAIGN_COMPLETE -> {
                        when {
                            inRect(nx, ny, 0.15f, 0.565f, 0.85f, 0.665f) -> startLevel(1)
                            inRect(nx, ny, 0.15f, 0.680f, 0.85f, 0.780f) -> { selectedWorld=CampaignWorlds.indexForLevel(levelId);screen=GameScreen.LEVELS }
                        }
                        invalidate()
                        return true
                    }
                    GameScreen.PLAYING -> {
                        if (if (leftHandedMode) inRect(nx, ny, 0.02f, 0.790f, 0.225f, 0.935f)
                            else inRect(nx, ny, 0.775f, 0.790f, 0.98f, 0.935f)) {
                            touchingAbility = true
                            if (upgradeLevel("nova") > 0) activateNovaPulse() else openShop(GameScreen.PLAYING)
                            invalidate()
                            return true
                        }
                        if (if (leftHandedMode) inRect(nx, ny, 0.02f, 0.025f, 0.18f, 0.115f)
                            else inRect(nx, ny, 0.82f, 0.025f, 0.98f, 0.115f)) {
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
                if (touchingAbility) return true
                if (screen == GameScreen.PLAYING && gameMode == "local_coop") {
                    for (index in 0 until event.pointerCount) {
                        val id = event.getPointerId(index)
                        val pointerY = event.getY(index) / viewH
                        if (id == primaryPointerId) guardPaddleY = clampGuardPaddleY(pointerY)
                        if (id == secondPointerId) secondGuardPaddleY = clampGuardPaddleY(pointerY)
                    }
                    invalidate()
                    return true
                }
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
                primaryPointerId = -1
                secondPointerId = -1
                if (touchingAbility) {
                    touchingAbility = false
                    invalidate()
                    return true
                }
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
                primaryPointerId = -1
                secondPointerId = -1
                touchingAbility = false
                aiming = false
                slowMotion = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun inRect(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val padX = (right - left) * 0.025f
        val padY = (bottom - top) * 0.025f
        return x >= left - padX && x <= right + padX &&
            y >= top - padY && y <= bottom + padY
    }

    companion object {
        private val TypefaceFactory = object {
            val bold = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
    }
}

