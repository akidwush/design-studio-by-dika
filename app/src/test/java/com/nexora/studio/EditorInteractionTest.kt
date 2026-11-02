package com.nexora.studio

import android.content.Intent
import android.graphics.Color
import android.os.Looper
import android.view.*
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorInteractionTest {
    private fun descendants(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{descendants(view.getChildAt(it))}else emptyList()
    @Test fun homeOpensEditorAndTouchActuallyDrawsAndErases() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get()
        descendants(activity.window.decorView).filterIsInstance<Button>().first{it.text=="Proyek baru"}.performClick()
        val editor=descendants(activity.window.decorView).filterIsInstance<EditorView>().single()
        editor.layout(0,0,768,768)
        fun touch(action:Int,x:Float,y:Float){val e=MotionEvent.obtain(0,100,action,x,y,0);editor.onTouchEvent(e);e.recycle()}
        editor.color=Color.BLUE;editor.brushSize=30f
        touch(MotionEvent.ACTION_DOWN,200f,200f);touch(MotionEvent.ACTION_MOVE,400f,200f);touch(MotionEvent.ACTION_UP,400f,200f)
        assertEquals(Color.BLUE,activity.doc.active.bitmap!!.getPixel(280,188))
        editor.tool="Eraser";editor.brushSize=50f
        touch(MotionEvent.ACTION_DOWN,290f,160f);touch(MotionEvent.ACTION_MOVE,290f,240f);touch(MotionEvent.ACTION_UP,290f,240f)
        assertEquals(0,Color.alpha(activity.doc.active.bitmap!!.getPixel(280,188)))
        controller.pause().stop().destroy();shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun shareProviderRejectsTraversalAndWrites() {
        val provider=Robolectric.buildContentProvider(ShareProvider::class.java).create().get()
        assertThrows(Exception::class.java){provider.openFile(android.net.Uri.parse("content://com.nexora.studio.files/../secret"),"r")}
        assertThrows(Exception::class.java){provider.openFile(android.net.Uri.parse("content://com.nexora.studio.files/project.nxd"),"w")}
    }
}
