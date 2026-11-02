package com.nexora.studio

import android.app.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity:Activity() {
    var doc=Document()
    val history=History()
    var busy=false;private set
    private lateinit var editor:EditorView
    private lateinit var status:TextView
    private val worker=Executors.newSingleThreadExecutor()
    private val latestAutosave=AtomicReference<Document?>()
    private val savingAutosave=AtomicBoolean(false)
    private var destination:Uri?=null
    private var output="nxd"
    private var pending:ByteArray?=null
    private val recovery get()=File(filesDir,"recovery.nxd")
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.decorView.setOnApplyWindowInsetsListener { view,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30){val i=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(i.left,i.top,i.right,i.bottom)}
            else {@Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)}
            insets
        }
        home()
    }
    private fun column()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12);setBackgroundColor(0xff202630.toInt())}
    private fun button(text:String,action:()->Unit)=Button(this).apply {this.text=text;setOnClickListener{if(!busy)action()}}
    private fun row(parent:LinearLayout,vararg items:Pair<String,()->Unit>) {
        val row=LinearLayout(this);for((label,action)in items)row.addView(button(label,action),LinearLayout.LayoutParams(0,52.dp(),1f));parent.addView(row)
    }
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
    private fun home() {
        val root=column();root.addView(TextView(this).apply {text="NEXORA\nDraw Studio";textSize=34f;setTextColor(Color.WHITE);setPadding(16,64,16,32)})
        root.addView(TextView(this).apply {text="Editor offline • Bitmap + vector\n768 × 768 • maksimal 8 layer";textSize=18f;setPadding(16,16,16,32)})
        root.addView(button("Proyek baru"){newProject()})
        root.addView(button("Buka NXD / Impor gambar atau SVG"){openPicker()})
        root.addView(button("Pulihkan autosave"){task("Memulihkan",{ProjectIO.recover(recovery) ?: error("Belum ada autosave")}){replace(it);showEditor()}})
        root.addView(button("Lisensi & batasan"){about()});setContentView(root)
    }
    private fun newProject(){history.clear();doc.dispose();doc=Document();destination=null;showEditor();changed()}
    private fun showEditor() {
        val root=column()
        row(root,"Menu" to {menu()},"Undo" to {editor.cancelStroke();history.undo(doc)?.let {doc=it;changed()}},"Redo" to {editor.cancelStroke();history.redo(doc)?.let {doc=it;changed()}},"Layer" to {layers()})
        row(root,"Tool" to {chooseTool()},"Warna" to {colorDialog()},"Shape" to {shapeDialog()},"Fit" to {editor.resetView()})
        status=TextView(this).apply {textSize=13f;setTextColor(Color.WHITE)};root.addView(status)
        editor=EditorView(this,this);root.addView(editor,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(TextView(this).apply {text="Ukuran brush"})
        root.addView(SeekBar(this).apply {max=99;progress=13;setOnSeekBarChangeListener(slider {editor.brushSize=(it+1).toFloat()})})
        root.addView(TextView(this).apply {text="Opacity brush"})
        root.addView(SeekBar(this).apply {max=255;progress=255;setOnSeekBarChangeListener(slider {editor.brushOpacity=it})})
        setContentView(root);refreshStatus()
    }
    private fun slider(change:(Int)->Unit)=object:SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar:SeekBar,value:Int,fromUser:Boolean){if(fromUser)change(value)}
        override fun onStartTrackingTouch(bar:SeekBar){}
        override fun onStopTrackingTouch(bar:SeekBar){}
    }
    fun refreshStatus(){if(::status.isInitialized)status.text="${editor.tool} · ${doc.active.name} (${doc.selected+1}/${doc.layers.size})"}
    fun message(text:String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
    fun changed(){if(::editor.isInitialized){editor.invalidate();refreshStatus()};autosave()}
    private fun edit(action:()->Unit){editor.cancelStroke();if(doc.active.locked){message("Buka kunci layer dahulu");return};history.record(doc);action();changed()}
    private fun replace(next:Document){history.clear();doc.dispose();doc=next;destination=null}
    private fun chooseTool(){val tools=arrayOf("Brush","Eraser","Select","Move","Pan");AlertDialog.Builder(this).setTitle("Tool • pinch untuk zoom/pan").setItems(tools){_,i->editor.cancelStroke();editor.tool=tools[i];refreshStatus()}.show()}
    private fun shapeDialog(){val shapes=arrayOf("circle","rectangle","ellipse","line","path");AlertDialog.Builder(this).setTitle("Tambah objek vector").setItems(shapes){_,i->add(Layer(name=shapes[i],kind="vector",shape=shapes[i],color=editor.color))}.show()}
    private fun add(layer:Layer){if(doc.layers.size>=MAX_LAYERS){layer.bitmap?.recycle();message("Maksimal $MAX_LAYERS layer");return};history.record(doc);doc.add(layer);changed()}
    private fun colorDialog(){
        val input=EditText(this).apply {setText(hex(editor.color));isSingleLine=true}
        AlertDialog.Builder(this).setTitle("Warna HEX #RRGGBB / #AARRGGBB").setView(input).setPositiveButton("Terapkan"){_,_->
            try{val color=Color.parseColor(input.text.toString());editor.color=color
                if(doc.active.kind=="vector")edit {doc.active.color=color}
            }catch(e:IllegalArgumentException){message("HEX tidak valid")}
        }.setNegativeButton("Batal",null).show()
    }
    private fun layers(){
        val names=doc.layers.mapIndexed { i,l->"${if(i==doc.selected)"● " else ""}${if(l.visible)"" else "[hidden] "}${if(l.locked)"[lock] " else ""}${l.name} · ${l.kind}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Layer • bawah → atas").setItems(names){_,i->editor.cancelStroke();doc.selected=i;refreshStatus();editor.invalidate();layerOptions()}
            .setPositiveButton("+ Bitmap"){_,_->add(Layer(name="Bitmap ${doc.layers.size+1}",kind="raster",bitmap=blankBitmap()))}.setNegativeButton("Tutup",null).show()
    }
    private fun layerOptions(){
        val options=arrayOf("Ganti nama","Naik","Turun","Show / Hide","Lock / Unlock","Opacity","Transform","Stroke vector","Hapus")
        AlertDialog.Builder(this).setTitle(doc.active.name).setItems(options){_,i->when(i){
            0->textDialog("Nama layer",doc.active.name){name->require(name.length in 1..100);edit{doc.active.name=name}}
            1->if(doc.selected<doc.layers.lastIndex)edit{val l=doc.layers.removeAt(doc.selected);doc.selected++;doc.layers.add(doc.selected,l)}
            2->if(doc.selected>0)edit{val l=doc.layers.removeAt(doc.selected);doc.selected--;doc.layers.add(doc.selected,l)}
            3->edit{doc.active.visible=!doc.active.visible}
            4->{history.record(doc);doc.active.locked=!doc.active.locked;changed()}
            5->textDialog("Opacity 0–100",(doc.active.opacity*100).toInt().toString()){v->val opacity=v.toFloat()/100;require(opacity.isFinite()&&opacity in 0f..1f);edit{doc.active.opacity=opacity}}
            6->transformDialog()
            7->if(doc.active.kind=="vector")textDialog("Stroke: #RRGGBB, ukuran 0–100","${hex(doc.active.strokeColor)},${doc.active.strokeWidth}"){v->val p=v.split(',');val c=Color.parseColor(p[0].trim());val w=p[1].trim().toFloat();require(w.isFinite()&&w in 0f..100f);edit{doc.active.strokeColor=c;doc.active.strokeWidth=w}}else message("Stroke hanya untuk shape Nexora")
            8->if(doc.layers.size>1)edit{doc.layers.removeAt(doc.selected);doc.selected=doc.selected.coerceAtMost(doc.layers.lastIndex)}else message("Satu layer harus tersisa")
        }}.show()
    }
    private fun transformDialog(){val l=doc.active;textDialog("x,y,scale(0.1–8),rotasi(-360–360)","${l.x},${l.y},${l.scale},${l.rotation}"){v->
        val p=v.split(',').map {it.trim().toFloat()};require(p.size==4&&p.all{it.isFinite()}&&p[0] in -4096f..4096f&&p[1] in -4096f..4096f&&p[2] in .1f..8f&&p[3] in -360f..360f)
        edit{l.x=p[0];l.y=p[1];l.scale=p[2];l.rotation=p[3]}
    }}
    private fun textDialog(title:String,value:String,accept:(String)->Unit){val input=EditText(this).apply{setText(value)};AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("OK"){_,_->try{accept(input.text.toString())}catch(e:Exception){message("Nilai tidak valid")}}.setNegativeButton("Batal",null).show()}
    private fun menu(){val options=arrayOf("Proyek baru","Buka / Impor","Save NXD","Save As NXD","Export PNG transparan","Export PNG putih","Export SVG (vector saja)","Share NXD","Lisensi & batasan")
        AlertDialog.Builder(this).setTitle("Nexora Draw Studio").setItems(options){_,i->when(i){
            0->AlertDialog.Builder(this).setMessage("Buat proyek baru? Simpan proyek saat ini dahulu bila dibutuhkan.").setPositiveButton("Baru"){_,_->newProject()}.setNegativeButton("Batal",null).show()
            1->openPicker();2->save("nxd",false);3->save("nxd",true);4->save("png",true);5->save("white",true);6->save("svg",true);7->share();8->about()
        }}.show()
    }
    private fun openPicker(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},10)}
    private fun save(kind:String,saveAs:Boolean){
        editor.cancelStroke();output=kind
        val snapshot=doc.duplicate()
        task("Menyiapkan $kind",{
            try {when(kind){"nxd"->ProjectIO.encode(snapshot);"svg"->ProjectIO.exportSvg(snapshot).toByteArray();else->{val bitmap=Compositor().bitmap(snapshot,kind=="white");try{ProjectIO.png(bitmap)}finally{bitmap.recycle()}}}}finally{snapshot.dispose()}
        }){bytes->pending=bytes
            if(kind=="nxd" && !saveAs && destination!=null)write(destination!!,bytes) else startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type=when(kind){"nxd"->"application/zip";"svg"->"image/svg+xml";else->"image/png"};putExtra(Intent.EXTRA_TITLE,"Nexora.${if(kind=="white")"png" else kind}")},11)
        }
    }
    private fun write(uri:Uri,bytes:ByteArray){task("Menyimpan",{if(output=="nxd")ProjectIO.atomic(File(filesDir,"last-save.nxd"),bytes);contentResolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)};true}){if(output=="nxd")destination=uri;pending=null;message("Tersimpan")}}
    @Deprecated("Platform result API used to avoid an additional UI dependency")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK){pending=null;return};val uri=data?.data?:return
        if(requestCode==11){pending?.let{write(uri,it)}?:message("Data ekspor hilang; silakan export kembali");return}
        if(requestCode==10)task("Membuka",{
            val bytes=contentResolver.openInputStream(uri)!!.use{ProjectIO.readBounded(it)}
            when {
                bytes.size>=2&&bytes[0]==0x50.toByte()&&bytes[1]==0x4b.toByte()->ProjectIO.decode(bytes)
                bytes.take(256).toByteArray().toString(Charsets.UTF_8).trimStart().startsWith("<")->{val xml=bytes.toString(Charsets.UTF_8);SvgGuard.validate(xml);Layer(name="SVG preserved (read-only)",kind="svg",svg=xml)}
                else->Layer(name="Imported image",kind="image",bitmap=ProjectIO.image(bytes))
            }
        }){value->if(value is Document){replace(value);destination=uri;showEditor();changed()}else{if(!::editor.isInitialized)showEditor();add(value as Layer)}}
    }
    private fun share(){editor.cancelStroke();val snapshot=doc.duplicate();task("Menyiapkan share",{try{ProjectIO.atomic(File(cacheDir,"shared-project.nxd"),ProjectIO.encode(snapshot))}finally{snapshot.dispose()};true}){
        val uri=Uri.parse("content://$packageName.files/project.nxd")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="application/zip";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("Nexora.nxd",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Bagikan NXD"))
    }}
    private fun autosave(){
        latestAutosave.getAndSet(doc.duplicate())?.dispose()
        drainAutosave()
    }
    private fun drainAutosave(){
        if(!savingAutosave.compareAndSet(false,true))return
        worker.execute{
            try {
                while(true){val snapshot=latestAutosave.getAndSet(null)?:break
                    try{ProjectIO.atomic(recovery,ProjectIO.encode(snapshot))}catch(e:Exception){runOnUiThread{message("Autosave gagal: ${e.message}")}}finally{snapshot.dispose()}
                }
            }finally{savingAutosave.set(false);if(latestAutosave.get()!=null&&!worker.isShutdown)drainAutosave()}
        }
    }
    private fun <T> task(label:String,work:()->T,done:(T)->Unit){busy=true;if(::status.isInitialized)status.text=label
        worker.execute{try{val result=work();runOnUiThread{busy=false;try{done(result)}catch(e:Exception){message(e.message?:"Operasi gagal")};if(::editor.isInitialized)refreshStatus()}}catch(e:Exception){runOnUiThread{busy=false;message("$label gagal: ${e.message}");if(::editor.isInitialized)refreshStatus()}}}
    }
    private fun about(){AlertDialog.Builder(this).setTitle("Nexora 0.1 • batas MVP").setMessage("CiDrawing: Apache 2.0, stroke path. AndroidSVG 1.4: Apache 2.0, render SVG.\n\nBrush smooth tunggal + eraser. Belum ada pencil, pressure, node editing, boolean, groups, fill bucket, text, atau CDR. SVG impor read-only mempertahankan XML; subset tidak didukung ditolak. HEX color; belum hue wheel. Lihat ENGINE_AUDIT.md dan THIRD_PARTY_NOTICES.md.").setPositiveButton("Lisensi"){_,_->val text=listOf("THIRD_PARTY_NOTICES.md","cidrawing.txt","androidsvg.txt","kotlin.txt","kotlin-NOTICE.txt","jetbrains-annotations.txt").joinToString("\n\n"){name->assets.open("licenses/$name").bufferedReader().use{it.readText()}};AlertDialog.Builder(this).setTitle("Third-party notices & licenses").setMessage(text).setPositiveButton("OK",null).show()}.setNegativeButton("Tutup",null).show()}
    override fun onStop(){if(::editor.isInitialized){editor.cancelStroke();autosave()};super.onStop()}
    override fun onDestroy(){worker.shutdown();super.onDestroy()}
}
