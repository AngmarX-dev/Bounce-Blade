package com.angmarx.bounceblade

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var game: BladeView
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        game = BladeView(this)
        setContentView(game)
    }
    override fun onPause() { if (::game.isInitialized) game.pauseFrames(); super.onPause() }
    override fun onResume() { super.onResume(); if (::game.isInitialized) game.resumeFrames() }
    @Deprecated("Deprecated in Android SDK")
    override fun onBackPressed() { if (::game.isInitialized) game.back() else super.onBackPressed() }
    override fun onDestroy() { if (::game.isInitialized) game.close(); super.onDestroy() }
}

internal object Campaign {
    const val COUNT = 50
    val names = listOf("NEON GRID", "CRIMSON CIRCUIT", "AZURE VAULT", "MAGENTA MAZE", "GOLDEN CORE")
    val short = listOf("NEON", "CRIMSON", "AZURE", "MAGENTA", "GOLDEN")
    val colors = intArrayOf(0xFF00FFFF.toInt(), 0xFFFF2B4A.toInt(), 0xFF44AAFF.toInt(), 0xFFFF00FF.toInt(), 0xFFFFDD00.toInt())
    fun world(stage: Int) = ((stage.coerceIn(1, COUNT) - 1) / 10).coerceIn(0, 4)
    fun local(stage: Int) = (stage.coerceIn(1, COUNT) - 1) % 10
    fun enemyCount(stage: Int) = min(18, 3 + world(stage) * 2 + (local(stage) * 0.8f).toInt())
    fun stars(lives: Int, combo: Int) = if (lives >= 3 && combo >= 3) 3 else if (lives >= 2) 2 else 1
}
private enum class Kind { DRONE, TURRET, SLIME }
private enum class HazardKind { SPIKES, LASER, MOVER, STICKY, PORTAL, POWER }
private enum class Power { EXPAND, DOUBLE, SLOW }
private data class Enemy(
    val kind: Kind, var x: Float, var y: Float, var bx: Float, var by: Float,
    var r: Float, var hp: Int, var maxHp: Int, var vx: Float, var vy: Float,
    var phase: Float, val phaseSpeed: Float, val bob: Float, val color: Int,
    var alive: Boolean = true, var flash: Float = 0f
)
private data class Hazard(
    val kind: HazardKind, var x: Float, var y: Float, var w: Float = 0f, var h: Float = 0f,
    var r: Float = 16f, var x2: Float = 0f, var y2: Float = 0f, var timer: Float = 0f,
    var period: Float = 2.5f, var origin: Float = 0f, var range: Float = 0f,
    var taken: Boolean = false, val power: Power = Power.EXPAND
)
private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val r: Float, val color: Int)
private data class Floater(var x: Float, var y: Float, val label: String, val color: Int, var life: Float)

private class BladeView(ctx: Context) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("bounceblade_save_v1", Context.MODE_PRIVATE)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD) }
    private val glowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 1280f, 720f, intArrayOf(0xFF091426.toInt(), 0xFF03040A.toInt(), 0xFF17071C.toInt()), null, Shader.TileMode.CLAMP) }
    private val path = Path()
    private val particles = ArrayList<Particle>()
    private val floaters = ArrayList<Floater>()
    private val trail = ArrayList<PointF>()
    private val tone = try { ToneGenerator(AudioManager.STREAM_MUSIC, 45) } catch (_: Throwable) { null }
    private val rng = Random()
    private var page = 0 // 0 menu, 1 stages, 2 how, 3 playing, 4 pause, 5 win, 6 lose
    private var difficulty = prefs.getInt("difficulty", 1).coerceIn(0, 2)
    private var selectedWorld = 0
    private var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, 50)
    private val stars = IntArray(51) { prefs.getInt("stars_" + it, 0) }
    private var stage = 1
    private var world = 0
    private var enemies = mutableListOf<Enemy>()
    private var hazards = mutableListOf<Hazard>()
    private var score = 0
    private var lives = 3
    private var catches = 0
    private var combo = 1
    private var bestCombo = 1
    private var comboStreak = 0
    private var catchStreak = 0
    private var clock = 0f
    private var bladeX = 66f
    private var bladeY = 360f
    private var vx = 0f
    private var vy = 0f
    private var bladeAngle = 0f
    private var held = true
    private var active = false
    private var launchTimer = .8f
    private var shieldY = 360f
    private var shieldTarget = 360f
    private var shieldH = 110f
    private var flash = 0f
    private var expand = 0f
    private var double = 0f
    private var slow = 0f
    private var hazardCooldown = 0f
    private var toast = ""
    private var toastLeft = 0f
    private var lastFrame = 0L
    private var sx = 1f
    private var ox = 0f
    private var oy = 0f
    private val W = 1280f
    private val H = 720f

    init { isFocusable = true; isClickable = true; keepScreenOn = true }
    fun pauseFrames() { lastFrame = 0L }
    fun resumeFrames() { lastFrame = 0L; invalidate() }
    fun close() { tone?.release() }
    fun back() { page = when (page) { 3 -> 4; 4 -> 3; 0 -> { (context as? Activity)?.finish(); 0 }; else -> 0 }; lastFrame = 0L; invalidate() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= 0 || height <= 0) return
        sx = min(width / W, height / H); ox = (width - W * sx) / 2f; oy = (height - H * sx) / 2f
        c.drawColor(0xFF03040A.toInt())
        val save = c.save(); c.translate(ox, oy); c.scale(sx, sx)
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 1f/60f else ((now-lastFrame)/1e9f).coerceIn(0f,.035f)
        lastFrame = now; clock += dt
        if (page == 3) update(dt)
        when (page) {
            0 -> drawMenu(c)
            1 -> drawStages(c)
            2 -> drawHow(c)
            3 -> drawGame(c)
            4 -> { drawGame(c); overlay(c); frame(c,300f,175f,980f,535f,Campaign.colors[world]); glow(c,"PAUSED",640f,245f,52f,Campaign.colors[world]); button(c,"▶ RESUME",380f,290f,900f,360f,CYAN,true); button(c,"↻ RETRY",380f,380f,620f,448f,CYAN); button(c,"⌂ MENU",660f,380f,900f,448f,MAGENTA) }
            5 -> drawWin(c)
            6 -> drawLose(c)
        }
        c.restoreToCount(save)
        if (page == 0 || page == 3) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = (e.x-ox)/sx; val y = (e.y-oy)/sx
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (page == 3) { if (inside(x,y,1140f,8f,1260f,88f)) page=4 else shieldTarget=y.coerceIn(shieldH/2f,H-shieldH/2f) }
                else tap(x,y)
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> { if (page==3) shieldTarget=y.coerceIn(shieldH/2f,H-shieldH/2f); return true }
            MotionEvent.ACTION_UP -> { performClick(); return true }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    private fun tap(x:Float,y:Float) {
        when(page) {
            0 -> when {
                inside(x,y,330f,230f,950f,318f) -> start(unlocked)
                inside(x,y,320f,350f,620f,422f) -> { selectedWorld=0; page=1 }
                inside(x,y,660f,350f,960f,422f) -> page=2
                inside(x,y,460f,438f,820f,492f) -> { prefs.edit().clear().apply(); for(i in stars.indices) stars[i]=0; unlocked=1; difficulty=1; say("SAVE RESET") }
                y in 520f..590f -> { difficulty=if(x<535f)0 else if(x<745f)1 else 2; prefs.edit().putInt("difficulty",difficulty).apply() }
            }
            1 -> {
                if (inside(x,y,25f,18f,165f,82f)) { page=0; return }
                for (i in 0..4) { val l=104f+i*218f; if(inside(x,y,l,150f,l+210f,208f)){selectedWorld=i;return} }
                for(i in 0..9) { val col=i%5; val row=i/5; val l=270f+col*150f; val t=260f+row*112f; val n=selectedWorld*10+i+1; if(inside(x,y,l,t,l+120f,t+74f)&&n<=unlocked){start(n);return} }
            }
            2 -> if (inside(x,y,25f,18f,165f,82f)||inside(x,y,480f,580f,800f,650f)) page=0
            4 -> when { inside(x,y,380f,290f,900f,360f)->page=3; inside(x,y,380f,380f,620f,448f)->start(stage); inside(x,y,660f,380f,900f,448f)->page=0 }
            5 -> when { stage<50&&inside(x,y,400f,440f,880f,514f)->start(stage+1); inside(x,y,310f,530f,610f,596f)->start(stage); inside(x,y,670f,530f,970f,596f)->page=0 }
            6 -> when { inside(x,y,380f,430f,620f,505f)->start(stage); inside(x,y,660f,430f,900f,505f)->page=0 }
        }
        lastFrame=0L
    }

    private fun start(n:Int) {
        stage=n.coerceIn(1,50); world=Campaign.world(stage)
        val r=Random(stage*9173L+41L); val wi=world; val count=Campaign.enemyCount(stage)
        val pool=mutableListOf(Kind.DRONE); if(wi>=1){pool.add(Kind.TURRET);pool.add(Kind.SLIME)}; if(wi>=2){pool.add(Kind.DRONE);pool.add(Kind.TURRET)}; if(wi>=3){pool.add(Kind.SLIME);pool.add(Kind.TURRET)}
        enemies=ArrayList(count)
        for(i in 0 until count) {
            val x=380f+(i%6)*120f+(r.nextFloat()-.5f)*60f
            val y=150f+(i/6)*130f+(r.nextFloat()-.5f)*60f
            val k=pool[r.nextInt(pool.size)]; val radius=when(k){Kind.TURRET->24f;Kind.SLIME->22f;else->18f}
            var hp=if(k==Kind.TURRET&&wi>=2)2 else 1
            if(wi>=2&&r.nextFloat()<.25f+(stage-1)/49f*.3f) hp+=1+if(wi>=4)1 else 0
            val moving=k!=Kind.TURRET; val speed=.4f+(stage-1)/49f*1.2f+wi*.15f
            enemies.add(Enemy(k,x,y,x,y,radius,hp,hp,if(moving)(if(r.nextBoolean())1f else -1f)*speed*60f else 0f,if(moving)(r.nextFloat()-.5f)*speed*40f else 0f,r.nextFloat()*6.28f,.5f+r.nextFloat()*1.5f,if(k==Kind.SLIME)18f+r.nextFloat()*12f else if(moving)10f else 4f,Campaign.colors[wi]))
        }
        hazards=ArrayList(); val local=Campaign.local(stage); val chance=min(.9f,.12f*wi+local*.05f)
        if(wi>=1&&r.nextFloat()<chance) hazards.add(Hazard(HazardKind.SPIKES,1250f,120f+r.nextFloat()*(H-240f),16f,120f))
        if(wi>=2&&r.nextFloat()<chance*.8f) hazards.add(Hazard(HazardKind.LASER,500f+r.nextFloat()*500f,110f,6f,530f,timer=r.nextFloat()*2f,period=2.2f+r.nextFloat()*1.5f))
        if(wi>=2&&r.nextFloat()<chance*.7f){val a=450f+r.nextFloat()*150f;hazards.add(Hazard(HazardKind.MOVER,a,180f+r.nextFloat()*360f,64f,18f,origin=a,range=220f+r.nextFloat()*60f))}
        if(wi>=3&&r.nextFloat()<.5f) hazards.add(Hazard(HazardKind.STICKY,700f+r.nextFloat()*300f,150f+r.nextFloat()*420f,44f,90f))
        if(wi>=3&&r.nextFloat()<.4f) hazards.add(Hazard(HazardKind.PORTAL,560f,160f+r.nextFloat()*120f,x2=900f+r.nextFloat()*150f,y2=560f-r.nextFloat()*120f,r=26f))
        if(r.nextFloat()<.35f) hazards.add(Hazard(HazardKind.POWER,500f+r.nextFloat()*600f,160f+r.nextFloat()*400f,r=16f,power=Power.values()[r.nextInt(3)]))
        score=0;lives=3;catches=0;combo=1;bestCombo=1;comboStreak=0;catchStreak=0
        shieldY=360f;shieldTarget=360f;shieldH=110f*shieldFactor();bladeX=66f;bladeY=360f;vx=0f;vy=0f;bladeAngle=0f;held=true;active=false;launchTimer=.8f;expand=0f;double=0f;slow=0f;flash=0f;hazardCooldown=0f
        particles.clear();floaters.clear();trail.clear();page=3;lastFrame=0L;sound(ToneGenerator.TONE_PROP_BEEP2,90)
    }
    private fun shieldFactor()=when(difficulty){0->1.35f;1->1f;else->.7f}
    private fun bladeFactor()=when(difficulty){0->.85f;1->1f;else->1.2f}

    private fun update(dt0:Float) {
        val dt=dt0.coerceIn(0f,.035f); expand=max(0f,expand-dt);double=max(0f,double-dt);slow=max(0f,slow-dt);hazardCooldown=max(0f,hazardCooldown-dt);flash=max(0f,flash-dt*4f)
        shieldH=110f*shieldFactor()*if(expand>0f)1.5f else 1f
        shieldY+=(shieldTarget-shieldY)*min(1f,dt*14f);shieldY=shieldY.coerceIn(shieldH/2f,H-shieldH/2f)
        for(e in enemies) if(e.alive){e.phase+=e.phaseSpeed*dt;e.flash=max(0f,e.flash-dt*3f);when(e.kind){
            Kind.DRONE->{e.x+=e.vx*dt;e.y=e.by+sin(e.phase)*e.bob;if(e.x<380f){e.x=380f;e.vx=abs(e.vx)};if(e.x>1220f){e.x=1220f;e.vx=-abs(e.vx)}}
            Kind.SLIME->{e.y+=e.vy*dt;e.x=e.bx+sin(e.phase)*e.bob;if(e.y<100f){e.y=100f;e.vy=abs(e.vy)};if(e.y>640f){e.y=640f;e.vy=-abs(e.vy)}}
            Kind.TURRET->e.y=e.by+sin(e.phase*.5f)*3f
        }}
        for(h in hazards){if(h.kind==HazardKind.MOVER){h.timer+=dt;h.x=h.origin+sin(h.timer*.9f)*h.range};if(h.kind==HazardKind.LASER)h.timer=(h.timer+dt)%h.period}
        if(held){launchTimer-=dt;bladeX=66f;bladeY=shieldY;bladeAngle+=dt*2f;if(launchTimer<=0f)launch();return}
        if(!active)return
        val dx=vx*dt*if(slow>0f).55f else 1f;val dy=vy*dt*if(slow>0f).55f else 1f;val steps=max(1,ceil(sqrt(dx*dx+dy*dy)/12f).toInt());var collided=false
        repeat(steps){ if(collided)return@repeat
            bladeX+=dx/steps;bladeY+=dy/steps
            if(bladeY<16f){bladeY=16f;vy=abs(vy);sound(ToneGenerator.TONE_PROP_BEEP,30);burst(bladeX,bladeY,CYAN,3,100f)}
            if(bladeY>704f){bladeY=704f;vy=-abs(vy);sound(ToneGenerator.TONE_PROP_BEEP,30);burst(bladeX,bladeY,CYAN,3,100f)}
            if(bladeX>1264f){bladeX=1264f;vx=-abs(vx);sound(ToneGenerator.TONE_PROP_BEEP,30)}
            for(e in enemies){if(!e.alive)continue;val ax=bladeX-e.x;val ay=bladeY-e.y;val rr=16f+e.r;if(ax*ax+ay*ay<rr*rr){val d=max(1f,sqrt(ax*ax+ay*ay));bladeX=e.x+ax/d*rr;bladeY=e.y+ay/d*rr;vx=-abs(vx);vy=(vy+(rng.nextFloat()-.5f)*220f).coerceIn(-700f,700f);e.hp--;e.flash=1f;comboStreak++;combo=if(comboStreak>1)min(9,1+comboStreak/2)else 1;bestCombo=max(bestCombo,combo);val pts=50*combo*if(double>0f)2 else 1;score+=pts;floater(e.x,e.y-e.r,"+$pts",if(combo>1)GOLD else CYAN);burst(e.x,e.y,e.color,10,190f);if(e.hp<=0){e.alive=false;score+=100;burst(e.x,e.y,e.color,20,250f);floater(e.x,e.y,"+100",e.color)};sound(ToneGenerator.TONE_PROP_ACK,60);collided=true;break}}
            hitHazards()
        }
        trail.add(PointF(bladeX,bladeY));if(trail.size>28)trail.removeAt(0);bladeAngle+=dt*14f
        for(p in particles){p.x+=p.vx*dt;p.y+=p.vy*dt;p.vx*=.98f;p.vy*=.98f;p.life-=dt};particles.removeAll{it.life<=0f}
        for(f in floaters){f.y-=36f*dt;f.life-=dt};floaters.removeAll{it.life<=0f}
        if(active&&bladeX-16f<=54f&&vx<0f){if(abs(bladeY-shieldY)<shieldH/2f+9.6f)catchBlade()else if(bladeX< -40f)loseBlade()}
        if(active&&bladeX< -60f&&page==3)loseBlade()
        toastLeft=max(0f,toastLeft-dt)
    }
    private fun launch(){val target=enemies.filter{it.alive}.let{if(it.isEmpty())null else it[rng.nextInt(it.size)]};val a=if(target!=null)atan2(target.y-bladeY,target.x-bladeX)else(rng.nextFloat()-.5f)*.6f;val speed=640f*bladeFactor();vx=cos(a)*speed;vy=sin(a)*speed;active=true;held=false;trail.clear();sound(ToneGenerator.TONE_PROP_BEEP2,80)}
    private fun hitHazards(){for(h in hazards){if(h.kind==HazardKind.POWER&&!h.taken){val dx=bladeX-h.x;val dy=bladeY-h.y;if(dx*dx+dy*dy<(16f+h.r)*(16f+h.r)){h.taken=true;when(h.power){Power.EXPAND->expand=8f;Power.DOUBLE->double=10f;Power.SLOW->slow=5f};say(when(h.power){Power.EXPAND->"SHIELD EXPANDED";Power.DOUBLE->"SCORE x2";Power.SLOW->"TIME SLOWED"});burst(h.x,h.y,GOLD,18,200f)}}else if(h.kind==HazardKind.STICKY&&hazardCooldown<=0f&&bladeX>h.x-16f&&bladeX<h.x+h.w+16f&&bladeY>h.y-16f&&bladeY<h.y+h.h+16f){vx=-abs(vx)*.6f;vy*=.4f;hazardCooldown=.4f;burst(bladeX,bladeY,MAGENTA,8,120f)}else if((h.kind==HazardKind.SPIKES||h.kind==HazardKind.MOVER||h.kind==HazardKind.LASER)&&hazardCooldown<=0f){val activeLaser=h.kind!=HazardKind.LASER||h.timer>h.period*.45f;if(activeLaser&&abs(bladeX-h.x)<h.w/2f+16f&&abs(bladeY-h.y)<h.h/2f+16f){vx=-vx;vy=(vy+(rng.nextFloat()-.5f)*150f).coerceIn(-700f,700f);hazardCooldown=.28f;burst(bladeX,bladeY,MAGENTA,8,140f)}}}}
    private fun catchBlade(){active=false;held=true;launchTimer=.55f;trail.clear();catches++;catchStreak++;flash=1f;sound(ToneGenerator.TONE_PROP_ACK,100);burst(55f,shieldY,CYAN,15,220f);burst(55f,shieldY,MAGENTA,8,160f);if(catchStreak>=2){val bonus=25*catchStreak;score+=bonus;floater(60f,shieldY-30f,"CATCH +$bonus",CYAN)};if(enemies.none{it.alive})win()}
    private fun loseBlade(){active=false;held=true;launchTimer=1f;trail.clear();catchStreak=0;comboStreak=0;combo=1;lives--;sound(ToneGenerator.TONE_PROP_NACK,180);burst(20f,bladeY,MAGENTA,22,260f);if(lives<=0)page=6 else say("BLADE LOST - "+lives+" LIVES LEFT")}
    private fun win(){val award=Campaign.stars(lives,bestCombo);stars[stage]=max(stars[stage],award);unlocked=max(unlocked,min(50,stage+1));prefs.edit().putInt("unlocked",unlocked).putInt("stars_"+stage,stars[stage]).putInt("best_"+stage,max(score,prefs.getInt("best_"+stage,0))).apply();page=5;sound(ToneGenerator.TONE_PROP_ACK,180)}
    private fun burst(x:Float,y:Float,color:Int,n:Int,speed:Float){repeat(n){if(particles.size>=480)particles.removeAt(0);val a=rng.nextFloat()*6.28318f;val v=speed*(.3f+rng.nextFloat());val life=.45f+rng.nextFloat()*.6f;particles.add(Particle(x,y,cos(a)*v,sin(a)*v,life,life,1.5f+rng.nextFloat()*3f,color))}}
    private fun floater(x:Float,y:Float,s:String,color:Int){if(floaters.size>80)floaters.removeAt(0);floaters.add(Floater(x,y,s,color,1f))}
    private fun say(s:String){toast=s;toastLeft=1.8f}
    private fun sound(t:Int,d:Int){try{tone?.startTone(t,d)}catch(_:Throwable){}}

    private fun drawMenu(c:Canvas){background(c,null);frame(c,210f,74f,1070f,665f,CYAN);glow(c,"BOUNCE BLADE",640f,145f,64f,WHITE);label(c,"CATCH THE LIGHT  •  SAVE THE GRID",640f,190f,21f,CYAN);bladeIcon(c,1030f,145f,clock*80f);button(c,"▶  START CAMPAIGN",330f,230f,950f,318f,MAGENTA,true);button(c,"▦  STAGE SELECT",320f,350f,620f,422f,CYAN);button(c,"?  HOW TO PLAY",660f,350f,960f,422f,CYAN);button(c,"↻  RESET SAVE",460f,438f,820f,492f,MUTED);label(c,"DIFFICULTY",640f,518f,17f,MUTED);val diffs=listOf("EASY","NORMAL","HARD");for(i in 0..2){val l=340f+i*220f;button(c,diffs[i],l,530f,l+180f,582f,if(i==difficulty)GOLD else 0xFF456174.toInt(),selected=i==difficulty)};label(c,"PROGRESS  "+(1..50).count{stars[it]>0}+" / 50      •      "+(1..50).sumOf{stars[it]}+" STARS",640f,630f,18f,MUTED);label(c,"50 STAGES  •  5 WORLDS  •  NEON ARCADE ACTION",640f,657f,14f,0xFF5D869A.toInt())}
    private fun drawStages(c:Canvas){background(c,null);frame(c,70f,66f,1210f,670f,CYAN);button(c,"← BACK",25f,18f,165f,82f,MUTED);glow(c,"STAGE SELECT",640f,112f,42f,WHITE);label(c,"CHOOSE YOUR WORLD",640f,137f,17f,MUTED);for(i in 0..4){val l=104f+i*218f;button(c,Campaign.short[i],l,150f,l+210f,208f,Campaign.colors[i],selected=i==selectedWorld)};label(c,Campaign.names[selectedWorld]+"  •  STAGES "+(selectedWorld*10+1)+"–"+(selectedWorld*10+10),640f,238f,18f,Campaign.colors[selectedWorld]);for(i in 0..9){val l=270f+(i%5)*150f;val t=260f+(i/5)*112f;val n=selectedWorld*10+i+1;val ok=n<=unlocked;button(c,if(ok)"STAGE $n" else "LOCKED",l,t,l+120f,t+74f,if(stars[n]>0)GOLD else Campaign.colors[selectedWorld],selected=stars[n]>0,disabled=!ok);label(c,if(stars[n]>0)"★".repeat(stars[n])+"☆".repeat(3-stars[n])else if(ok)"READY" else "—",l+60f,t+62f,13f,MUTED)};label(c,"TAP AN UNLOCKED STAGE TO PLAY",640f,610f,17f,MUTED)}
    private fun drawHow(c:Canvas){background(c,null);frame(c,170f,74f,1110f,654f,MAGENTA);button(c,"← BACK",25f,18f,165f,82f,MUTED);glow(c,"HOW TO PLAY",640f,130f,46f,WHITE);val rows=listOf("1. THE BLADE LAUNCHES AUTOMATICALLY FROM THE LEFT.","2. DRAG YOUR FINGER UP OR DOWN TO MOVE THE SHIELD.","3. THE BLADE RICOCHETS OFF ENEMIES AND RETURNS.","4. CATCH THE BLADE — MISS THE SHIELD AND LOSE A LIFE.","5. DESTROY ALL ENEMIES AND CATCH THE BLADE TO CLEAR.","HITS BUILD COMBOS; CATCH STREAKS AWARD BONUS SCORE.","POWER-UPS: SHIELD EXPANSION, SCORE x2, AND SLOW TIME.","COMPLETE STAGES TO EARN UP TO THREE STARS.");rows.forEachIndexed{i,s->label(c,s,640f,207f+i*45f,19f,if(i==5)GOLD else 0xFFB8DCE6.toInt())};label(c,"50 STAGES  •  5 WORLDS  •  EASY / NORMAL / HARD",640f,584f,20f,CYAN);button(c,"BACK TO MENU",480f,600f,800f,654f,MAGENTA)}
    private fun drawGame(c:Canvas){background(c,Campaign.colors[world]);frame(c,45f,100f,1235f,698f,Campaign.colors[world]);fill.color=0xB0060B18.toInt();c.drawRoundRect(26f,10f,1254f,91f,18f,18f,fill);label(c,"BOUNCE BLADE",130f,40f,24f,WHITE);label(c,Campaign.names[world]+"  •  STAGE "+stage+"/50",500f,39f,20f,Campaign.colors[world]);label(c,"SCORE  "+score,150f,72f,18f,GOLD);label(c,"COMBO  x"+combo,488f,72f,18f,MAGENTA);label(c,"LIVES  "+"♥".repeat(lives)+"♡".repeat(max(0,3-lives)),850f,72f,18f,MAGENTA);button(c,"Ⅱ",1140f,8f,1260f,88f,MAGENTA);line.color=0x2239C9E9;line.strokeWidth=1f;for(x in 100..1200 step 196)c.drawLine(x.toFloat(),110f,x.toFloat(),688f,line);for(y in 180..680 step 72)c.drawLine(52f,y.toFloat(),1228f,y.toFloat(),line);hazards.forEach{drawHazard(c,it)};enemies.filter{it.alive}.forEach{drawEnemy(c,it)};particles.forEach{p->fill.color=Color.argb((255*p.life/p.max).toInt().coerceIn(0,255),Color.red(p.color),Color.green(p.color),Color.blue(p.color));c.drawCircle(p.x,p.y,p.r,fill)};trail.forEachIndexed{i,p->if(i>0){val q=trail[i-1];line.color=Color.argb((30+i*200/trail.size).coerceIn(0,240),0,255,255);line.strokeWidth=3f+i*.25f;c.drawLine(q.x,q.y,p.x,p.y,line)}};drawShield(c);drawBlade(c);floaters.forEach{f->label(c,f.label,f.x,f.y,21f,Color.argb((255*f.life).toInt().coerceIn(0,255),Color.red(f.color),Color.green(f.color),Color.blue(f.color)))};val power=listOf(if(expand>0f)"SHIELD +" else "",if(double>0f)"SCORE x2" else "",if(slow>0f)"SLOW TIME" else "").filter{it.isNotEmpty()};if(power.isNotEmpty())label(c,power.joinToString("  •  "),640f,680f,16f,GOLD);if(toastLeft>0f){fill.color=0xE90A1324.toInt();c.drawRoundRect(415f,580f,865f,638f,18f,18f,fill);glow(c,toast,640f,616f,21f,GOLD)}}
    private fun drawShield(c:Canvas){val top=shieldY-shieldH/2f;val bot=shieldY+shieldH/2f;fill.color=Color.argb((45+flash*80).toInt().coerceIn(0,150),0,255,255);c.drawRoundRect(24f,top-9f,83f,bot+9f,22f,22f,fill);line.color=if(flash>0f)WHITE else CYAN;line.strokeWidth=4f;line.setShadowLayer(18f,0f,0f,CYAN);c.drawRoundRect(39f,top,66f,bot,14f,14f,line);line.clearShadowLayer();fill.color=0xFFB7FFFF.toInt();c.drawRoundRect(45f,top+10f,60f,bot-10f,7f,7f,fill)}
    private fun drawBlade(c:Canvas){val r=18f+if(held)sin(clock*4f)*2f else 0f;line.color=0xAA50F0FF.toInt();line.strokeWidth=3f;line.setShadowLayer(18f,0f,0f,CYAN);c.drawCircle(bladeX,bladeY,r+9f,line);line.clearShadowLayer();c.save();c.rotate(bladeAngle*180f/PI.toFloat(),bladeX,bladeY);path.reset();path.moveTo(bladeX-21f,bladeY+10f);path.lineTo(bladeX+24f,bladeY-22f);path.lineTo(bladeX+9f,bladeY+1f);path.lineTo(bladeX+26f,bladeY+8f);path.lineTo(bladeX-7f,bladeY+22f);path.close();fill.color=WHITE;fill.setShadowLayer(15f,0f,0f,CYAN);c.drawPath(path,fill);fill.clearShadowLayer();line.color=CYAN;line.strokeWidth=2f;c.drawPath(path,line);c.restore()}
    private fun drawEnemy(c:Canvas,e:Enemy){val col=if(e.flash>0f)WHITE else e.color;fill.color=Color.argb(48,Color.red(col),Color.green(col),Color.blue(col));c.drawCircle(e.x,e.y,e.r*1.5f,fill);fill.color=col;fill.setShadowLayer(10f,0f,0f,col);when(e.kind){Kind.DRONE->{path.reset();path.moveTo(e.x,e.y-e.r);path.lineTo(e.x+e.r,e.y);path.lineTo(e.x,e.y+e.r);path.lineTo(e.x-e.r,e.y);path.close();c.drawPath(path,fill)};Kind.SLIME->c.drawOval(e.x-e.r,e.y-e.r*.7f,e.x+e.r,e.y+e.r,fill);Kind.TURRET->c.drawRoundRect(e.x-e.r,e.y-e.r*.7f,e.x+e.r,e.y+e.r*.8f,7f,7f,fill)};fill.clearShadowLayer();fill.color=BG;if(e.kind!=Kind.TURRET){c.drawCircle(e.x-e.r*.3f,e.y,3f,fill);c.drawCircle(e.x+e.r*.3f,e.y,3f,fill)};if(e.maxHp>1){fill.color=0xCC09121E.toInt();c.drawRoundRect(e.x-e.r,e.y+e.r+7f,e.x+e.r,e.y+e.r+13f,3f,3f,fill);fill.color=if(e.hp*2<=e.maxHp)MAGENTA else CYAN;c.drawRoundRect(e.x-e.r,e.y+e.r+7f,e.x-e.r+2*e.r*e.hp/e.maxHp,e.y+e.r+13f,3f,3f,fill)}}
    private fun drawHazard(c:Canvas,h:Hazard){when(h.kind){HazardKind.SPIKES->{fill.color=0xFF380C30.toInt();c.drawRect(h.x-h.w/2,h.y-h.h/2,h.x+h.w/2,h.y+h.h/2,fill);line.color=MAGENTA;for(y in (h.y-h.h/2).toInt()..(h.y+h.h/2).toInt() step 24){path.reset();path.moveTo(h.x-h.w/2,y.toFloat());path.lineTo(h.x+h.w,y+12f);path.lineTo(h.x-h.w/2,y+24f);path.close();c.drawPath(path,line)}};HazardKind.LASER->{line.color=if(h.timer>h.period*.45f)MAGENTA else 0x445D7A94;line.strokeWidth=if(h.timer>h.period*.45f)5f else 2f;c.drawLine(h.x,h.y,h.x,h.y+h.h,line)};HazardKind.MOVER->{fill.color=0xCC34114A.toInt();c.drawRoundRect(h.x-h.w/2,h.y-h.h/2,h.x+h.w/2,h.y+h.h/2,7f,7f,fill);line.color=MAGENTA;c.drawRoundRect(h.x-h.w/2,h.y-h.h/2,h.x+h.w/2,h.y+h.h/2,7f,7f,line)};HazardKind.STICKY->{fill.color=0x66550066;c.drawRoundRect(h.x,h.y,h.x+h.w,h.y+h.h,8f,8f,fill);line.color=MAGENTA;c.drawRoundRect(h.x,h.y,h.x+h.w,h.y+h.h,8f,8f,line)};HazardKind.PORTAL->{line.color=CYAN;line.strokeWidth=4f;c.drawCircle(h.x,h.y,h.r,line);c.drawCircle(h.x2,h.y2,h.r,line);line.color=MAGENTA;c.drawCircle(h.x,h.y,h.r*.55f,line);c.drawCircle(h.x2,h.y2,h.r*.55f,line)};HazardKind.POWER->if(!h.taken){fill.color=0x55FFDD00;c.drawCircle(h.x,h.y,h.r*1.8f,fill);fill.color=GOLD;fill.setShadowLayer(12f,0f,0f,GOLD);c.drawCircle(h.x,h.y,h.r,fill);fill.clearShadowLayer();label(c,when(h.power){Power.EXPAND->"S";Power.DOUBLE->"x2";Power.SLOW->"T"},h.x,h.y+6f,17f,BG)}}}
    private fun drawWin(c:Canvas){background(c,Campaign.colors[world]);overlay(c);frame(c,280f,100f,1000f,625f,GOLD);glow(c,"STAGE CLEAR",640f,190f,53f,GOLD);label(c,Campaign.names[world]+"  •  STAGE "+stage,640f,230f,21f,CYAN);label(c,"★".repeat(stars[stage])+"☆".repeat(3-stars[stage]),640f,310f,64f,GOLD);label(c,"SCORE  "+score+"      CATCHES  "+catches+"      BEST COMBO x"+bestCombo,640f,370f,20f,WHITE);if(stage<50)button(c,"▶ NEXT STAGE",400f,440f,880f,514f,MAGENTA,true);button(c,"↻ REPLAY",310f,530f,610f,596f,CYAN);button(c,"⌂ MENU",670f,530f,970f,596f,CYAN)}
    private fun drawLose(c:Canvas){background(c,MAGENTA);overlay(c);frame(c,310f,150f,970f,575f,MAGENTA);glow(c,"BLADE LOST",640f,240f,52f,WHITE);label(c,"THE CORRUPTION SPREADS…",640f,282f,21f,MAGENTA);label(c,"SCORE "+score+"    •    ENEMIES LEFT "+enemies.count{it.alive},640f,365f,23f,WHITE);button(c,"↻ RETRY",380f,430f,620f,505f,MAGENTA,true);button(c,"⌂ MENU",660f,430f,900f,505f,CYAN)}
    private fun background(c:Canvas,accent:Int?){fill.shader=glowBg.shader;c.drawRect(0f,0f,W,H,fill);fill.shader=null;val color=accent?:CYAN;line.color=0x2439C9E9;line.strokeWidth=1f;for(x in 0..1280 step 48)c.drawLine(x.toFloat(),0f,x.toFloat(),H,line);for(y in 0..720 step 48)c.drawLine(0f,y.toFloat(),W,y.toFloat(),line);line.color=Color.argb(35,Color.red(color),Color.green(color),Color.blue(color));c.drawCircle(640f,360f,175f+9f*sin(clock),line);c.drawCircle(640f,360f,230f+8f*cos(clock),line)}
    private fun frame(c:Canvas,l:Float,t:Float,r:Float,b:Float,color:Int){fill.color=0xD8060A17.toInt();fill.setShadowLayer(28f,0f,0f,color);c.drawRoundRect(l,t,r,b,25f,25f,fill);fill.clearShadowLayer();line.color=color;line.strokeWidth=2f;line.setShadowLayer(9f,0f,0f,color);c.drawRoundRect(l,t,r,b,25f,25f,line);line.clearShadowLayer();line.color=0x3335E9F4;line.strokeWidth=1f;c.drawRoundRect(l+11f,t+11f,r-11f,b-11f,18f,18f,line)}
    private fun overlay(c:Canvas){fill.color=0xB9000208.toInt();c.drawRect(0f,0f,W,H,fill)}
    private fun button(c:Canvas,s:String,l:Float,t:Float,r:Float,b:Float,color:Int,primary:Boolean=false,selected:Boolean=false,disabled:Boolean=false){fill.color=Color.argb(if(disabled)20 else if(primary||selected)48 else 22,Color.red(color),Color.green(color),Color.blue(color));fill.setShadowLayer(if(primary||selected)14f else 6f,0f,0f,color);c.drawRoundRect(l,t,r,b,20f,20f,fill);fill.clearShadowLayer();line.color=Color.argb(if(disabled)65 else if(primary||selected)245 else 155,Color.red(color),Color.green(color),Color.blue(color));line.strokeWidth=if(primary||selected)2.8f else 1.8f;c.drawRoundRect(l,t,r,b,20f,20f,line);label(c,s,(l+r)/2f,(t+b)/2f+8f,min(24f,(b-t)*.34f),if(disabled)0xFF526777.toInt() else WHITE,glow=if(primary||selected)7f else 3f)}
    private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,glow:Float=0f){text(c,s,x,y,size,color,Paint.Align.CENTER,glow)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,align:Paint.Align,glow:Float){label.textSize=size;label.textAlign=align;label.color=color;if(glow>0f)label.setShadowLayer(glow,0f,0f,color)else label.clearShadowLayer();c.drawText(s,x,y,label);label.clearShadowLayer()}
    private fun glow(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int){text(c,s,x,y,size,color,Paint.Align.CENTER,20f)}
    private fun bladeIcon(c:Canvas,x:Float,y:Float,angle:Float){c.save();c.rotate(angle,x,y);path.reset();path.moveTo(x-25,y+10);path.lineTo(x+28,y-25);path.lineTo(x+8,y+2);path.lineTo(x+25,y+9);path.lineTo(x-7,y+24);path.close();fill.color=WHITE;fill.setShadowLayer(16f,0f,0f,CYAN);c.drawPath(path,fill);fill.clearShadowLayer();c.restore()}
    private fun inside(x:Float,y:Float,l:Float,t:Float,r:Float,b:Float)=x>=l&&x<=r&&y>=t&&y<=b
}
