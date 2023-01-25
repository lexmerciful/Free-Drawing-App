package com.lex.freedrawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColor
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import yuku.ambilwarna.AmbilWarnaDialog;

class MainActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {

    private var drawingView: DrawingView? = null
    private var imageButtonCurrentPaint : ImageButton? = null
    private var ibGallery : ImageButton? = null
    private var customProgressDialog: Dialog? = null

    private lateinit var resultUri : Uri
    private lateinit var ibShare : ImageButton
    private var mDefaultColor = 0
    private lateinit var colorWheel: ImageButton

    //Brush Dialog and Seekbar
    private lateinit var brushDialog : Dialog
    private var sbText : TextView? = null
    private lateinit var sbBrushSize : SeekBar

    //Open Gallery Launcher
    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if (result.resultCode == RESULT_OK && result.data != null){
                val ivBackground: ImageView = findViewById(R.id.ivBackground)

                ivBackground.setImageURI(result.data?.data)
            }
        }

    //Permission Request with MultiplePermissions since we are requesting both read and write
    private var requestLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted) {
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)

                }else if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                    Toast.makeText(this, "Storage Access Permission Denied", Toast.LENGTH_SHORT).show()
                }else if (permissionName == Manifest.permission.WRITE_EXTERNAL_STORAGE){
                    Toast.makeText(this, "Write to Storage Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        val ibBrush: ImageButton = findViewById(R.id.ibBrush)
        val llPaintColors = findViewById<LinearLayout>(R.id.llPaintColors)

        llPaintColors.visibility = View.GONE
        imageButtonCurrentPaint = llPaintColors[1] as ImageButton

        //Color Picker Button
        val ibColorPicker : ImageButton = findViewById(R.id.ibColorPicker)
        ibColorPicker.setOnClickListener {
            if (llPaintColors.visibility == View.GONE) {
                llPaintColors.visibility = View.VISIBLE

                colorWheel= findViewById(R.id.colorWheel)
                colorWheel.setOnClickListener {
                    openColorPickerDialogue()
                }
            }else{
                llPaintColors.visibility = View.GONE
            }
        }

        //Undo Button
        val ibUndo : ImageButton = findViewById(R.id.ibUndo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        //Redo Button
        val ibRedo : ImageButton = findViewById(R.id.ibRedo)
        ibRedo.setOnClickListener {
            drawingView?.onClickRedo()
        }

        //Save Button
        val ibSave : ImageButton = findViewById(R.id.ibSave)
        ibSave.setOnClickListener {
            showProgressDialog()
            //check if permission is allowed
            if (isReadStorageAllowed()){
                //launch a coroutine block
                lifecycleScope.launch {
                    //reference the frame layout
                    val flDrawingViewContainer: FrameLayout = findViewById(R.id.flDrawingViewContainer)
                    //Save the image to the device
                    saveBitmapFile(getBitmapFromView(flDrawingViewContainer))
                }
            }

        }

        //Share Button
        ibShare = findViewById(R.id.ibShare)
        ibShare.isVisible = false
        ibShare.setOnClickListener {
                setUpEnablingFeat(resultUri)
        }

        //Gallery Image Picker Button
        ibGallery = findViewById(R.id.ibGalley)
        ibGallery?.setOnClickListener {
            requestStoragePermission()
        }

        brushDialog()

        drawingView?.setSizeForBrush(20.toFloat())
        //Brush Size Button
        ibBrush.setOnClickListener {
            showBrushSizeSelectorDialog()
        }
    }

    /**
     * Method is used to launch the Color Picker dialog to select different color options.
     */
    private fun openColorPickerDialogue() {
        val colorPickerDialogue = AmbilWarnaDialog(this, mDefaultColor,
            object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog?) {
                }

                override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                    mDefaultColor = color
                    val stringColor = Integer.toHexString(mDefaultColor).substring(2)
                    colorWheel.tag = "#$stringColor"
                    Log.e("COLOUR", stringColor)

                    //mColorPreview.setBackgroundColor(mDefaultColor)
                    drawingView?.setColor("#$stringColor")
                    if (colorWheel != imageButtonCurrentPaint){
                        colorWheel.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.pallet_pressed))
                        imageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.pallet_normal))
                    }
                    imageButtonCurrentPaint = colorWheel
                }
            })
        colorPickerDialogue.show()
    }

    /**
     * Calling this method to check the permission status
     */
    private fun isReadStorageAllowed() : Boolean{
        val result = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            showRationaleDialog(
                "Free Drawing App",
                "Free Drawing App can't use gallery because storage access is denied, allow permission in app setting"
            )
        } else {
            requestLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE/*, Manifest.permission.WRITE_EXTERNAL_STORAGE*/))
        }
    }

    /**
     * Method is used to launch the dialog to select different brush sizes.
     */
    private fun showBrushSizeSelectorDialog(){
        sbBrushSize.setOnSeekBarChangeListener(this)
        brushDialog.show()
    }

    private fun brushDialog() {
        brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")

        sbText = brushDialog.findViewById(R.id.sbText)
        sbBrushSize = brushDialog.findViewById(R.id.sbBrushSize)

        sbText?.text = "20"
        sbBrushSize.progress = 20
    }

    override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
        //drawingView?.setSizeForBrush(progress.toFloat())
        sbText?.text = progress.toString()
        drawingView?.setSizeForBrush(progress.toFloat())
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {
    }

    override fun onStopTrackingTouch(progress: SeekBar?) {
        brushDialog.dismiss()
    }

    /**
     * Method is called when color is clicked from pallet_normal.
     * @param view ImageButton on which click took place.
     */
    fun paintClicked (view : View){
        if (view != imageButtonCurrentPaint){
            val imageButton = view as ImageButton
            //Here the tag is used for swaping the current color with previous color. The tag stores the selected view
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            // Swap the backgrounds for last active and currently active image button
            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
            imageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_normal))
            colorWheel.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.pallet_normal))

            imageButtonCurrentPaint = view
        }
    }

    private fun showRationaleDialog(title: String, message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") {dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    /**
     * Create bitmap from view and returns it
     */
    private fun getBitmapFromView(view: View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap) //Bind a canvas to the bitmap
        val bgDrawable = view.background //Get the view's background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        // draw the view on the canvas
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(myBitmap: Bitmap) : String{
        var result = ""

        withContext(Dispatchers.IO){
            if (myBitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()
                    myBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString()
                    + File.separator + "FreeDrawingApp_" + System.currentTimeMillis()/1000 + ".jpg")

                val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath
                    resultUri = FileProvider.getUriForFile(applicationContext,"com.lex.freedrawingapp.fileprovider", f)

                    runOnUiThread {
                        //shareImage(result)
                        setUpEnablingFeat(FileProvider.getUriForFile(applicationContext,"com.lex.freedrawingapp.fileprovider", f))
                        cancelProgressDialog()
                        if (result.isNotEmpty()){
                            Toast.makeText(this@MainActivity, "File saved successfully :$result",
                                Toast.LENGTH_SHORT).show()
                            ibShare.isVisible = true
                        }else{
                            Toast.makeText(this@MainActivity, "Something went wrong while saving file",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.setCancelable(false)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if (customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result: String){
        MediaScannerConnection.scanFile(applicationContext, arrayOf(result), null){
            paths, uri ->
            val requestFile = File(externalCacheDir?.absoluteFile.toString() + File.separator, paths)
            val fileUri: Uri? = try {
                FileProvider.getUriForFile(applicationContext,"com.lex.freedrawingapp.fileprovider", requestFile)
            }catch (e: IllegalArgumentException){
                Log.e("File Selector", "The selected file cannot be shared: $requestFile")
            null}
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
            shareIntent.type = "image/jpeg"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }

    private fun setUpEnablingFeat(uri: Uri){
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "image/jpeg"
        startActivity(Intent.createChooser(shareIntent,"Share"))
    }
}