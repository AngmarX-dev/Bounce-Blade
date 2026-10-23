@file:Suppress("UnsafeCastFromDynamic")
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.math.*

private data class Enemy(var x: Double, var y: Double, var hp: Int = 3, var kind: Int = 0, var alive: Boolean = true)
private data class Shot(var x: Double, var y: Double, var vx: Double, var vy: Double)
private data class Particle(var x: Double, var y: Double, var life: Double)
private lateinit var canvas: HTMLCanvasElement
private lateinit var g: CanvasRenderingContext2D
private var w = 960.0; private var h = 540.0
private var aiming = false; private var flying = false; private var returning = false
private var gameOver = false; private var won = false; private var slow = false
private var bladeX = 76.0; private var bladeY = 270.0; private var bladeVx = 0.0; private var bladeVy = 0.0
private var lastPointerX = 300.0; private var lastPointerY = 270.0
private var level = 1; private var money = 0; private var bounces = 5; private var slowMeter = 100.0; private var time = 0.0
private var screen = "menu"
private val enemies = mutableListOf<Enemy>(); private val shots = mutableListOf<Shot>(); private val particles = mutableListOf<Particle>()
private fun resetLevel() {
 enemies.clear(); shots.clear(); particles.clear(); bladeX=76.0; bladeY=h/2; bladeVx=0.0; bladeVy=0.0
 flying=false; returning=false; gameOver=false; won=false; bounces=5
 for(i in 0 until min(3+level,9)) { val k=(i+level)%3; enemies.add(Enemy(w*(0.4+(i%4)*0.14),h*(0.28+(i/4)*0.22),if(k==2)6 else 3,k)) }
}
private fun label(s:String,x:Double,y:Double,size:Int=18,color:String="#f4f7ff") { g.font="bold ${size}px system-ui"; g.fillStyle=color; g.fillText(s,x,y) }
private fun circle(x:Double,y:Double,r:Double,c:String) { g.beginPath();g.arc(x,y,r,0.0,PI*2);g.fillStyle=c;g.fill() }
private fun button(s:String,x:Double,y:Double,bw:Double,bh:Double,accent:String="#19e6cf") {
 g.fillStyle="#101b2c";g.strokeStyle=accent;g.lineWidth=2.0;g.beginPath();g.asDynamic().roundRect(x,y,bw,bh,12.0);g.fill();g.stroke()
 g.font="bold 18px system-ui";g.fillStyle="#f4f7ff";g.asDynamic().textAlign="center";g.fillText(s,x+bw/2,y+bh/2+6);g.asDynamic().textAlign="left"
}
private fun blade(x:Double,y:Double,a:Double) {
 g.save();g.translate(x,y);g.rotate(a);g.shadowColor="#23f5df";g.shadowBlur=18.0;g.fillStyle="#e8ffff";g.beginPath()
 g.moveTo(-23.0,-5.0);g.lineTo(17.0,-2.0);g.lineTo(26.0,0.0);g.lineTo(17.0,2.0);g.lineTo(-23.0,5.0);g.closePath();g.fill();g.shadowBlur=0.0;g.restore()
}
private fun drawEnemy(e:Enemy) {
 val c=when(e.kind){0->"#ff557d";1->"#bd7bff";else->"#ffb347"};g.shadowColor=c;g.shadowBlur=16.0;circle(e.x,e.y,if(e.kind==2)23.0 else 17.0,c);g.shadowBlur=0.0
 circle(e.x-5,e.y-3,3.0,"#101526");circle(e.x+5,e.y-3,3.0,"#101526")
 g.fillStyle="#29354a";g.fillRect(e.x-20,e.y-29,40.0,4.0);g.fillStyle="#5cffbd";g.fillRect(e.x-20,e.y-29,40.0*e.hp/(if(e.kind==2)6.0 else 3.0),4.0)
}
private fun draw() {
 val bg=g.createLinearGradient(0.0,0.0,w,h);bg.addColorStop(0.0,"#080d19");bg.addColorStop(1.0,"#101b2c");g.fillStyle=bg;g.fillRect(0.0,0.0,w,h)
 g.strokeStyle="#26344b";g.lineWidth=1.0
 for(x in 0..(w/40).toInt()){g.beginPath();g.moveTo(x*40.0,0.0);g.lineTo(x*40.0,h);g.stroke()}
 for(y in 0..(h/40).toInt()){g.beginPath();g.moveTo(0.0,y*40.0);g.lineTo(w,y*40.0);g.stroke()}
 g.fillStyle="#20344b";g.fillRect(0.0,0.0,18.0,h);g.shadowColor="#20e9d0";g.shadowBlur=15.0;g.fillStyle="#1be4cf";g.fillRect(18.0,0.0,3.0,h);g.shadowBlur=0.0
 g.fillStyle="#172b3c";g.beginPath();g.asDynamic().roundRect(28.0,h/2-44,60.0,88.0,14.0);g.fill();circle(58.0,h/2-25,8.0,"#24f2d8");circle(58.0,h/2+25,8.0,"#24f2d8")
 label("BOUNCE",30.0,28.0,12,"#20e9d0")
 if(screen=="menu"){label("BOUNCE BLADE",w/2-145,140.0,38);label("A neon ricochet arena",w/2-104,174.0,17,"#a5b5ca");blade(w/2,225.0,-0.2);button("PLAY",w/2-100,290.0,200.0,54.0);button("SHOP  •  $money",w/2-100,360.0,200.0,48.0,"#bd7bff");label("Drag to aim • Release to launch • Hold to slow time",w/2-222,445.0,14,"#9eb0c7");return}
 if(screen=="shop"){label("BLADE SHOP",w/2-100,105.0,32);label("Coins: $money",w/2-55,150.0,20,"#ffcf66");button("UPGRADE BLADE  •  20",w/2-150,210.0,300.0,52.0,"#bd7bff");button("BACK",w/2-100,300.0,200.0,48.0);label("Browser edition demo shop",w/2-130,390.0,14,"#9eb0c7");return}
 label("LEVEL $level",34.0,56.0,19);label("BOUNCES  $bounces",34.0,82.0,14,"#a5b5ca");label("SLOW TIME  ${slowMeter.toInt()}%",w-185,36.0,14,"#bd7bff")
 g.fillStyle="#29354a";g.fillRect(w-185,46.0,150.0,7.0);g.fillStyle="#bd7bff";g.fillRect(w-185,46.0,150.0*slowMeter/100.0,7.0)
 enemies.filter{it.alive}.forEach{drawEnemy(it)};shots.forEach{circle(it.x,it.y,3.0,"#ffd166")};particles.forEach{circle(it.x,it.y,2.0+it.life*4,"#21f2dc")}
 if(flying||returning||!gameOver)blade(bladeX,bladeY,atan2(bladeVy,bladeVx))
 if(aiming&&!flying&&!gameOver){g.setLineDash(arrayOf(6.0,6.0));g.strokeStyle="#b9c7d8";g.lineWidth=2.0;g.beginPath();g.moveTo(76.0,h/2);g.lineTo(lastPointerX,lastPointerY);g.stroke();g.setLineDash(emptyArray<Double>());circle(lastPointerX,lastPointerY,7.0,"#20e9d0")}
 if(gameOver||won){g.fillStyle="rgba(4,8,16,0.78)";g.fillRect(0.0,0.0,w,h);label(if(won)"LEVEL CLEARED!" else "BLADE LOST",w/2-125,h/2-48,32,if(won)"#5cffbd" else "#ff557d");label(if(won)"Reward: +${10+level*2} coins" else "Try another angle and save your bounces",w/2-155,h/2-12,15,"#c3d0e0");button(if(won)"NEXT LEVEL" else "RETRY",w/2-100,h/2+25,200.0,48.0);button("MENU",w/2-100,h/2+85,200.0,42.0,"#bd7bff")}
}
private fun update() {
 val dt=if(slow)0.45 else 1.0
 if(screen=="game"&&!gameOver&&!won){time+=1.0
  if(flying){bladeX+=bladeVx*dt;bladeY+=bladeVy*dt;bladeVy+=0.035*dt
   if(bladeY<28||bladeY>h-28){bladeVy*=-0.92;bladeY=bladeY.coerceIn(28.0,h-28);bounces--}
   if(bladeX>w-24){bladeVx*=-0.95;bladeX=w-24;bounces--};if(bladeX<22){gameOver=true;flying=false};if(bounces<0){returning=true;flying=false}
   enemies.filter{it.alive}.forEach{e->if(hypot(bladeX-e.x,bladeY-e.y)<if(e.kind==2)25.0 else 20.0){e.hp--;particles.add(Particle(e.x,e.y,1.0));bladeVx*=-0.88;if(e.hp<=0)e.alive=false}}
   if(time%18.0<1.0)for(gy in listOf(h/2-25,h/2+25)){val target=enemies.filter{it.alive}.minByOrNull{hypot(it.x-58,it.y-gy)};if(target!=null){target.hp--;shots.add(Shot(75.0,gy,(target.x-75.0)/30.0,(target.y-gy)/30.0));if(target.hp<=0)target.alive=false}}
   if(enemies.none{it.alive}){won=true;flying=false;money+=10+level*2}
   if(!gameOver&&bladeX>100&&!returning&&time%140.0<1.0){returning=true;flying=false}
  }else if(returning){val dx=76.0-bladeX;val dy=h/2-bladeY;val d=hypot(dx,dy);if(d<10){bladeX=76.0;bladeY=h/2;returning=false;bounces=5}else{bladeX+=dx/d*7;bladeY+=dy/d*7}}
  if(slow)slowMeter=max(0.0,slowMeter-0.8)else slowMeter=min(100.0,slowMeter+0.15);particles.forEach{it.life-=0.04};particles.removeAll{it.life<=0};shots.forEach{it.x+=it.vx;it.y+=it.vy};shots.removeAll{it.x>w||it.y<0||it.y>h}
 }
}
private fun frame(t:Double){update();draw();window.requestAnimationFrame(::frame)}
fun main(){
 canvas=document.getElementById("game") as HTMLCanvasElement;g=canvas.getContext("2d") as CanvasRenderingContext2D
 fun resize(){val dpr=window.devicePixelRatio.coerceAtMost(2.0);val r=canvas.getBoundingClientRect();w=max(320.0,r.width);h=max(240.0,r.height);canvas.width=(w*dpr).toInt();canvas.height=(h*dpr).toInt();g.setTransform(dpr,0.0,0.0,dpr,0.0,0.0)}
 resize();window.addEventListener("resize",{resize()})
 canvas.asDynamic().addEventListener("pointerdown",{e:dynamic->val r=canvas.getBoundingClientRect();lastPointerX=(e.clientX as Double)-r.left;lastPointerY=(e.clientY as Double)-r.top
  if(screen=="menu"){if(lastPointerY in 270.0..350.0){screen="game";level=1;resetLevel()}else if(lastPointerY in 350.0..425.0)screen="shop"}
  else if(screen=="shop"){if(lastPointerY in 290.0..365.0)screen="menu" else if(lastPointerY in 190.0..270.0&&money>=20)money-=20}
  else if(gameOver||won){if(lastPointerY>h/2+75&&lastPointerY<h/2+145)screen="menu" else if(lastPointerY>h/2+15&&lastPointerY<h/2+80){if(won)level++;resetLevel()}}
  else if(!flying&&!returning){aiming=true;slow=true}
 })
 canvas.asDynamic().addEventListener("pointermove",{e:dynamic->val r=canvas.getBoundingClientRect();lastPointerX=((e.clientX as Double)-r.left).coerceIn(35.0,w-20);lastPointerY=((e.clientY as Double)-r.top).coerceIn(25.0,h-25)})
 canvas.asDynamic().addEventListener("pointerup",{_:dynamic->if(aiming&&screen=="game"&&!gameOver&&!won){val dx=76.0-lastPointerX;val dy=h/2-lastPointerY;val d=hypot(dx,dy).coerceAtLeast(1.0);bladeVx=dx/d*9.0;bladeVy=dy/d*9.0;flying=true;returning=false;aiming=false;slow=false}else{aiming=false;slow=false}})
 canvas.asDynamic().addEventListener("pointercancel",{_:dynamic->aiming=false;slow=false})
 window.requestAnimationFrame(::frame)
}
