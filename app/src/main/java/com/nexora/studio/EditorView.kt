package com.nexora.studio

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.hypot

class EditorView(context:Context, private val owner:MainActivity):View(context) {
    var tool="Brush"
    var brushSize=14f
    var brushOpacity=255
    var color=Color.rgb(40,160,150)
    private val renderer=Compositor()
    private var zoom=1f;private var panX=0f;private var panY=0f
    private var stroke:RasterAdapter?=null
    private var preview:Bitmap?=null
    private var lastX=0f;private var lastY=0f
    private var moving=false;private var gesture=false;private var points=0
    private var moveLayer:Layer?=null;private var moveStartX=0f;private var moveStartY=0f
    private var span=0f;private var focusX=0f;private var focusY=0f
    private val viewport=Matrix()
    private val drawPaint=Paint()
    private val shapeBounds=RectF(210f,210f,560f,560f)
    private val layerBounds=RectF(1f,1f,767f,767f)
    private fun fit() = minOf(width,height).toFloat()/SIZE*.94f
    private fun updateViewport() {
        viewport.reset();viewport.postScale(fit()*zoom,fit()*zoom)
        viewport.postTranslate((width-SIZE*fit()*zoom)/2+panX,(height-SIZE*fit()*zoom)/2+panY)
    }
    fun resetView(){zoom=1f;panX=0f;panY=0f;invalidate()}
    fun cancelStroke(){stroke=null;preview?.recycle();preview=null
        if(moving){moveLayer?.let{it.x=moveStartX;it.y=moveStartY}}
        moving=false;moveLayer=null;invalidate()
    }
    override fun onDraw(canvas:Canvas) {
        canvas.drawColor(Color.rgb(28,33,42));updateViewport();canvas.save();canvas.concat(viewport)
        val p=drawPaint;p.style=Paint.Style.FILL
        for(y in 0 until 24)for(x in 0 until 24){p.color=if((x+y)%2==0)Color.WHITE else 0xffdddddd.toInt();canvas.drawRect(x*32f,y*32f,(x+1)*32f,(y+1)*32f,p)}
        canvas.clipRect(0,0,SIZE,SIZE)
        renderer.draw(canvas,owner.doc,preview)
        if(tool=="Select" || tool=="Move") {
            val l=owner.doc.active;canvas.save();canvas.concat(l.matrix())
            p.color=0xff00695c.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=2f/(fit()*zoom)
            canvas.drawRect(if(l.kind=="vector")shapeBounds else layerBounds,p);canvas.restore()
        }
        canvas.restore()
    }
    private fun position(e:MotionEvent,layer:Boolean=false):FloatArray {
        updateViewport();val inverse=Matrix();viewport.invert(inverse)
        val point=floatArrayOf(e.x,e.y);inverse.mapPoints(point)
        if(layer){owner.doc.active.matrix().invert(inverse);inverse.mapPoints(point)}
        return point
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(owner.busy)return true
        parent.requestDisallowInterceptTouchEvent(true)
        if(e.pointerCount>=2) {
            cancelStroke();val fx=(e.getX(0)+e.getX(1))/2;val fy=(e.getY(0)+e.getY(1))/2
            val s=hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0))
            if(gesture && e.actionMasked==MotionEvent.ACTION_MOVE){
                val old=zoom;zoom=(zoom*(s/span.coerceAtLeast(1f))).coerceIn(.25f,8f)
                val ratio=zoom/old
                panX=(panX+width/2f-focusX)*ratio-width/2f+fx
                panY=(panY+height/2f-focusY)*ratio-height/2f+fy
            }
            gesture=true;span=s;focusX=fx;focusY=fy;invalidate();return true
        }
        if(gesture){if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL)gesture=false;return true}
        val p=position(e);val l=owner.doc.active
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX=p[0];lastY=p[1]
                if(tool=="Select") {
                    for(i in owner.doc.layers.indices.reversed()) {
                        val candidate=owner.doc.layers[i];if(!candidate.visible)continue
                        val inv=Matrix();candidate.matrix().invert(inv);val hit=p.copyOf();inv.mapPoints(hit)
                        val bounds=if(candidate.kind=="vector")RectF(210f,210f,560f,560f) else RectF(0f,0f,768f,768f)
                        if(bounds.contains(hit[0],hit[1])){owner.doc.selected=i;owner.refreshStatus();break}
                    };invalidate();return true
                }
                if(tool=="Pan")return true
                if(l.locked || !l.visible){owner.message("Layer terkunci atau tersembunyi");return true}
                if(tool=="Move"){owner.history.record(owner.doc);moving=true;moveLayer=l;moveStartX=l.x;moveStartY=l.y;return true}
                if(l.kind!="raster"){owner.message("Pilih layer bitmap untuk menggambar");return true}
                val local=position(e,true)
                if(local.any { it !in 0f..768f })return true
                preview=l.bitmap!!.copy(Bitmap.Config.ARGB_8888,true)
                stroke=RasterAdapter(color,brushSize,brushOpacity,tool=="Eraser")
                points=0;stroke!!.point(local[0],local[1]);stroke!!.point(local[0]+.01f,local[1]+.01f)
                updatePreview()
            }
            MotionEvent.ACTION_MOVE -> {
                if(tool=="Pan"){panX+=(p[0]-lastX)*fit()*zoom;panY+=(p[1]-lastY)*fit()*zoom;invalidate();return true}
                if(moving){l.x=(l.x+p[0]-lastX).coerceIn(-4096f,4096f);l.y=(l.y+p[1]-lastY).coerceIn(-4096f,4096f);lastX=p[0];lastY=p[1];invalidate()}
                if(stroke!=null){
                    val local=position(e,true)
                    if(++points<=10_000){stroke!!.point(local[0].coerceIn(0f,768f),local[1].coerceIn(0f,768f));updatePreview()}
                }
            }
            MotionEvent.ACTION_UP -> {
                if(stroke!=null){owner.history.record(owner.doc);stroke!!.finish();stroke!!.draw(Canvas(l.bitmap!!));cancelStroke();owner.changed()}
                if(moving){moving=false;moveLayer=null;owner.changed()};performClick()
            }
            MotionEvent.ACTION_CANCEL -> {cancelStroke()}
        }
        invalidate();return true
    }
    private fun updatePreview(){
        val c=Canvas(preview!!);c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);c.drawBitmap(owner.doc.active.bitmap!!,0f,0f,null);stroke!!.draw(c)
    }
    override fun performClick():Boolean {super.performClick();return true}
}
