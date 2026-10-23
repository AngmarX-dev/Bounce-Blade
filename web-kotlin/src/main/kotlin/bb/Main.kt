
package bb
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.*
import kotlin.random.Random

private const val W=1600.0
private const val H=900.0
private const val SX=70.0
private const val SH=160.0
private const val BR=24.0
private const val CYAN="#22e6ff"
private const val PINK="#ff2bd6"
private const val GOLD="#ffc83d"
private const val RED="#ff4565"
private const val BG="#05070f"
private enum class Phase { MENU, PLAYING, PAUSED, OVER, CLEAR }
private data class Settings(var sound:Boolean=true,var particles:Boolean=true,var difficulty:String="normal") {
 fun save(){try{window.localStorage.setItem("bb.sound",sound.toString());window.localStorage.setItem("bb.particles",particles.toString());window.localStorage.setItem("bb.difficulty",difficulty)}catch(_:Throwable){}}
 companion object { fun load():Settings=try{val d=window.localStorage.getItem("bb.difficulty")?:"normal";Settings(window.localStorage.getItem("bb.sound")!="false",window.localStorage.getItem("bb.particles")!="false",if(d in listOf("easy","normal","hard"))d else "normal")}catch(_:Throwable){Settings()} }
}
private data class Enemy(var x:Double,var y:Double,var hp:Int,val maxHp:Int,val r:Double,val speed:Double,var t:Double=Random.nextDouble()*7,var flash:Double=0.0,var cooldown:Double=0.0)
private data class Spark(var x:Double,var y:Double,var vx:Double,var vy:Double,var life:Double,val max:Double,val color:String,val size:Double)

private class Game(private val canvas:HTMLCanvasElement,val changed:()->Unit){
 private val c=canvas.getContext("2d") as CanvasRenderingContext2D
 var phase=Phase.MENU;private set
 var stage=1;private set
 var score=0;private set
 var lives=3;private set
 var best=read("bb.best",0);private set
 var unlocked=read("bb.unlocked",1);private set
 var remaining=0;private set
 var shieldY=H/2
 private var bx=SX+50;private var by=shieldY;private var vx=0.0;private var vy=0.0;private var held=true;private var spin=0.0;private var aimAngle=0.0;private var aiming=false;private var dragY:Double?=null
 private var spawn=0;private var spawnTime=0.0;private var last=0.0;private var elapsed=0.0
 private val keys=mutableSetOf<String>();private val enemies=mutableListOf<Enemy>();private val sparks=mutableListOf<Spark>();private var audio:dynamic=null;private var signature=""
 private fun read(k:String,f:Int)=try{window.localStorage.getItem(k)?.toIntOrNull()?.coerceAtLeast(f)?:f}catch(_:Throwable){f}
 init{window.requestAnimationFrame{frame(it)}}
 fun start(s:Int=1){if(s==1||phase==Phase.MENU||phase==Phase.OVER){score=0;lives=3};stage=max(1,s);enemies.clear();sparks.clear();spawn=4+stage*2;spawnTime=.25;shieldY=H/2;resetBlade();phase=Phase.PLAYING;emit(true)}
 fun retry()=start(stage)
 fun next()=start(stage+1)
 fun menu(){phase=Phase.MENU;enemies.clear();spawn=0;resetBlade();emit(true)}
 fun pause(){phase=when(phase){Phase.PLAYING->Phase.PAUSED;Phase.PAUSED->{last=0.0;Phase.PLAYING};else->phase};emit(true)}
 private fun resetBlade(){held=true;bx=SX+50;by=shieldY;vx=0.0;vy=0.0;aimAngle=0.0;aiming=false}
 fun down(x:Double,y:Double){if(phase!=Phase.PLAYING)return;if(held){aiming=true;setAim(x,y)}else dragY=y;audioStart()}
 fun move(x:Double,y:Double){if(phase==Phase.PLAYING){if(aiming)setAim(x,y)else if(dragY!=null)dragY=y}}
 fun up(){if(aiming)launch();aiming=false;dragY=null}
 private fun setAim(x:Double,y:Double){aimAngle=atan2(y-shieldY,max(20.0,x-(SX+50))).coerceIn(-1.3,1.3)}
 fun key(raw:String){when(raw.lowercase()){"escape","p"->pause();" "->launch();else->keys.add(raw.lowercase())};audioStart()}
 fun keyUp(raw:String){keys.remove(raw.lowercase())}
 private fun launch(){if(phase!=Phase.PLAYING||!held)return;val d=when(Settings.load().difficulty){"easy"->.8;"hard"->1.2;else->1.0};held=false;vx=cos(aimAngle)*(1080+stage*20)*d;vy=sin(aimAngle)*(1080+stage*20)*d;sfx(500.0,.08)}
 private fun audioStart(){if(!Settings.load().sound||audio!=null)return;audio=try{js("new (window.AudioContext || window.webkitAudioContext)()")}catch(_:Throwable){null}}
 private fun sfx(f:Double,d:Double){if(!Settings.load().sound)return;audioStart();try{val a=audio;if(a==null)return;if(a.state=="suspended")a.resume();val t=a.currentTime as Double;val o=a.createOscillator();val g=a.createGain();o.type="triangle";o.frequency.setValueAtTime(f,t);g.gain.setValueAtTime(.045,t);g.gain.exponentialRampToValueAtTime(.0001,t+d);o.connect(g);g.connect(a.destination);o.start();o.stop(t+d+.02)}catch(_:Throwable){}}
 private fun frame(now:Double){val dt=if(last==0.0||phase==Phase.PAUSED)0.0 else min(.032,(now-last)/1000.0);last=now;elapsed+=dt;if(phase==Phase.PLAYING)update(dt)else updateSparks(dt);draw();window.requestAnimationFrame{frame(it)}}
 private fun burst(x:Double,y:Double,color:String,n:Int){if(!Settings.load().particles)return;repeat(n){val a=Random.nextDouble()*PI*2;val speed=100+Random.nextDouble()*350;val life=.25+Random.nextDouble()*.5;sparks.add(Spark(x,y,cos(a)*speed,sin(a)*speed,life,life,color,2+Random.nextDouble()*3))};if(sparks.size>400)sparks.subList(0,sparks.size-400).clear()}
 private fun updateSparks(dt:Double){sparks.forEach{it.x+=it.vx*dt;it.y+=it.vy*dt;it.vx*=.94;it.vy*=.94;it.life-=dt};sparks.removeAll{it.life<=0}}
 private fun hurt(){lives--;burst(SX,shieldY,RED,20);sfx(130.0,.2);if(lives<=0){phase=Phase.OVER;if(score>best){best=score;try{window.localStorage.setItem("bb.best",best.toString())}catch(_:Throwable){}}};emit(true)}
 private fun update(dt:Double){
  var dir=0;if("arrowup" in keys||"w" in keys)dir--;if("arrowdown" in keys||"s" in keys)dir++
  if(held&&("arrowleft" in keys||"a" in keys))aimAngle=max(-1.3,aimAngle-dt*2);if(held&&("arrowright" in keys||"d" in keys))aimAngle=min(1.3,aimAngle+dt*2)
  shieldY+=if(dragY==null)dir*760*dt else ((dragY?:shieldY)-shieldY)*min(1.0,dt*18);shieldY=shieldY.coerceIn(SH/2+12,H-SH/2-12)
  spin+=dt*if(held)6 else 22
  if(held){bx=SX+50;by=shieldY}else{bx+=vx*dt;by+=vy*dt
   if(by<BR+12){by=BR+12;vy=abs(vy);wall()};if(by>H-BR-12){by=H-BR-12;vy=-abs(vy);wall()};if(bx>W-BR-12){bx=W-BR-12;vx=-abs(vx);wall()}
   if(vx<0&&bx-BR<SX+18&&bx>SX-15&&abs(by-shieldY)<SH/2+BR*.5){held=true;score+=25;burst(SX+30,by,CYAN,14);sfx(800.0,.1)}
   if(!held&&bx < -BR){resetBlade();hurt()}
  }
  val diff=when(Settings.load().difficulty){"easy"->.75;"hard"->1.25;else->1.0}
  spawnTime-=dt;if(spawn>0&&spawnTime<=0&&enemies.size<3+stage/2){val hp=max(1,((1+stage/2+Random.nextDouble()*2)*diff).toInt());enemies.add(Enemy(W+40,100+Random.nextDouble()*(H-200),hp,hp,28+Random.nextDouble()*12,(42+stage*6+Random.nextDouble()*20)*diff));spawn--;spawnTime=max(.5,2.1-stage*.09)/diff}
  for(e in enemies){e.t+=dt;e.flash=max(0.0,e.flash-dt);e.cooldown=max(0.0,e.cooldown-dt);val dx=SX-e.x;val dy=shieldY-e.y;val dist=max(1.0,hypot(dx,dy));e.x+=dx/dist*e.speed*dt;e.y+=(dy/dist*e.speed*.6+sin(e.t*2.4)*38)*dt;if(!held&&e.cooldown<=0&&hypot(bx-e.x,by-e.y)<e.r+BR){e.hp--;e.flash=.14;e.cooldown=.24;score+=10;burst(e.x,e.y,GOLD,10);sfx(350.0,.06)}}
  enemies.filter{it.hp>0&&it.x<SX+28}.forEach{it.hp=0;hurt()};enemies.filter{it.hp<=0}.forEach{score+=100*stage;burst(it.x,it.y,PINK,22)};enemies.removeAll{it.hp<=0};remaining=enemies.size+spawn;updateSparks(dt)
  if(phase==Phase.PLAYING&&spawn==0&&enemies.isEmpty()){phase=Phase.CLEAR;score+=500*stage;unlocked=max(unlocked,stage+1);try{window.localStorage.setItem("bb.unlocked",unlocked.toString());window.localStorage.setItem("bb.best",max(best,score).toString())}catch(_:Throwable){};emit(true)}else emit()
 }
 private fun wall(){burst(bx,by,CYAN,6);sfx(230.0,.05)}
 private fun emit(force:Boolean=false){val s=phase.toString()+stage+score+lives+remaining;if(force||s!=signature){signature=s;changed()}}
 private fun draw(){
  c.save();c.fillStyle=BG;c.fillRect(0.0,0.0,W,H);c.strokeStyle="rgba(40,90,160,.16)";c.lineWidth=1.0
  var x=-(elapsed*24%60);while(x<W){c.beginPath();c.moveTo(x,0.0);c.lineTo(x,H);c.stroke();x+=60};var y=0.0;while(y<H){c.beginPath();c.moveTo(0.0,y);c.lineTo(W,y);c.stroke();y+=60}
  c.shadowBlur=20.0;c.shadowColor=PINK;c.strokeStyle=PINK;c.lineWidth=5.0;c.strokeRect(6.0,6.0,W-12,H-12);c.shadowBlur=0.0;c.strokeStyle="rgba(255,69,101,.5)";c.setLineDash(arrayOf(10.0,14.0));c.beginPath();c.moveTo(SX-25,10.0);c.lineTo(SX-25,H-10);c.stroke();c.setLineDash(emptyArray())
  c.shadowBlur=24.0;c.shadowColor=CYAN;c.fillStyle=CYAN;c.fillRect(SX-8,shieldY-SH/2,18.0,SH);c.shadowBlur=0.0;c.fillStyle="rgba(34,230,255,.13)";c.fillRect(SX+10,shieldY-SH/2,32.0,SH)
  if(held&&phase==Phase.PLAYING){c.strokeStyle="rgba(255,200,61,.7)";c.lineWidth=3.0;c.setLineDash(arrayOf(6.0,14.0));c.beginPath();c.moveTo(bx,by);c.lineTo(bx+cos(aimAngle)*220,by+sin(aimAngle)*220);c.stroke();c.setLineDash(emptyArray())}
  for(e in enemies){c.save();c.translate(e.x,e.y);c.shadowBlur=18.0;c.shadowColor=PINK;c.strokeStyle=if(e.flash>0)"#fff" else PINK;c.lineWidth=4.0;c.beginPath();for(i in 0..5){val a=i*PI/3+e.t;val px=cos(a)*e.r;val py=sin(a)*e.r;if(i==0)c.moveTo(px,py)else c.lineTo(px,py)};c.closePath();c.stroke();c.shadowBlur=0.0;c.fillStyle=RED;c.beginPath();c.arc(0.0,0.0,e.r*.25,0.0,PI*2);c.fill();c.fillStyle="#263244";c.fillRect(-e.r,-e.r-15,e.r*2,6.0);c.fillStyle=if(e.hp.toDouble()/e.maxHp>.5)CYAN else GOLD;c.fillRect(-e.r,-e.r-15,e.r*2*max(0.0,e.hp.toDouble()/e.maxHp),6.0);c.restore()}
  c.save();c.translate(bx,by);c.rotate(spin);c.fillStyle=GOLD;c.shadowBlur=25.0;c.shadowColor=GOLD;c.beginPath();for(i in 0..7){val a=i*PI/4;val r=if(i%2==0)BR+6 else BR*.45;val px=cos(a)*r;val py=sin(a)*r;if(i==0)c.moveTo(px,py)else c.lineTo(px,py)};c.closePath();c.fill();c.shadowBlur=0.0;c.fillStyle=BG;c.beginPath();c.arc(0.0,0.0,6.0,0.0,PI*2);c.fill();c.restore()
  for(p in sparks){c.globalAlpha=(p.life/p.max).coerceIn(0.0,1.0);c.fillStyle=p.color;c.fillRect(p.x,p.y,p.size,p.size)};c.globalAlpha=1.0;c.restore()
 }
}

private class BrowserApp(private val root:HTMLElement){
 private var settings=Settings.load();private lateinit var game:Game;private lateinit var canvas:HTMLCanvasElement;private lateinit var hud:HTMLElement;private lateinit var overlay:HTMLElement;private var modal="";private var hudCache="";private var overlayCache=""
 init{root.innerHTML="""<div class="app"><header class="hud" id="hud"></header><section class="arena"><canvas id="arena" width="1600" height="900"></canvas><div class="overlay" id="overlay"></div></section><p class="hint">DRAG TO AIM · RELEASE TO LAUNCH · MOVE THE CYAN SHIELD TO CATCH THE BLADE</p></div>""";canvas=root.querySelector("#arena") as HTMLCanvasElement;hud=root.querySelector("#hud") as HTMLElement;overlay=root.querySelector("#overlay") as HTMLElement;game=Game(canvas){render()};bind();render()}
 private fun point(e:Event):Pair<Double,Double>{val r=canvas.getBoundingClientRect();val d=e.asDynamic();return Pair(((d.clientX as Double)-r.left)/r.width*W,((d.clientY as Double)-r.top)/r.height*H)}
 private fun bind(){
  canvas.addEventListener("pointerdown",{e->e.preventDefault();try{canvas.asDynamic().setPointerCapture(e.asDynamic().pointerId)}catch(_:Throwable){};val p=point(e);game.down(p.first,p.second)})
  canvas.addEventListener("pointermove",{e->val p=point(e);game.move(p.first,p.second)});canvas.addEventListener("pointerup",{game.up()});canvas.addEventListener("pointercancel",{game.up()})
  window.addEventListener("keydown",{e->val k=(e as KeyboardEvent).key;if(k in listOf(" ","ArrowUp","ArrowDown","ArrowLeft","ArrowRight"))e.preventDefault();game.key(k)})
  window.addEventListener("keyup",{e->game.keyUp((e as KeyboardEvent).key)})
  root.addEventListener("click",{e->val el=(e.target as? Element)?.closest("[data-action]")?:return@addEventListener;act(el.getAttribute("data-action")?:"")})
 }
 private fun b(a:String,t:String,s:String="primary")="<button class='btn "+s+"' data-action='"+a+"' type='button'>"+t+"</button>"
 private fun panel(t:String,body:String)="<div class='overlay-content'><h2>"+t+"</h2><div class='buttons'>"+body+"</div></div>"
 private fun act(a:String){when(a){"play"->game.start(1);"continue"->game.start(game.unlocked);"retry"->game.retry();"next"->game.next();"menu"->game.menu();"pause"->game.pause();"how"->modal="how";"settings"->modal="settings";"close"->modal="";"sound"->{settings.sound=!settings.sound;settings.save()};"particles"->{settings.particles=!settings.particles;settings.save()};"easy","normal","hard"->{settings.difficulty=a;settings.save()}};render()}
 private fun render(){
  val ph=game.phase.name
  val h=if(ph=="MENU")"" else "<div class='stats'><span>STAGE <b class='gold'>"+game.stage+"</b></span><span>SCORE <b class='cyan'>"+game.score+"</b></span><span>LIVES <b class='pink'>"+ "♦".repeat(game.lives.coerceAtLeast(0)) +"</b></span><span>DRONES <b class='gold'>"+game.remaining+"</b></span></div><div class='actions'>"+b("sound",if(settings.sound)"🔊" else "🔇","ghost")+b("pause",if(ph=="PAUSED")"▶" else "Ⅱ","ghost")+b("retry","↻","ghost")+"</div>"
  if(h!=hudCache){hud.innerHTML=h;hudCache=h}
  val best=try{window.localStorage.getItem("bb.best")?.toIntOrNull()?:0}catch(_:Throwable){0}
  val ov=when{
   modal=="how"->panel("HOW TO PLAY","<p class='copy'>Drag and release to launch. Arrow keys or A/D aim; Space launches. W/S or ↑/↓ moves your shield.</p><p class='copy'>The blade pierces drones and bounces off the top, right and bottom walls. Catch it on return or lose a life.</p>"+b("close","GOT IT"))
   modal=="settings"->panel("SETTINGS",b("sound","SOUND: "+if(settings.sound)"ON" else "OFF","ghost")+b("particles","PARTICLES: "+if(settings.particles)"ON" else "OFF","ghost")+"<div class='buttons'>"+listOf("easy","normal","hard").joinToString(""){b(it,it.uppercase(),if(settings.difficulty==it)"gold-btn" else "ghost")}+"</div>"+b("close","DONE"))
   ph=="MENU"->"<div class='overlay-content'><h1 class='title'>BOUNCE<br><span class='pink'>BLADE</span></h1><p class='subtitle'>KOTLIN BROWSER ARCADE</p><p class='copy'>BEST "+best+" · STAGE "+game.unlocked+" UNLOCKED</p><div class='buttons'>"+b("play","PLAY")+(if(game.unlocked>1)b("continue","CONTINUE · STAGE "+game.unlocked,"gold-btn")else"")+b("how","HOW TO PLAY","ghost")+b("settings","SETTINGS","ghost")+b("sound","SOUND: "+if(settings.sound)"ON" else "OFF","ghost")+"</div></div>"
   ph=="PAUSED"->panel("PAUSED",b("pause","RESUME")+b("retry","RESTART STAGE","ghost")+b("menu","MAIN MENU","ghost"))
   ph=="OVER"->panel("BLADE BREACH","<p class='copy'>Score "+game.score+" · Best "+best+"</p>"+b("retry","RETRY STAGE")+b("menu","MAIN MENU","ghost"))
   ph=="CLEAR"->panel("STAGE "+game.stage+" CLEAR","<p class='copy'>Score "+game.score+"</p>"+b("next","NEXT STAGE","gold-btn")+b("retry","RETRY","ghost")+b("menu","MAIN MENU","ghost"))
   else->""
  }
  if(ov!=overlayCache){overlay.innerHTML=ov;overlay.classList.toggle("hidden",ov.isEmpty());overlayCache=ov}
 }
}
fun main(){val root=document.getElementById("game-root") as? HTMLElement?:return;BrowserApp(root)}
