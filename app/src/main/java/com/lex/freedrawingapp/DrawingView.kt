package com.lex.freedrawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

//We need to inherit from View(), and for that we need to pass Context amd AttributeSet
class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    //We need variables like color, paint, bitmap file to draw on
    // brush thickness, path to draw, Arraylist of paths to store all drawn paths

    private var mDrawPath : CustomPath? = null //A variable of CustomPath class to use

    private var mCanvasBitmap: Bitmap? = null //An instance of canvas

    //Paint class holds the style and color info about how to draw text, line and bitmaps.
    private var mDrawPaint: Paint? = null

    private var mCanvasPaint: Paint? = null //An instance of canvas paint view

    //Variable for stroke/brush size to draw on the canvas(screen).
    private var mBrushSize: Float = 0.toFloat()

    //Variable to hold color of the stroke
    private var color = Color.BLACK

    /**
     * A variable for canvas which will be initialized later and used.
     *
     *The Canvas class holds the "draw" calls. To draw something, you need 4 basic components: A Bitmap to hold the pixels, a Canvas to host
     * the draw calls (writing into the bitmap), a drawing primitive (e.g. Rect,
     * Path, text, Bitmap), and a paint (to describe the colors and styles for the
     * drawing)
     */
    private var canvas: Canvas? = null
    private var mPaths = ArrayList<CustomPath>() //ArrayList for Paths
    private var mUndoPaths = ArrayList<CustomPath>() //ArrayList to store Undo draw Paths

    init {
        setUpDrawing()
    }

    /**
     * This function is called when the user selects the undo
     * command from the application. This function removes the
     * last stroke input by the user depending on the
     * number of times undo has been activated.
     */
    fun onClickUndo(){
        if (mPaths.size > 0){
            mUndoPaths.add(mPaths.removeAt(mPaths.size - 1))
            invalidate() //Invalidate the whole view. If the view is visible
        }
    }

    /**
     * This function is called when the user selects the redo
     * command from the application. This function returns the
     * removed stroke input by the user depending on the
     * number of times undo was activated.
     */
    fun onClickRedo(){
        if (mUndoPaths.size > 0){
            mPaths.add(mUndoPaths.removeAt(mUndoPaths.size - 1))
            invalidate() //Invalidate the whole view. If the view is visible
        }
    }

    /**
     * Function to initialize the attributes of the
     * DrawingView(TheViewForDrawing) class.
     */
    private fun setUpDrawing(){
        mDrawPaint = Paint()
        mDrawPath = CustomPath(color,mBrushSize)
        mDrawPaint!!.color = color
        mDrawPaint!!.style = Paint.Style.STROKE //This is to draw a STROKE style
        mDrawPaint!!.strokeJoin = Paint.Join.ROUND //This is for stroke join
        mDrawPaint!!.strokeCap = Paint.Cap.ROUND //This is for stroke Cap
        mCanvasPaint = Paint(Paint.DITHER_FLAG) //Paint flag that enables dithering when blitting.
        //mBrushSize = 20.toFloat()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(mCanvasBitmap!!)
    }

    /**
     * This method is called when a stroke is drawn on the canvas
     * as a part of the painting.
     */
    //Change Canvas to Canvas? if fails
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        /**
        *If the bitmap and canvas have different densities, this function will take care of
        * automatically scaling the bitmap to draw at the same density as the canvas.
        *
        * @param bitmap The bitmap to be drawn (mCanvasBitmap)
        * @param left The position of the left side of the bitmap being drawn
        * @param top The position of the top side of the bitmap being drawn
        * @param paint The paint used to draw the bitmap (may be null) (mCanvasPaint)
        */
        canvas.drawBitmap(mCanvasBitmap!!, 0f,0f, mCanvasPaint)

        //To make drawpath remain on screen
        for (path in mPaths){
            mDrawPaint!!.strokeWidth = path.brushThickness
            mDrawPaint!!.color = path.color
            canvas.drawPath(path, mDrawPaint!!)
        }

        if (!mDrawPath!!.isEmpty){
            mDrawPaint!!.strokeWidth = mDrawPath!!.brushThickness
            mDrawPaint!!.color = mDrawPath!!.color
            canvas.drawPath(mDrawPath!!, mDrawPaint!!)
        }
    }

    //Event listener Actions set for when we touch the screen
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event?.x
        val touchY = event?.y

        when(event?.action){
            //When screen is touched
            MotionEvent.ACTION_DOWN ->{
                mDrawPath!!.color = color
                mDrawPath!!.brushThickness = mBrushSize

                mDrawPath!!.reset()
                if (touchX != null) {
                    if (touchY != null) {
                        mDrawPath!!.moveTo(touchX,touchY) //Set the beginning of the next contour to the point (x,y)
                    }
                }
            }

            //When moving around on screen
            MotionEvent.ACTION_MOVE ->{
                if (touchX != null) {
                    if (touchY != null) {
                        mDrawPath!!.lineTo(touchX, touchY) // Add a line from the last point to the specified point (x,y)
                    }
                }
            }

            //When we release touch
            MotionEvent.ACTION_UP ->{
                mPaths.add(mDrawPath!!) //Add drawn path to canvas and add in the path arraylist
                mDrawPath = CustomPath(color,mBrushSize)
            }
            //Return false for any other touch action
            else -> return false
        }

        invalidate()
        return true
    }

    /**
    * Method is called when brush size are to be changed or set.
    * This method sets the brush/eraser
    * sizes to the new values depending on user selection.
    */
    fun setSizeForBrush(newSize : Float) {
        mBrushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        newSize, resources.displayMetrics)

        mDrawPaint!!.strokeWidth = mBrushSize
    }

    /**
     * This function is called when the user desires a color change.
     * This functions sets the color of a store to selected color and able to draw on view using that color.
     * @param newColor
     */
    fun setColor(newColor: String){
        color = Color.parseColor(newColor)
        mDrawPaint!!.color = color
    }

    //An inner class for custom path with two params as color and stroke
    internal inner class CustomPath(var color: Int, var brushThickness: Float) : Path(){

    }

}