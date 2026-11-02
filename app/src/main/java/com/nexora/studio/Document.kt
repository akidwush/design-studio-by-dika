package com.nexora.studio

import android.graphics.*
import com.caverock.androidsvg.SVG
import com.mocircle.cidrawing.core.CiPaint
import com.mocircle.cidrawing.element.StrokeElement
import java.util.UUID

const val SIZE = 768
const val MAX_LAYERS = 8

data class Layer(
    val id: String = UUID.randomUUID().toString(), var name: String,
    val kind: String, var visible: Boolean = true, var locked: Boolean = false,
    var opacity: Float = 1f, var x: Float = 0f, var y: Float = 0f,
    var scale: Float = 1f, var rotation: Float = 0f,
    var bitmap: Bitmap? = null, var shape: String? = null,
    var color: Int = Color.rgb(40, 160, 150), var strokeColor: Int = Color.BLACK,
    var strokeWidth: Float = 2f, var svg: String? = null
) {
    fun duplicate() = copy(bitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true))
    fun matrix() = Matrix().apply {
        postTranslate(-SIZE / 2f, -SIZE / 2f)
        postScale(scale, scale); postRotate(rotation)
        postTranslate(SIZE / 2f + x, SIZE / 2f + y)
    }
    fun vectorXml(): String = svg ?: """<svg xmlns="http://www.w3.org/2000/svg" width="$SIZE" height="$SIZE" viewBox="0 0 $SIZE $SIZE"><g fill="${hex(color)}" fill-opacity="${Color.alpha(color)/255f}" stroke="${hex(strokeColor)}" stroke-width="$strokeWidth">${when(shape) {
        "circle" -> "<circle cx=\"384\" cy=\"384\" r=\"130\"/>"
        "ellipse" -> "<ellipse cx=\"384\" cy=\"384\" rx=\"170\" ry=\"100\"/>"
        "rectangle" -> "<rect x=\"230\" y=\"260\" width=\"308\" height=\"248\"/>"
        "line" -> "<line x1=\"220\" y1=\"260\" x2=\"540\" y2=\"500\"/>"
        "path" -> "<path d=\"M220 500 C220 170 550 170 550 500 Z\"/>"
        else -> error("Unknown shape")
    }}</g></svg>"""
}

fun hex(c: Int) = "#%06X".format(c and 0xffffff)
fun blankBitmap(): Bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

class Document(val layers: MutableList<Layer> = mutableListOf(Layer(name="Bitmap 1", kind="raster", bitmap=blankBitmap())), var selected: Int = 0) {
    val active get() = layers[selected]
    fun duplicate() = Document(layers.map { it.duplicate() }.toMutableList(), selected)
    fun estimatedBytes() = layers.count { it.bitmap != null }.coerceAtLeast(1) * SIZE * SIZE * 4L
    fun dispose() { layers.forEach { it.bitmap?.recycle() } }
    fun add(layer: Layer) { require(layers.size < MAX_LAYERS) { "Maksimal $MAX_LAYERS layer" }; layers.add(layer); selected=layers.lastIndex }
}

class History {
    private val undo = ArrayDeque<Document>()
    private val redo = ArrayDeque<Document>()
    fun record(doc: Document) {
        redo.forEach { it.dispose() }; redo.clear()
        undo.addLast(doc.duplicate())
        while (undo.size > 1 && undo.sumOf { it.estimatedBytes() } > 48L*1024*1024) undo.removeFirst().dispose()
        while (undo.size > 16) undo.removeFirst().dispose()
    }
    fun undo(doc: Document): Document? { if(undo.isEmpty()) return null; redo.addLast(doc); return undo.removeLast() }
    fun redo(doc: Document): Document? { if(redo.isEmpty()) return null; undo.addLast(doc); return redo.removeLast() }
    fun clear() { undo.forEach { it.dispose() }; redo.forEach { it.dispose() }; undo.clear(); redo.clear() }
}

/** Integration adapter: CiDrawing owns stroke point/path construction; Android Canvas rasterizes. */
class RasterAdapter(color: Int, size: Float, opacity: Int, erase: Boolean) {
    private val stroke = StrokeElement().apply {
        paint = CiPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=color; alpha=opacity; strokeWidth=size
            style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
            pathEffect=CornerPathEffect(12f)
            if (erase) xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }
    fun point(x: Float, y: Float) = stroke.addPoint(x, y)
    fun finish() = stroke.doneEditing()
    fun draw(canvas: Canvas) = stroke.drawElement(canvas)
}

class Compositor {
    private val cache = object : LinkedHashMap<String, SVG>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SVG>?) = size > 16
    }
    init { SVG.setInternalEntitiesEnabled(false); SVG.deregisterExternalFileResolver() }
    fun draw(canvas: Canvas, doc: Document, preview: Bitmap? = null) {
        for ((index,layer) in doc.layers.withIndex()) if (layer.visible) {
            canvas.save(); canvas.concat(layer.matrix())
            val save=canvas.saveLayerAlpha(0f,0f,SIZE.toFloat(),SIZE.toFloat(),(layer.opacity*255).toInt())
            if(layer.bitmap != null) canvas.drawBitmap(if(index==doc.selected && preview!=null) preview else layer.bitmap!!,0f,0f,null)
            else {
                val xml=layer.vectorXml()
                val parsed=cache.getOrPut(xml) { SVG.getFromString(xml) }
                parsed.renderToCanvas(canvas,RectF(0f,0f,SIZE.toFloat(),SIZE.toFloat()))
            }
            canvas.restoreToCount(save); canvas.restore()
        }
    }
    fun bitmap(doc: Document, white: Boolean=false): Bitmap = blankBitmap().also { b ->
        val c=Canvas(b); if(white)c.drawColor(Color.WHITE); draw(c,doc)
    }
}
