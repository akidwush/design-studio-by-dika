package com.nexora.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.*
import java.util.zip.*

object ProjectIO {
    const val MAX_FILE = 32*1024*1024
    private const val MAX_ENTRY = 8*1024*1024
    fun readBounded(input: InputStream, limit: Int = MAX_FILE): ByteArray {
        val out=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0
        while(true) { val n=input.read(buffer); if(n<0)break; total+=n; require(total<=limit){"File terlalu besar"}; out.write(buffer,0,n) }
        return out.toByteArray()
    }
    fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }.toByteArray()
    fun image(bytes: ByteArray, exact: Boolean=false): Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 && bounds.outWidth.toLong()*bounds.outHeight<=32_000_000){"Dimensi gambar tidak didukung"}
        if(exact)require(bounds.outWidth==SIZE && bounds.outHeight==SIZE){"Ukuran layer tidak valid"}
        var sample=1
        while(bounds.outWidth/sample>SIZE*2 || bounds.outHeight/sample>SIZE*2)sample*=2
        val b=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample; inMutable=true }) ?: error("Gambar rusak")
        if(exact)return b
        val target=blankBitmap();val ratio=minOf(SIZE.toFloat()/b.width,SIZE.toFloat()/b.height)
        val w=b.width*ratio;val h=b.height*ratio
        android.graphics.Canvas(target).drawBitmap(b,null,android.graphics.RectF((SIZE-w)/2,(SIZE-h)/2,(SIZE+w)/2,(SIZE+h)/2),android.graphics.Paint(3));b.recycle();return target
    }
    fun encode(doc: Document): ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name:String,data:ByteArray){zip.putNextEntry(ZipEntry(name));zip.write(data);zip.closeEntry()}
            val layers=JSONArray()
            for(l in doc.layers) {
                val resource=if(l.bitmap!=null) "raster/${l.id}.png" else "vector/${l.id}.svg"
                layers.put(JSONObject().put("id",l.id).put("name",l.name).put("kind",l.kind).put("visible",l.visible).put("locked",l.locked)
                    .put("opacity",l.opacity).put("x",l.x).put("y",l.y).put("scale",l.scale).put("rotation",l.rotation)
                    .put("shape",l.shape ?: JSONObject.NULL).put("color",l.color).put("strokeColor",l.strokeColor).put("strokeWidth",l.strokeWidth).put("resource",resource))
                entry(resource,if(l.bitmap!=null)png(l.bitmap!!) else l.vectorXml().toByteArray(Charsets.UTF_8))
            }
            entry("document.json",JSONObject().put("schemaVersion",1).put("width",SIZE).put("height",SIZE).put("selected",doc.selected).put("layers",layers).toString().toByteArray())
            val preview=Compositor().bitmap(doc);entry("preview.png",png(preview));preview.recycle()
        }
        return out.toByteArray()
    }
    fun decode(bytes: ByteArray): Document {
        require(bytes.size<=MAX_FILE)
        // Require a complete central-directory footer; ZipInputStream alone accepts truncated ZIPs.
        require(bytes.size>=22){"ZIP terpotong"}
        val footer=java.nio.ByteBuffer.wrap(bytes,bytes.size-22,22).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        require(footer.int==0x06054b50 && footer.short.toInt()==0 && footer.short.toInt()==0){"ZIP footer tidak didukung"}
        val diskEntries=footer.short.toInt() and 0xffff;val totalEntries=footer.short.toInt() and 0xffff
        val directorySize=footer.int.toLong() and 0xffffffffL;val directoryOffset=footer.int.toLong() and 0xffffffffL
        require(diskEntries==totalEntries && totalEntries in 1..20 && directoryOffset+directorySize==bytes.size-22L && footer.short.toInt()==0){"ZIP directory tidak valid"}
        val entries=mutableMapOf<String,ByteArray>();var total=0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while(true) {
                val e=zip.nextEntry ?: break
                require(entries.size<20 && !e.isDirectory && e.name.matches(Regex("(document\\.json|preview\\.png|(raster|vector)/[a-f0-9-]{36}\\.(png|svg))")) && e.name !in entries){"Entry arsip tidak valid"}
                val data=readBounded(zip,MAX_ENTRY);total+=data.size;require(total<=48*1024*1024){"Arsip terlalu besar"};entries[e.name]=data
            }
        }
        require(entries.size==totalEntries){"Jumlah entry ZIP berbeda"}
        val root=JSONObject(String(entries["document.json"] ?: error("Manifest hilang"),Charsets.UTF_8))
        require(root.getInt("schemaVersion")==1 && root.getInt("width")==SIZE && root.getInt("height")==SIZE){"Schema tidak didukung"}
        val list=root.getJSONArray("layers");require(list.length() in 1..MAX_LAYERS)
        val result=mutableListOf<Layer>();val ids=mutableSetOf<String>();val refs=mutableSetOf("document.json","preview.png")
        try {
            for(i in 0 until list.length()) {
                val o=list.getJSONObject(i);val id=o.getString("id");require(id.matches(Regex("[a-f0-9-]{36}")) && ids.add(id))
                val kind=o.getString("kind");require(kind in setOf("raster","image","vector","svg"))
                val ref=o.getString("resource");require(ref==if(kind in setOf("raster","image"))"raster/$id.png" else "vector/$id.svg");refs.add(ref)
                val l=Layer(id=id,name=o.getString("name").also { require(it.length<=100) },kind=kind,visible=o.getBoolean("visible"),locked=o.getBoolean("locked"),
                    opacity=finite(o,"opacity",0f,1f),x=finite(o,"x",-4096f,4096f),y=finite(o,"y",-4096f,4096f),scale=finite(o,"scale",.1f,8f),rotation=finite(o,"rotation",-360f,360f),
                    shape=if(o.isNull("shape"))null else o.getString("shape"),color=o.getInt("color"),strokeColor=o.getInt("strokeColor"),strokeWidth=finite(o,"strokeWidth",0f,100f))
                val data=entries[ref] ?: error("Resource hilang")
                if(kind in setOf("raster","image"))l.bitmap=image(data,true) else {
                    val xml=String(data,Charsets.UTF_8); SvgGuard.validate(xml)
                    if(kind=="svg")l.svg=xml else {require(l.shape in setOf("circle","ellipse","rectangle","line","path"));require(xml==l.vectorXml()){"Metadata vektor berbeda"}}
                }
                result.add(l)
            }
            require(entries.keys.all { it in refs })
            val selected=root.getInt("selected");require(selected in result.indices)
            return Document(result,selected)
        } catch(e:Exception) { result.forEach { it.bitmap?.recycle() };throw e }
    }
    private fun finite(o: JSONObject,key:String,min:Float,max:Float):Float = o.getDouble(key).toFloat().also { require(it.isFinite() && it in min..max){"Transform tidak valid"} }
    fun atomic(file: File, bytes: ByteArray) {
        val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) } catch(e:Exception){atomic.failWrite(stream);throw e}
    }
    fun recover(file:File):Document? = if(file.exists() || File(file.path+".bak").exists()) AtomicFile(file).openRead().use { decode(readBounded(it)) } else null
    fun exportSvg(doc:Document):String {
        require(doc.layers.none { it.bitmap!=null }){"Export SVG hanya untuk dokumen vector. Gunakan PNG atau NXD untuk dokumen hybrid."}
        val ids=mutableSetOf<String>()
        for(l in doc.layers)for(id in SvgGuard.ids(l.vectorXml()))require(ids.add(id)){"ID resource SVG antar-layer bentrok; simpan sebagai NXD"}
        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$SIZE\" height=\"$SIZE\" viewBox=\"0 0 $SIZE $SIZE\">")
            for(l in doc.layers)append("<g visibility=\"${if(l.visible)"visible" else "hidden"}\" opacity=\"${l.opacity}\" transform=\"translate(${l.x} ${l.y}) translate(384 384) rotate(${l.rotation}) scale(${l.scale}) translate(-384 -384)\">${l.vectorXml().replace(Regex("<\\?xml[^>]*>"),"")}</g>")
            append("</svg>")
        }
    }
}

/** Explicit preserved SVG subset. Unsupported elements/attributes fail before AndroidSVG sees input. */
object SvgGuard {
    private val tags=setOf("svg","g","path","rect","circle","ellipse","line","polyline","polygon","defs","linearGradient","radialGradient","stop","clipPath")
    private val attrs=setOf("id","version","width","height","viewBox","preserveAspectRatio","x","y","x1","y1","x2","y2","cx","cy","r","rx","ry","d","points","fill","fill-rule","fill-opacity","stroke","stroke-width","stroke-opacity","stroke-linecap","stroke-linejoin","stroke-miterlimit","stroke-dasharray","stroke-dashoffset","opacity","transform","clip-path","clip-rule","clipPathUnits","gradientUnits","gradientTransform","spreadMethod","offset","stop-color","stop-opacity","fx","fy","visibility")
    fun ids(xml:String):List<String> {
        val p=Xml.newPullParser();p.setInput(StringReader(xml));val result=mutableListOf<String>()
        while(p.eventType!=XmlPullParser.END_DOCUMENT){if(p.eventType==XmlPullParser.START_TAG)p.getAttributeValue(null,"id")?.let{result.add(it)};p.next()}
        return result
    }
    fun validate(xml: String) {
        require(xml.toByteArray().size<=1024*1024){"SVG maksimal 1 MiB"}
        require(!Regex("<!|<\\?xml-stylesheet",RegexOption.IGNORE_CASE).containsMatchIn(xml)){"DTD/entity tidak diizinkan"}
        val p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);p.setInput(StringReader(xml));var count=0;var roots=0
        while(p.eventType!=XmlPullParser.END_DOCUMENT) {
            if(p.eventType==XmlPullParser.START_TAG) {
                require(++count<=5000 && p.depth<=16 && p.name in tags && p.namespace in setOf("","http://www.w3.org/2000/svg")){"Elemen SVG tidak didukung"}
                if(p.depth==1){require(p.name=="svg" && ++roots==1)}
                for(i in 0 until p.attributeCount) {
                    val name=p.getAttributeName(i);val value=p.getAttributeValue(i)
                    require(p.getAttributeNamespace(i).isNullOrEmpty() && name in attrs && value.length<=100_000){"Atribut SVG tidak didukung: $name"}
                    require(!Regex("(?i)(https?:|file:|data:|javascript:|@import)").containsMatchIn(value))
                    if(value.contains("url",true))require(value.matches(Regex("url\\(#[A-Za-z_][A-Za-z0-9_.-]*\\)"))){"Resource eksternal ditolak"}
                }
            } else if(p.eventType==XmlPullParser.TEXT)require(p.text.isBlank()){"Teks SVG belum didukung"}
            p.next()
        }
        require(roots==1)
        val identifiers=ids(xml);require(identifiers.size==identifiers.toSet().size){"ID SVG duplikat"}
        com.caverock.androidsvg.SVG.setInternalEntitiesEnabled(false)
        com.caverock.androidsvg.SVG.getFromString(xml)
    }
}
