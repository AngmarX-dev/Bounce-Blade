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
  setContentView(GameView(this))
 }
}
private data class Enemy(val x: Float, val y: Float, var alive: Boolean = true)
private data class Crate(val x: Float, val y: Float, var alive: Boolean = true)
private class GameView(context: android.content.Context) : View(context) {
 private val cyan=Color.rgb(45,235,255); private val pink=Color.rgb(255,65,150); private val gold=Color.rgb(255,199,83)
 private val p=Paint(3); private var w=1f; private var h=1f
 private var level=0; private var bladeX=0.13f; private var bladeY=0.83f; private var vx=0f; private var vy=0f; private var spin=0f
 private var flying=false; private var aiming=false; private var slow=false; private var won=false; private var lost=false
 private var startX=0f; private var startY=0f; private var dragX=0f; private var dragY=0f; private var last=System.nanoTime(); private var slowMeter=1f; private var bounces=5
 private var sx=0.78f; private var sy=0.78f; private var ex=0.88f; private var ey=0.13f
 private var enemies= mutableListOf<Enemy>(); private var crates= mutableListOf<Crate>()
 private val layouts=listOf(
  listOf(0.35f to 0.32f,0.65f to 0.26f,0.72f to 0.55f),
  listOf(0.25f to 0.25f,0.5f to 0.4f,0.75f to 0.27f,0.7f to 0.68f),
  listOf(0.25f to 0.4f,0.48f to 0.25f,0.72f to 0.43f,0.55f to 0.68f,0.8f to 0.7f),
  listOf(0.22f to 0.25f,0.42f to 0.4f,0.72f to 0.24f,0.75f to 0.6f,0.43f to 0.72f),
  listOf(0.2f to 0.28f,0.4f to 0.25f,0.6f to 0.4f,0.8f to 0.3f,0.68f to 0.68f,0.38f to 0.7f),
  listOf(0.25f to 0.25f,0.5f to 0.28f,0.75f to 0.25f,0.28f to 0.6f,0.5f to 0.7f,0.75f to 0.6f),
  listOf(0.2f to 0.4f,0.35f to 0.25f,0.5f to 0.4f,0.65f to 0.25f,0.8f to 0.4f,0.5f to 0.68f),
  listOf(0.24f to 0.28f,0.44f to 0.35f,0.64f to 0.28f,0.8f to 0.42f,0.32f to 0.65f,0.64f to 0.68f,0.48f to 0.52f),
  listOf(0.2f to 0.25f,0.4f to 0.25f,0.6f to 0.25f,0.8f to 0.25f,0.25f to 0.5f,0.5f to 0.5f,0.75f to 0.5f,0.5f to 0.72f),
  listOf(0.2f to 0.28f,0.4f to 0.35f,0.6f to 0.28f,0.8f to 0.35f,0.28f to 0.58f,0.5f to 0.48f,0.72f to 0.58f,0.5f to 0.72f)
 )
 init { load(0) }
 private fun load(n:Int) { level=n%layouts.size; enemies=layouts[level].map{Enemy(it.first,it.second)}.toMutableList()
  crates=mutableListOf(Crate(0.43f,0.55f),Crate(0.63f,0.42f)); sx=if(level%3==2)0.18f else 0.8f; sy=0.78f; bladeX=0.13f; bladeY=0.83f; vx=0f;vy=0f; flying=false;aiming=false;won=false;lost=false;bounces=5+level/3;slowMeter=1f; invalidate() }
 override fun onDraw(c:Canvas) { super.onDraw(c); w=width.toFloat();h=height.toFloat(); c.drawColor(Color.rgb(7,10,22))
  p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=Color.rgb(20,31,52)
  for(i in 1..5)c.drawLine(w*i/6f,h*0.08f,w*i/6f,h*0.93f,p)
  for(i in 1..10)c.drawLine(w*0.05f,h*(0.08f+i*0.08f),w*0.95f,h*(0.08f+i*0.08f),p)
  p.color=cyan;p.strokeWidth=3f;p.setShadowLayer(16f,0f,0f,cyan);c.drawRoundRect(w*0.05f,h*0.075f,w*0.95f,h*0.93f,18f,18f,p);p.clearShadowLayer()
  label(c,"BOUNCE BLADE",0.08f,0.045f,0.027f,Color.WHITE);label(c,"LEVEL "+(level+1),0.92f,0.045f,0.023f,cyan,Paint.Align.RIGHT)
  label(c,"BOUNCES  "+bounces,0.09f,0.11f,0.02f,gold);label(c,"SLOW  "+(slowMeter*100).toInt()+"%",0.91f,0.11f,0.02f,cyan,Paint.Align.RIGHT)
  p.color=if(enemies.none{it.alive}&&crates.none{it.alive})cyan else Color.rgb(60,85,110);p.strokeWidth=3f;p.style=Paint.Style.STROKE;c.drawRoundRect(w*(ex-0.055f),h*(ey-0.023f),w*(ex+0.055f),h*(ey+0.023f),8f,8f,p);label(c,"EXIT",ex,ey+0.006f,0.014f,Color.WHITE,Paint.Align.CENTER)
  p.color=gold;p.strokeWidth=3f;c.drawCircle(w*sx,h*sy,min(w,h)*0.024f,p);label(c,"SWITCH",sx,sy+0.05f,0.014f,gold,Paint.Align.CENTER)
  p.style=Paint.Style.FILL;p.color=Color.rgb(57,25,51);c.drawRoundRect(w*0.22f,h*0.885f,w*0.78f,h*0.91f,5f,5f,p)
  for(i in 0..9){val x=0.23f+i*0.06f;val path=Path();path.moveTo(w*x,h*0.91f);path.lineTo(w*(x+0.025f),h*0.885f);path.lineTo(w*(x+0.05f),h*0.91f);path.close();p.color=pink;c.drawPath(path,p)}
  crates.filter{it.alive}.forEach{p.color=gold;p.style=Paint.Style.STROKE;p.strokeWidth=3f;c.drawRect(w*(it.x-0.032f),h*(it.y-0.032f),w*(it.x+0.032f),h*(it.y+0.032f),p);c.drawLine(w*(it.x-0.02f),h*(it.y-0.02f),w*(it.x+0.02f),h*(it.y+0.02f),p)}
  enemies.filter{it.alive}.forEach{p.color=pink;p.style=Paint.Style.STROKE;p.strokeWidth=3f;p.setShadowLayer(12f,0f,0f,pink);c.drawCircle(w*it.x,h*it.y,min(w,h)*0.025f,p);p.clearShadowLayer();p.style=Paint.Style.FILL;c.drawCircle(w*(it.x-0.008f),h*(it.y-0.004f),5f,p);c.drawCircle(w*(it.x+0.008f),h*(it.y-0.004f),5f,p)}
  if(aiming){p.color=cyan;p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawLine(w*bladeX,h*bladeY,w*(bladeX+(startX-dragX).coerceIn(-0.3f,0.3f)),h*(bladeY+(startY-dragY).coerceIn(-0.3f,0.3f)),p)}
  val bx=w*bladeX;val by=h*bladeY;val r=min(w,h)*0.028f;val blade=Path();blade.moveTo(bx-r,by+r*0.5f);blade.lineTo(bx+r*0.9f,by-r);blade.lineTo(bx+r*0.45f,by);blade.lineTo(bx+r*1.1f,by+r*0.35f);blade.lineTo(bx-r*0.7f,by+r*0.7f);blade.close()
  c.save();c.rotate(spin,bx,by);p.color=cyan;p.style=Paint.Style.FILL;p.setShadowLayer(18f,0f,0f,cyan);c.drawPath(blade,p);p.clearShadowLayer();c.restore()
  if(!flying&&!won&&!lost){label(c,"DRAG BACK TO AIM",0.5f,0.16f,0.023f,Color.WHITE,Paint.Align.CENTER);label(c,"RELEASE TO LAUNCH  •  TWO FINGERS = SLOW TIME",0.5f,0.19f,0.012f,cyan,Paint.Align.CENTER)}
  if(won||lost){p.color=Color.argb(225,3,6,18);p.style=Paint.Style.FILL;c.drawRoundRect(w*0.12f,h*0.36f,w*0.88f,h*0.62f,20f,20f,p);label(c,if(won)"LEVEL CLEARED" else "TRY AGAIN",0.5f,0.44f,0.036f,if(won)cyan else pink,Paint.Align.CENTER);label(c,"TAP TO "+if(won)"CONTINUE" else "RETRY",0.5f,0.54f,0.02f,gold,Paint.Align.CENTER)}
  if(flying){step();postInvalidateDelayed(16)}
 }
 private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,align:Paint.Align=Paint.Align.LEFT){p.style=Paint.Style.FILL;p.color=color;p.textSize=size*h;p.typeface=Typeface.create("sans-serif",Typeface.BOLD);p.textAlign=align;c.drawText(s,w*x,h*y,p)}
 private fun step(){val now=System.nanoTime();val raw=((now-last)/1e9).toFloat().coerceIn(0f,0.03f);last=now;val dt=raw*if(slow&&slowMeter>0f)0.28f else 1f
  if(slow&&slowMeter>0f)slowMeter=(slowMeter-raw*0.18f).coerceAtLeast(0f) else slowMeter=(slowMeter+raw*0.04f).coerceAtMost(1f)
  repeat(3){bladeX+=vx*dt/3;bladeY+=vy*dt/3;spin+=18f*dt;var bounce=false
   if(bladeX<0.08f){bladeX=0.08f;vx=abs(vx);bounce=true};if(bladeX>0.92f){bladeX=0.92f;vx=-abs(vx);bounce=true}
   if(bladeY<0.085f){bladeY=0.085f;vy=abs(vy);bounce=true};if(bladeY>0.87f){bladeY=0.87f;vy=-abs(vy);bounce=true}
   if(bounce){bounces--;if(bounces<0){lost=true;flying=false}}
   enemies.filter{it.alive}.forEach{if(sqrt(bladeX-it.x,bladeY-it.y)<0.05f)it.alive=false}
   crates.filter{it.alive}.forEach{if(abs(bladeX-it.x)<0.05f&&abs(bladeY-it.y)<0.05f){it.alive=false;vx=-vx*0.9f;vy=-vy*0.9f}}
   if(enemies.none{it.alive}&&crates.none{it.alive}&&sqrt(bladeX-ex,bladeY-ey)<0.07f){won=true;flying=false}
   if(bladeY>0.865f&&bladeX in 0.22f.. 0.78f){lost=true;flying=false}
  }
  if(sqrt(vx,vy)<0.025f){flying=false;if(!won)lost=true}
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{when(e.actionMasked){
  MotionEvent.ACTION_POINTER_DOWN->{slow=true;return true};MotionEvent.ACTION_POINTER_UP->{slow=false;return true}
  MotionEvent.ACTION_DOWN->{if(won){load(level+1);return true};if(lost){load(level);return true};if(!flying){aiming=true;startX=e.x/w;startY=e.y/h;dragX=startX;dragY=startY};return true}
  MotionEvent.ACTION_MOVE->{if(e.pointerCount>1)slow=true;if(aiming&&!flying){dragX=e.x/w;dragY=e.y/h;invalidate()};return true}
  MotionEvent.ACTION_UP->{if(aiming&&!flying){aiming=false;val dx=startX-dragX;val dy=startY-dragY;val mag=sqrt(dx,dy);if(mag>0.045f){val power=mag.coerceAtMost(0.32f);vx=dx/mag*power*2.8f;vy=dy/mag*power*2.8f;flying=true;last=System.nanoTime()};invalidate()};slow=false;return true}
 };return true}
}
