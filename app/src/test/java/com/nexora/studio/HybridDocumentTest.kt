package com.nexora.studio

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HybridDocumentTest {
    @Test fun hybridRoundTripRetainsVectorAndRasterAndExportIsNonDestructive() {
        val circle=Layer(name="Circle",kind="vector",shape="circle",color=Color.RED)
        val doc=Document(mutableListOf(circle),0)
        doc.add(Layer(name="Ink",kind="raster",bitmap=blankBitmap()))
        val brush=RasterAdapter(Color.BLUE,20f,255,false)
        brush.point(80f,80f);brush.point(200f,80f);brush.finish();brush.draw(Canvas(doc.active.bitmap!!))
        val bytes=ProjectIO.encode(doc);val restored=ProjectIO.decode(bytes)
        assertEquals(2,restored.layers.size);assertEquals(circle.id,restored.layers[0].id)
        assertEquals("vector",restored.layers[0].kind);assertEquals("circle",restored.layers[0].shape);assertNull(restored.layers[0].bitmap)
        assertEquals(Color.BLUE,restored.layers[1].bitmap!!.getPixel(100,80))
        val before=restored.layers.map { it.copy() }
        val export=Compositor().bitmap(restored)
        assertEquals(Color.RED,export.getPixel(384,384));assertEquals(Color.BLUE,export.getPixel(100,80));assertEquals(0,Color.alpha(export.getPixel(10,10)))
        assertEquals(before,restored.layers);assertEquals(2,ProjectIO.decode(ProjectIO.encode(restored)).layers.size)
        val encoded=ProjectIO.png(export);assertEquals(Color.RED,BitmapFactory.decodeByteArray(encoded,0,encoded.size).getPixel(384,384))
    }
    @Test fun eraserClearsOnlyBitmapAndUndoRedoRestoreStroke() {
        val doc=Document();val history=History();history.record(doc)
        val stroke=RasterAdapter(Color.BLUE,30f,255,false);stroke.point(100f,100f);stroke.point(200f,100f);stroke.finish();stroke.draw(Canvas(doc.active.bitmap!!))
        val undone=history.undo(doc)!!;assertEquals(0,Color.alpha(undone.active.bitmap!!.getPixel(150,100)))
        val redone=history.redo(undone)!!;assertEquals(Color.BLUE,redone.active.bitmap!!.getPixel(150,100))
        val eraser=RasterAdapter(Color.BLACK,40f,255,true);eraser.point(150f,80f);eraser.point(150f,120f);eraser.finish();eraser.draw(Canvas(redone.active.bitmap!!))
        assertEquals(0,Color.alpha(redone.active.bitmap!!.getPixel(150,100)))
    }
    @Test fun supportedSvgPreservesHolesClipsTransformsAndOriginalXml() {
        val xml="""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 768 768"><defs><clipPath id="clip"><rect width="768" height="768"/></clipPath></defs><g transform="translate(10 10)" clip-path="url(#clip)"><path fill="#ff0000" fill-rule="evenodd" d="M50 50H300V300H50Z M100 100H200V200H100Z"/></g></svg>"""
        SvgGuard.validate(xml)
        val doc=Document(mutableListOf(Layer(name="SVG",kind="svg",svg=xml)),0)
        val saved=ProjectIO.decode(ProjectIO.encode(doc));assertEquals(xml,saved.active.svg)
        val bitmap=Compositor().bitmap(saved);assertEquals(Color.RED,bitmap.getPixel(70,70));assertEquals(0,Color.alpha(bitmap.getPixel(150,150)))
        SvgGuard.validate(ProjectIO.exportSvg(doc))
    }
    @Test fun unsafeOrUnsupportedSvgIsRejected() {
        for(xml in listOf("<!DOCTYPE svg [<!ENTITY x 'bad'>]><svg/>","<svg><script/></svg>","<svg><image href='https://example.com/x'/></svg>","<svg><rect style='fill:red'/></svg>","<svg><g fill='url(https://example.com/x)'/></svg>")) {
            assertThrows(Exception::class.java){SvgGuard.validate(xml)}
        }
    }
    @Test fun invalidArchivesAreRejected() {
        fun zip(vararg names:String):ByteArray {val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->names.forEach{z.putNextEntry(ZipEntry(it));z.write("{}".toByteArray());z.closeEntry()}};return out.toByteArray()}
        assertThrows(Exception::class.java){ProjectIO.decode(zip("../document.json"))}
        assertThrows(Exception::class.java){ProjectIO.decode(zip("document.json"))}
        val doc=Document();val bytes=ProjectIO.encode(doc)
        assertThrows(Exception::class.java){ProjectIO.decode(bytes.copyOf(bytes.size/2))}
    }
    @Test fun transformsOrderingMetadataAndHistorySurvive() {
        val doc=Document();val vector=Layer(name="Scaled",kind="vector",shape="rectangle",x=31f,y=-12f,scale=.7f,rotation=45f,opacity=.4f,locked=true,visible=false)
        doc.add(vector);val result=ProjectIO.decode(ProjectIO.encode(doc))
        assertEquals(vector,result.active);assertEquals(1,result.selected)
        assertThrows(Exception::class.java){ProjectIO.exportSvg(doc)}
    }
    @Test fun svgExportRejectsCollidingResourceIds() {
        val xml="""<svg xmlns="http://www.w3.org/2000/svg"><rect id="same" width="100" height="100"/></svg>"""
        SvgGuard.validate(xml)
        val doc=Document(mutableListOf(Layer(name="A",kind="svg",svg=xml),Layer(name="B",kind="svg",svg=xml)),0)
        assertThrows(Exception::class.java){ProjectIO.exportSvg(doc)}
    }
    @Test fun atomicRecoveryKeepsLastCommittedProject() {
        val file=java.io.File.createTempFile("nexora-recovery-",".nxd")
        val doc=Document(mutableListOf(Layer(name="Committed circle",kind="vector",shape="circle")),0)
        ProjectIO.atomic(file,ProjectIO.encode(doc))
        val atomic=android.util.AtomicFile(file);val interrupted=atomic.startWrite()
        interrupted.write("partial interrupted write".toByteArray());interrupted.close()
        val recovered=ProjectIO.recover(file)!!
        assertEquals("Committed circle",recovered.active.name);assertEquals("circle",recovered.active.shape)
        file.delete()
    }
    @Test fun zipBombEntryIsStoppedAtDecompressionLimit() {
        val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->
            z.putNextEntry(ZipEntry("raster/00000000-0000-0000-0000-000000000000.png"))
            val block=ByteArray(8192);repeat(1025){z.write(block)};z.closeEntry()
        }
        assertTrue(out.size()<100_000)
        assertThrows(Exception::class.java){ProjectIO.decode(out.toByteArray())}
    }
    @Test fun pngAndJpegImportDecodeIntoBoundedImageLayers() {
        val input=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);input.eraseColor(Color.RED)
        for(format in listOf(Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.JPEG)) {
            val bytes=ByteArrayOutputStream().also{assertTrue(input.compress(format,100,it))}.toByteArray()
            val imported=ProjectIO.image(bytes);assertEquals(SIZE,imported.width);assertEquals(SIZE,imported.height)
            assertTrue(Color.red(imported.getPixel(384,384))>240);assertEquals(0,Color.alpha(imported.getPixel(10,10)));imported.recycle()
        }
        assertThrows(Exception::class.java){ProjectIO.image("corrupt image".toByteArray())}
    }
}
