package com.angmarx.bounceblade

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/* ============================================================
   PALETTE
   ============================================================ */
val Cyan = Color(0xFF00F0FF)
val CyanDim = Color(0xFF0099AA)
val BlueBg = Color(0xFF061A2E)
val BlueDark = Color(0xFF020B14)
val Orange = Color(0xFFFFAA00)
val Red = Color(0xFFFF0055)
val Green = Color(0xFF00FF44)
val TextMain = Color(0xFFE0F7FF)
val TextMuted = Color(0xFF6A9DB8)

/* ============================================================
   ACTIVITY
   ============================================================ */
class ComposeArcadeActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility =
            (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        setContent { BounceBladeApp() }
    }
}

/* ============================================================
   AUDIO (procedural beeps via ToneGenerator)
   ============================================================ */
object Sfx {
    private var tg: ToneGenerator? = null
    private fun gen(): ToneGenerator? {
        if (tg == null) {
            tg = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            } catch (e: Exception) {
                null
            }
        }
        return tg
    }

    fun launch() = safe { gen()?.startTone(ToneGenerator.TONE_PROP_BEEP, 70) }
    fun hit() = safe { gen()?.startTone(ToneGenerator.TONE_PROP_ACK, 50) }
    fun catchIt() = safe { gen()?.startTone(ToneGenerator.TONE_PROP_PROMPT, 70) }
    fun lose() = safe { gen()?.startTone(ToneGenerator.TONE_SUP_ERROR, 400) }
    fun win() = safe { gen()?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400) }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }
}

/* ============================================================
   GAME STATE
   ============================================================ */
enum class GState { MENU, PLAYING, PAUSED, WIN, LOSE }

enum class Screen { MENU, PLAYING }

enum class Overlay { NONE, STAGES, PAUSE, VICTORY, DEFEAT, SETTINGS }

class Blade {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    val r = 14f
    var angle = 0f
    var held = true
    var active = false
    var launchTimer = 1f
    val trail = mutableListOf<Pair<Float, Float>>()
}

class Shield {
    var x = 40f
    var y = 0f
    val h = 120f
    var targetY = 0f
}

class Enemy(
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    var vx: Float,
    var vy: Float
) {
    val r = 22f
    var alive = true
    var hitFlash = 0f
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val r: Float
)

class GameEngine {
    // surface
    var cw by mutableStateOf(0f)
    var ch by mutableStateOf(0f)

    // hud / meta
    var stage by mutableStateOf(1)
    var score by mutableStateOf(0)
    var lives by mutableStateOf(3)
    var kills by mutableStateOf(0)
    var totalEnemies = 0
    var timeElapsed = 0f
    var frameTick by mutableStateOf(0L)

    var state by mutableStateOf(GState.MENU)

    val blade = Blade()
    val shield = Shield()
    val enemies = mutableListOf<Enemy>()
    val particles = mutableListOf<Particle>()

    var pointerY = 0f

    private var needsInit = false

    /* ---------------- lifecycle ---------------- */
    fun start(stageNum: Int) {
        stage = stageNum
        score = 0
        lives = 3
        kills = 0
        timeElapsed = 0f
        particles.clear()
        enemies.clear()
        blade.trail.clear()
        totalEnemies = 0
        needsInit = true
        state = GState.PLAYING
    }

    fun pause() {
        if (state == GState.PLAYING) state = GState.PAUSED
    }

    fun resume() {
        if (state == GState.PAUSED) state = GState.PLAYING
    }

    private fun ensureInit() {
        if (!needsInit || cw <= 0f || ch <= 0f) return
        needsInit = false

        shield.y = ch / 2f
        shield.targetY = ch / 2f
        pointerY = ch / 2f

        blade.x = 80f
        blade.y = ch / 2f
        blade.vx = 0f
        blade.vy = 0f
        blade.held = true
        blade.active = false
        blade.launchTimer = 1f
        blade.angle = 0f
        blade.trail.clear()

        enemies.clear()
        val count = 5 + stage * 2
        val hp = 1 + stage / 3
        repeat(count) {
            enemies.add(
                Enemy(
                    x = cw * 0.4f + Random.nextFloat() * (cw * 0.5f),
                    y = 100f + Random.nextFloat() * (ch - 200f).coerceAtLeast(60f),
                    hp = hp,
                    maxHp = hp,
                    vx = (Random.nextFloat() - 0.5f) * 100f,
                    vy = (Random.nextFloat() - 0.5f) * 100f
                )
            )
        }
        totalEnemies = enemies.size
    }

    /* ---------------- update ---------------- */
    fun update(dt: Float) {
        if (state != GState.PLAYING) return
        ensureInit()
        if (cw <= 0f || ch <= 0f) return

        timeElapsed += dt
        frameTick++

        val s = shield
        val b = blade

        // ---- shield follow ----
        s.targetY = pointerY
        s.y += (s.targetY - s.y) * min(1f, dt * 15f)
        s.y = s.y.coerceIn(s.h / 2f, ch - s.h / 2f)

        // ---- blade held ----
        if (b.held) {
            b.launchTimer -= dt
            b.x = 80f
            b.y = s.y
            b.angle += dt * 5f
            if (b.launchTimer <= 0f) {
                b.held = false
                b.active = true
                val alive = enemies.filter { it.alive }
                var ang = 0f
                if (alive.isNotEmpty()) {
                    val t = alive[Random.nextInt(alive.size)]
                    ang = atan2(t.y - b.y, t.x - b.x)
                }
                b.vx = cos(ang) * 800f
                b.vy = sin(ang) * 800f
                Sfx.launch()
            }
            return
        }

        // ---- blade physics ----
        b.x += b.vx * dt
        b.y += b.vy * dt
        b.angle += dt * 20f

        if (b.y < b.r) {
            b.y = b.r
            b.vy *= -1f
        }
        if (b.y > ch - b.r) {
            b.y = ch - b.r
            b.vy *= -1f
        }
        if (b.x > cw - b.r) {
            b.x = cw - b.r
            b.vx *= -1f
        }

        // ---- enemies ----
        for (e in enemies) {
            if (!e.alive) continue

            e.hitFlash -= dt * 3f
            if (e.hitFlash < 0f) e.hitFlash = 0f

            e.x += e.vx * dt
            e.y += e.vy * dt
            if (e.x < cw * 0.3f || e.x > cw - 40f) e.vx *= -1f
            if (e.y < 60f || e.y > ch - 60f) e.vy *= -1f

            val dx = b.x - e.x
            val dy = b.y - e.y
            val rr = b.r + e.r
            if (dx * dx + dy * dy < rr * rr) {
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                b.x = e.x + (dx / dist) * rr
                b.y = e.y + (dy / dist) * rr
                b.vx *= -1.05f

                e.hp--
                e.hitFlash = 1f
                Sfx.hit()
                burst(e.x, e.y, Cyan)

                if (e.hp <= 0) {
                    e.alive = false
                    kills++
                    score += 100
                    burst(e.x, e.y, Orange)
                    if (kills >= totalEnemies) {
                        winStage()
                        return
                    }
                }
            }
        }

        // ---- catch zone ----
        if (b.x - b.r < s.x + 20f && b.vx < 0f) {
            if (abs(b.y - s.y) < s.h / 2f + b.r) {
                b.held = true
                b.active = false
                b.launchTimer = 0.6f
                b.trail.clear()
                score += 50
                Sfx.catchIt()
                burst(s.x + 20f, s.y, Green)
            } else if (b.x < -50f) {
                b.held = true
                b.active = false
                b.launchTimer = 1.2f
                b.trail.clear()
                lives--
                if (lives <= 0) {
                    loseStage()
                    return
                }
            }
        }

        // ---- trail ----
        b.trail.add(b.x to b.y)
        if (b.trail.size > 20) b.trail.removeAt(0)

        // ---- particles ----
        for (i in particles.indices.reversed()) {
            val p = particles[i]
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) particles.removeAt(i)
        }
    }

    private fun burst(x: Float, y: Float, color: Color) {
        repeat(10) {
            val a = Random.nextFloat() * Math.PI.toFloat() * 2f
            val sp = 100f + Random.nextFloat() * 200f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(a) * sp,
                    vy = sin(a) * sp,
                    life = 1f,
                    maxLife = 0.5f + Random.nextFloat() * 0.5f,
                    color = color,
                    r = 2f + Random.nextFloat() * 3f
                )
            )
        }
    }

    private fun winStage() {
        state = GState.WIN
        Sfx.win()
    }

    private fun loseStage() {
        state = GState.LOSE
        Sfx.lose()
    }
}

/* ============================================================
   ROOT COMPOSABLE
   ============================================================ */
@Composable
fun BounceBladeApp() {
    val engine = remember { GameEngine() }
    var screen by remember { mutableStateOf(Screen.MENU) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }

    // React to game state transitions
    LaunchedEffect(engine.state) {
        when (engine.state) {
            GState.PLAYING -> overlay = Overlay.NONE
            GState.PAUSED -> overlay = Overlay.PAUSE
            GState.WIN -> overlay = Overlay.VICTORY
            GState.LOSE -> overlay = Overlay.DEFEAT
            GState.MENU -> overlay = Overlay.NONE
        }
    }

    // Main game loop (driven by the frame clock)
    LaunchedEffect(engine.state) {
        if (engine.state != GState.PLAYING) return@LaunchedEffect
        var last = 0L
        while (engine.state == GState.PLAYING) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    engine.update(dt)
                }
                last = now
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0A1F33), BlueDark),
                    center = Offset.Unspecified
                )
            )
    ) {
        if (screen == Screen.PLAYING) {
            GameCanvas(engine, Modifier.fillMaxSize())
            GameHud(engine) { engine.pause() }
        } else {
            MenuScreen(
                onPlay = {
                    engine.start(1)
                    screen = Screen.PLAYING
                },
                onStageSelect = { overlay = Overlay.STAGES },
                onSettings = { overlay = Overlay.SETTINGS }
            )
        }

        when (overlay) {
            Overlay.STAGES -> StageSelectOverlay(
                onClose = { overlay = Overlay.NONE },
                onPick = { i ->
                    overlay = Overlay.NONE
                    engine.start(i)
                    screen = Screen.PLAYING
                }
            )

            Overlay.PAUSE -> PauseOverlay(
                onResume = { engine.resume() },
                onRestart = { engine.start(engine.stage) },
                onQuit = {
                    engine.state = GState.MENU
                    screen = Screen.MENU
                }
            )

            Overlay.VICTORY -> VictoryOverlay(
                engine = engine,
                onRestart = { engine.start(engine.stage) },
                onNext = { engine.start(engine.stage + 1) }
            )

            Overlay.DEFEAT -> DefeatOverlay(
                engine = engine,
                onRestart = { engine.start(engine.stage) },
                onMenu = {
                    engine.state = GState.MENU
                    screen = Screen.MENU
                }
            )

            Overlay.SETTINGS -> SettingsOverlay(onClose = { overlay = Overlay.NONE })
            Overlay.NONE -> Unit
        }
    }
}

/* ============================================================
   GAME CANVAS
   ============================================================ */
@Composable
fun GameCanvas(engine: GameEngine, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()

    Canvas(
        modifier
            .onSizeChanged {
                engine.cw = it.width.toFloat()
                engine.ch = it.height.toFloat()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let {
                            engine.pointerY = it.position.y
                        }
                    }
                }
            }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = engine.frameTick // snapshot read → invalidates draw every frame
        drawGame(engine, tm)
    }
}

fun DrawScope.drawGame(g: GameEngine, tm: TextMeasurer) {
    val w = size.width
    val h = size.height

    /* ---- grid ---- */
    val grid = Color(0x0D00F0FF)
    var gx = 0f
    while (gx < w) {
        drawLine(grid, Offset(gx, 0f), Offset(gx, h), 1f)
        gx += 50f
    }
    var gy = 0f
    while (gy < h) {
        drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f)
        gy += 50f
    }

    /* ---- left wall ---- */
    drawLine(Cyan.copy(alpha = 0.30f), Offset(30f, 0f), Offset(30f, h), 4f)

    /* ---- enemies ---- */
    for (e in g.enemies) {
        if (!e.alive) continue
        val c = if (e.hitFlash > 0f) Color.White else Cyan
        val ctr = Offset(e.x, e.y)
        drawCircle(Cyan.copy(alpha = 0.08f), e.r * 2.3f, ctr)
        drawCircle(Cyan.copy(alpha = 0.14f), e.r * 1.6f, ctr)
        drawCircle(Cyan.copy(alpha = 0.20f), e.r, ctr)
        drawCircle(c, e.r, ctr, style = Stroke(2f))

        if (e.maxHp > 1) {
            val layout = tm.measure(
                AnnotatedString(e.hp.toString()),
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                layout,
                topLeft = Offset(e.x - layout.size.width / 2f, e.y - layout.size.height / 2f)
            )
        }
    }

    /* ---- particles ---- */
    for (p in g.particles) {
        val a = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(p.color.copy(alpha = a), p.r * a, Offset(p.x, p.y))
    }

    /* ---- shield ---- */
    val s = g.shield
    drawRoundRect(
        Cyan.copy(alpha = 0.18f),
        Offset(s.x - 14f, s.y - s.h / 2f - 8f),
        Size(26f, s.h + 16f),
        CornerRadius(13f)
    )
    drawRoundRect(
        Cyan,
        Offset(s.x - 10f, s.y - s.h / 2f),
        Size(12f, s.h),
        CornerRadius(6f)
    )
    drawRoundRect(
        Color.White,
        Offset(s.x - 5f, s.y - s.h / 2f + 5f),
        Size(4f, s.h - 10f),
        CornerRadius(2f)
    )

    /* ---- blade ---- */
    val b = g.blade
    if (!b.held) {
        for (i in b.trail.indices) {
            val t = b.trail[i]
            val a = (i.toFloat() / b.trail.size) * 0.5f
            drawCircle(
                Cyan.copy(alpha = a * 0.8f),
                b.r * (i.toFloat() / b.trail.size),
                Offset(t.first, t.second)
            )
        }
    }

    drawCircle(Cyan.copy(alpha = 0.18f), b.r * 2.4f, Offset(b.x, b.y))

    rotate(b.angle, pivot = Offset(b.x, b.y)) {
        drawLine(Color.White, Offset(b.x - b.r, b.y), Offset(b.x + b.r, b.y), 3f)
        drawLine(Color.White, Offset(b.x, b.y - b.r), Offset(b.x, b.y + b.r), 3f)
        drawLine(
            Cyan,
            Offset(b.x - b.r * 0.6f, b.y - b.r * 0.6f),
            Offset(b.x + b.r * 0.6f, b.y + b.r * 0.6f),
            1.5f
        )
        drawLine(
            Cyan,
            Offset(b.x + b.r * 0.6f, b.y - b.r * 0.6f),
            Offset(b.x - b.r * 0.6f, b.y + b.r * 0.6f),
            1.5f
        )
    }
}

/* ============================================================
   IN-GAME HUD
   ============================================================ */
@Composable
fun GameHud(engine: GameEngine, onPause: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            Modifier
                .clip(CutCornerShape(bottomEnd = 12.dp))
                .background(Color(0xCC020B14))
                .border(1.dp, Cyan, CutCornerShape(bottomEnd = 12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatBlock("STAGE", engine.stage.toString().padStart(2, '0'), Cyan)
            StatBlock("SCORE", engine.score.toString(), Cyan)
            StatBlock(
                "LIVES",
                "♥".repeat(engine.lives.coerceAtLeast(0)) +
                    "♡".repeat((3 - engine.lives).coerceAtLeast(0)),
                Orange
            )
        }

        Box(
            Modifier
                .size(48.dp)
                .clip(CutCornerShape(10.dp))
                .background(Cyan.copy(alpha = 0.10f))
                .border(1.dp, Cyan, CutCornerShape(10.dp))
                .clickable { onPause() },
            contentAlignment = Alignment.Center
        ) {
            Text("II", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun StatBlock(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = 2.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/* ============================================================
   MAIN MENU
   ============================================================ */
@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onStageSelect: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        /* ---- top hud ---- */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(CutCornerShape(10.dp))
                        .background(Color(0xFF112233))
                        .border(2.dp, Cyan, CutCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Xyra77", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("COMMANDER", color = TextMuted, fontSize = 9.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(110.dp)
                            .height(5.dp)
                            .background(Cyan.copy(alpha = 0.15f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.75f)
                                .background(Orange)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("⚡ 23", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("🔹 135", color = Orange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Box(
                    Modifier
                        .clip(CutCornerShape(8.dp))
                        .border(1.dp, Cyan, CutCornerShape(8.dp))
                        .clickable { }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "SHOP",
                        color = Cyan,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        /* ---- challenge ---- */
        Panel {
            Text(
                "Dead Orbit",
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                "▶ CHALLENGE",
                color = Orange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "08d 23h 15m",
                color = Cyan,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }

        /* ---- tasks ---- */
        Panel {
            Text("TASKS", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            TaskRow("Achieve a 3-match win streak", 0.66f)
            Spacer(Modifier.height(12.dp))
            TaskRow("Eliminate multiple targets with explosives (x10)", 1f)
        }

        /* ---- season ---- */
        Panel {
            Text(
                "▶ SEASON 4",
                color = Orange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "New enemies. Unstable tech.\nClassified operations.\nAdapt or be erased.",
                color = TextMain,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }

        /* ---- apex ---- */
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CutCornerShape(8.dp))
                        .background(Orange),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "High-Level Protocol Engaged. Command authority confirmed. Ready for tasking.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("APEX", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("PARAGON", color = Orange, fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        MenuButton("STAGE SELECT", onClick = onStageSelect)
        MenuButton("SETTINGS", onClick = onSettings)
        MenuButton("PLAY", primary = true, onClick = onPlay)

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val shape = CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BlueBg.copy(alpha = 0.72f))
            .border(1.dp, Cyan.copy(alpha = 0.30f), shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun TaskRow(label: String, progress: Float) {
    Column {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFF111122))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(Orange)
            )
        }
    }
}

@Composable
fun MenuButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 15.dp, bottomEnd = 15.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(if (primary) Cyan else BlueBg.copy(alpha = 0.85f))
            .then(if (primary) Modifier else Modifier.border(1.dp, CyanDim, shape))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (primary) BlueDark else TextMain,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            fontSize = if (primary) 20.sp else 15.sp
        )
    }
}

/* ============================================================
   OVERLAY SCAFFOLDING
   ============================================================ */
@Composable
fun OverlayBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6020B14)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun ModalCard(
    borderColor: Color = Cyan,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = CutCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .clip(shape)
            .background(BlueBg)
            .border(2.dp, borderColor, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun ModalButton(
    text: String,
    color: Color = Cyan,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = CutCornerShape(topStart = 15.dp, bottomEnd = 15.dp)
    Box(
        modifier
            .height(50.dp)
            .clip(shape)
            .border(1.dp, color, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
    }
}

/* ============================================================
   STAGE SELECT
   ============================================================ */
@Composable
fun StageSelectOverlay(onClose: () -> Unit, onPick: (Int) -> Unit) {
    OverlayBox {
        ModalCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SELECT STAGE",
                    color = Cyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Text(
                    "×",
                    color = TextMuted,
                    fontSize = 26.sp,
                    modifier = Modifier.clickable { onClose() }
                )
            }

            Spacer(Modifier.height(20.dp))

            for (row in 0 until 2) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 5) {
                        val index = row * 5 + col + 1
                        val locked = index > 5
                        StageCard(
                            index = index,
                            locked = locked,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!locked) onPick(index)
                        }
                    }
                }
                if (row == 0) Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(20.dp))

            ModalButton("CLOSE", modifier = Modifier.fillMaxWidth()) { onClose() }
        }
    }
}

@Composable
fun StageCard(index: Int, locked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = CutCornerShape(8.dp)
    val accent = if (locked) Orange else Cyan

    Box(
        modifier
            .height(88.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.7f), shape)
            .clickable { onClick() }
    ) {
        Text(
            index.toString(),
            color = if (locked) Orange else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        )
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (locked) "🔒" else "▶",
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (!locked) {
                Text("★★★", color = Orange, fontSize = 8.sp, letterSpacing = 1.sp)
            }
        }
    }
}

/* ============================================================
   PAUSE
   ============================================================ */
@Composable
fun PauseOverlay(onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    OverlayBox {
        ModalCard {
            Text(
                "PAUSE",
                color = Cyan,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(24.dp))
            ModalButton("RESUME", modifier = Modifier.fillMaxWidth()) { onResume() }
            Spacer(Modifier.height(12.dp))
            ModalButton("RESTART", modifier = Modifier.fillMaxWidth()) { onRestart() }
            Spacer(Modifier.height(12.dp))
            ModalButton("QUIT", color = Red, modifier = Modifier.fillMaxWidth()) { onQuit() }
        }
    }
}

/* ============================================================
   SETTINGS
   ============================================================ */
@Composable
fun SettingsOverlay(onClose: () -> Unit) {
    OverlayBox {
        ModalCard {
            Text(
                "SETTINGS",
                color = Cyan,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "SYSTEM CONFIGURATION\nALL SYSTEMS NOMINAL",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(24.dp))
            ModalButton("CLOSE", modifier = Modifier.fillMaxWidth()) { onClose() }
        }
    }
}

/* ============================================================
   VICTORY / DEFEAT
   ============================================================ */
@Composable
fun VictoryOverlay(engine: GameEngine, onRestart: () -> Unit, onNext: () -> Unit) {
    OverlayBox {
        ModalCard {
            Text("★★★", color = Orange, fontSize = 32.sp, letterSpacing = 8.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "VICTORY",
                color = Cyan,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(20.dp))
            StatRow("Kills", engine.kills.toString(), Cyan)
            StatRow("Score", engine.score.toString(), Cyan)
            StatRow("Best Score", maxOf(23100, engine.score).toString(), Cyan)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModalButton("RESTART", modifier = Modifier.weight(1f)) { onRestart() }
                ModalButton("NEXT", modifier = Modifier.weight(1f)) { onNext() }
            }
        }
    }
}

@Composable
fun DefeatOverlay(engine: GameEngine, onRestart: () -> Unit, onMenu: () -> Unit) {
    OverlayBox {
        ModalCard(borderColor = Red) {
            Text("★★☆", color = Red, fontSize = 32.sp, letterSpacing = 8.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "DEFEAT",
                color = Red,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(20.dp))
            StatRow("Kills", engine.kills.toString(), Red)
            StatRow("Score", engine.score.toString(), Red)
            val m = (engine.timeElapsed / 60).toInt()
            val s = (engine.timeElapsed % 60).toInt()
            StatRow("Clear Time", "$m'${s.toString().padStart(2, '0')}\"", Red)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModalButton("RESTART", color = Red, modifier = Modifier.weight(1f)) { onRestart() }
                ModalButton("MENU", color = Red, modifier = Modifier.weight(1f)) { onMenu() }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMain, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}