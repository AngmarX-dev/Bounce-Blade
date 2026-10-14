package com.angmarx.bounceblade

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  @Suppress("DEPRECATION")
  window.decorView.systemUiVisibility = 5894 or 1024 or 512
  requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  setContentView(GameView())
 }
}
private data class Enemy(val x: Float, val y: Float, var alive: Boolean = true)
private data class Crate(val x: Float, val y: Float, var alive: Boolean = true)
private class GameView : View {
 private val cyan=Color.rgb(45,235,255); private val pink=Color.rgb(255,65,150); private val gold=Color.rgb(255,199,83)
 private val p=Paint(3); private var w=1f; private var h=1f
 private var level=0; private var bladeX=.13f; private var bladeY=.83f; private var vx=0f; private var vy=0f; private var spin=0f
 private var flying=false; private var aiming=false; private var slow=false; private var won=false; private var lost=false
 private var startX=0f; private var startY=0f; private var dragX=0f; private var dragY=0f; private var last=System.nanoTime(); private var slowMeter=1f; private var bounces=5
 private var sx=.78f; private var sy=.78f; private var ex=.88f; private var ey=.13f
 private var enemies= mutableListOf<Enemy>(); private var crates= mutableListOf<Crate>()
 private val layouts=listOf(
  listOf(.35f to .32f,.65f to .26f,.72f to .55f),
  listOf(.25f to .25f,.5f to .4f,.75f to .27f,.7f to .68f),
  listOf(.25f to .4f,.48f to .25f,.72f to .43f,.55f to .68f,.8f to .7f),
  listOf(.22f to .25f,.42f to .4f,.72f to .24f,.75f to .6f,.43f to .72f),
  listOf(.2f to .28f,.4f to .25f,.6f to .4f,.8f to .3f,.68f to .68f,.38f to .7f),
  listOf(.25f to .25f,.5f to .28f,.75f to .25f,.28f to .6f,.5f to .7f,.75f to .6f),
  listOf(.2f to .4f,.35f to .25f,.5f to .4f,.65f to .25f,.8f to .4f,.5f to .68f),
  listOf(.24f to .28f,.44f to .35f,.64f to .28f,.8f to .42f,.32f to .65f,.64f to .68f,.48f to .52f),
  listOf(.2f to .25f,.4f to .25f,.6f to .25f,.8f to .25f,.25f to .5f,.5f to .5f,.75f to .5f,.5f to .72f),
  listOf(.2f to .28f,.4f to .35f,.6f to .28f,.8f to .35f,.28f to .58f,.5f to .48f,.72f to .58f,.5f to .72f)
 )
 init { load(0) }
 private fun load(n:Int) { level=n%layouts.size; enemies=layouts[level].map{Enemy(it.first,it.second)}.toMutableList()
  crates=mutableListOf(Crate(.43f,.55f),Crate(.63f,.42f)); sx=if(level%3==2).18f else .8f; sy=.78f; bladeX=.13f; bladeY=.83f; vx=0f;vy=0f; flying=false;aiming=false;won=false;lost=false;bounces=5+level/3;slowMeter=1f; invalidate() }
 override fun onDraw(c:Canvas) { super.onDraw(c); w=width.toFloat();h=height.toFloat(); c.drawColor(Color.rgb(7,10,22))
  p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=Color.rgb(20,31,52)
  for(i in 1..5)c.drawLine(w*i/6f,h*.08f,w*i/6f,h*.93f,p)
  for(i in 1..10)c.drawLine(w*.05f,h*(.08f+i*.08f),w*.95f,h*(.08f+i*.08f),p)
  p.color=cyan;p.strokeWidth=3f;p.setShadowLayer(16f,0f,0f,cyan);c.drawRoundRect(w*.05f,h*.075f,w*.95f,h*.93f,18f,18f,p);p.clearShadowLayer()
  label(c,"BOUNCE BLADE",.08f,.045f,.027f,Color.WHITE);label(c,"LEVEL "+(level+1),.92f,.045f,.023f,cyan,Paint.Align.RIGHT)
  label(c,"BOUNCES  "+bounces,.09f,.11f,.02f,gold);label(c,"SLOW  "+(slowMeter*100).toInt()+"%",.91f,.11f,.02f,cyan,Paint.Align.RIGHT)
  p.color=if(enemies.none{it.alive}&&crates.none{it.alive})cyan else Color.rgb(60,85,110);p.strokeWidth=3f;p.style=Paint.Style.STROKE;c.drawRoundRect(w*(ex-.055f),h*(ey-.023f),w*(ex+.055f),h*(ey+.023f),8f,8f,p);label(c,"EXIT",ex,ey+.006f,.014f,Color.WHITE,Paint.Align.CENTER)
  p.color=gold;p.strokeWidth=3f;c.drawCircle(w*sx,h*sy,min(w,h)*.024f,p);label(c,"SWITCH",sx,sy+.05f,.014f,gold,Paint.Align.CENTER)
  p.style=Paint.Style.FILL;p.color=Color.rgb(57,25,51);c.drawRoundRect(w*.22f,h*.885f,w*.78f,h*.91f,5f,5f,p)
  for(i in 0..9){val x=.23f+i*.06f;val path=Path();path.moveTo(w*x,h*.91f);path.lineTo(w*(x+.025f),h*.885f);path.lineTo(w*(x+.05f),h*.91f);path.close();p.color=pink;c.drawPath(path,p)}
  crates.filter{it.alive}.forEach{p.color=gold;p.style=Paint.Style.STROKE;p.strokeWidth=3f;c.drawRect(w*(it.x-.032f),h*(it.y-.032f),w*(it.x+.032f),h*(it.y+.032f),p);c.drawLine(w*(it.x-.02f),h*(it.y-.02f),w*(it.x+.02f),h*(it.y+.02f),p)}
  enemies.filter{it.alive}.forEach{p.color=pink;p.style=Paint.Style.STROKE;p.strokeWidth=3f;p.setShadowLayer(12f,0f,0f,pink);c.drawCircle(w*it.x,h*it.y,min(w,h)*.025f,p);p.clearShadowLayer();p.style=Paint.Style.FILL;c.drawCircle(w*(it.x-.008f),h*(it.y-.004f),5f,p);c.drawCircle(w*(it.x+.008f),h*(it.y-.004f),5f,p)}
  if(aiming){p.color=cyan;p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawLine(w*bladeX,h*bladeY,w*(bladeX+(startX-dragX).coerceIn(-.3f,.3f)),h*(bladeY+(startY-dragY).coerceIn(-.3f,.3f)),p)}
  val bx=w*bladeX;val by=h*bladeY;val r=min(w,h)*.028f;val blade=Path();blade.moveTo(bx-r,by+r*.5f);blade.lineTo(bx+r*.9f,by-r);blade.lineTo(bx+r*.45f,by);blade.lineTo(bx+r*1.1f,by+r*.35f);blade.lineTo(bx-r*.7f,by+r*.7f);blade.close()
  c.save();c.rotate(spin,bx,by);p.color=cyan;p.style=Paint.Style.FILL;p.setShadowLayer(18f,0f,0f,cyan);c.drawPath(blade,p);p.clearShadowLayer();c.restore()
  if(!flying&&!won&&!lost){label(c,"DRAG BACK TO AIM",.5f,.16f,.023f,Color.WHITE,Paint.Align.CENTER);label(c,"RELEASE TO LAUNCH  •  TWO FINGERS = SLOW TIME",.5f,.19f,.012f,cyan,Paint.Align.CENTER)}
  if(won||lost){p.color=Color.argb(225,3,6,18);p.style=Paint.Style.FILL;c.drawRoundRect(w*.12f,h*.36f,w*.88f,h*.62f,20f,20f,p);label(c,if(won)"LEVEL CLEARED" else "TRY AGAIN",.5f,.44f,.036f,if(won)cyan else pink,Paint.Align.CENTER);label(c,"TAP TO "+if(won)"CONTINUE" else "RETRY",.5f,.54f,.02f,gold,Paint.Align.CENTER)}
  if(flying){step();postInvalidateDelayed(16)}
 }
 private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,align:Paint.Align=Paint.Align.LEFT){p.style=Paint.Style.FILL;p.color=color;p.textSize=size*resources.displayMetrics.scaledDensity;p.typeface=Typeface.create("sans-serif",Typeface.BOLD);p.textAlign=align;c.drawText(s,w*x,h*y,p)}
 private fun step(){val now=System.nanoTime();val raw=((now-last)/1e9).toFloat().coerceIn(0f,.03f);last=now;val dt=raw*if(slow&&slowMeter>0f).28f else 1f
  if(slow&&slowMeter>0f)slowMeter=(slowMeter-raw*.18f).coerceAtLeast(0f) else slowMeter=(slowMeter+raw*.04f).coerceAtMost(1f)
  repeat(3){bladeX+=vx*dt/3;bladeY+=vy*dt/3;spin+=18f*dt;var bounce=false
   if(bladeX<.08f){bladeX=.08f;vx=abs(vx);bounce=true};if(bladeX>.92f){bladeX=.92f;vx=-abs(vx);bounce=true}
   if(bladeY<.085f){bladeY=.085f;vy=abs(vy);bounce=true};if(bladeY>.87f){bladeY=.87f;vy=-abs(vy);bounce=true}
   if(bounce){bounces--;if(bounces<0){lost=true;flying=false}}
   enemies.filter{it.alive}.forEach{if(hypot(bladeX-it.x,bladeY-it.y)<.05f)it.alive=false}
   crates.filter{it.alive}.forEach{if(abs(bladeX-it.x)<.05f&&abs(bladeY-it.y)<.05f){it.alive=false;vx=-vx*.9f;vy=-vy*.9f}}
   if(enemies.none{it.alive}&&crates.none{it.alive}&&hypot(bladeX-ex,bladeY-ey)<.07f){won=true;flying=false}
   if(bladeY>.865f&&bladeX in .22f.. .78f){lost=true;flying=false}
  }
  if(hypot(vx,vy)<.025f){flying=false;if(!won)lost=true}
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{when(e.actionMasked){
  MotionEvent.ACTION_POINTER_DOWN->{slow=true;return true};MotionEvent.ACTION_POINTER_UP->{slow=false;return true}
  MotionEvent.ACTION_DOWN->{if(won){load(level+1);return true};if(lost){load(level);return true};if(!flying){aiming=true;startX=e.x/w;startY=e.y/h;dragX=startX;dragY=startY};return true}
  MotionEvent.ACTION_MOVE->{if(e.pointerCount>1)slow=true;if(aiming&&!flying){dragX=e.x/w;dragY=e.y/h;invalidate()};return true}
  MotionEvent.ACTION_UP->{if(aiming&&!flying){aiming=false;val dx=startX-dragX;val dy=startY-dragY;val mag=hypot(dx,dy);if(mag>.045f){val power=mag.coerceAtMost(.32f);vx=dx/mag*power*2.8f;vy=dy/mag*power*2.8f;flying=true;last=System.nanoTime()};invalidate()};slow=false;return true}
 };return true}
}
