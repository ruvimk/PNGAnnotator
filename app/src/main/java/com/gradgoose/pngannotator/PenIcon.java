package com.gradgoose.pngannotator;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PenIcon extends ImageView { 
	int mColor = Color.TRANSPARENT; 
	
	Bitmap mBitmap = null; 
	Canvas mCanvas = null; 
	int pixels [] = null; 
	
	public PenIcon (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		TypedArray a = context.getTheme ().obtainStyledAttributes (attributeSet, 
				R.styleable.PenIcon, 0, 0); 
		try { 
			mColor = a.getColor (R.styleable.PenIcon_color, mColor); 
		} finally { 
			a.recycle (); 
		} 
	} 
	
	static final int COLOR_ZERO_THRESHOLD = 50; 
	static boolean isGreen (int color) { 
		return Color.green (color) > COLOR_ZERO_THRESHOLD && 
					   Color.red (color) < COLOR_ZERO_THRESHOLD &&
					   Color.blue (color) < COLOR_ZERO_THRESHOLD; 
	} 
	
	public void setColor (int color) { 
		mColor = color; 
		initBitmap (); 
	} 
	public int getColor () { 
		return mColor; 
	} 
	
	boolean mUseCachedBitmap = false; 
	File penImage = null; 
	int mPenWidth = 1; 
	int mPenHeight = 1; 
	void initBitmap () { 
		int color = mColor; 
		File folder = PensAdapter.getPensFolder (getContext ()); 
		penImage = new File (folder, Integer.toString (color, 16) + ".png"); 
		BitmapFactory.Options options = null; 
		if (!penImage.exists ()) { // Create a cache of the bitmap: 
			// Initialize, if have not done so yet: 
			ensureBitmapRightSize ();
			// Draw the pen bitmap: 
			Bitmap greenPen = BitmapFactory.decodeResource (getResources (), R.mipmap.ic_green_pen);
			mCanvas.drawBitmap (greenPen, 0, 0, null);
			greenPen.recycle ();
			// Now go and replace pure green (0, 255, 0) with our color: 
			mBitmap.getPixels (pixels, 0, mBitmap.getWidth (),
					0, 0, mBitmap.getWidth (), mBitmap.getHeight ());
			for (int i = 0; i < pixels.length; i++)
				if (isGreen (pixels[i]))
					pixels[i] = mColor;
			mBitmap.setPixels (pixels, 0, mBitmap.getWidth (),
					0, 0, mBitmap.getWidth (), mBitmap.getHeight ());
			// Save the bitmap to the cache file: 
			if (penImage != null) {
				try {
					// Write the cached bitmap into file: 
					FileOutputStream outputStream = new FileOutputStream (penImage, false);
					mBitmap.compress (Bitmap.CompressFormat.PNG, 100, outputStream);
					outputStream.close ();
					// Set the image bitmap to this: 
					setImageBitmap (mBitmap); 
					// From now on, use the cached bitmap: 
					mUseCachedBitmap = true;
				} catch (IOException e) {
					e.printStackTrace ();
				}
			}
		} else { 
			options = new BitmapFactory.Options (); 
			options.inJustDecodeBounds = true; 
			BitmapFactory.decodeFile (penImage.getPath (), options); 
			mPenWidth = options.outWidth; 
			mPenHeight = options.outHeight; 
			try { 
				setImageURI (Uri.fromFile (penImage)); 
			} catch (OutOfMemoryError err) {
				Toast.makeText (getContext (), R.string.title_out_of_mem,
						Toast.LENGTH_SHORT).show ();
				err.printStackTrace ();
			}
			requestLayout ();
			invalidate ();
			mUseCachedBitmap = true;
		} 
	} 
	
	@Override public void onMeasure (int widthMeasureSpec, int heightMeasureSpec) { 
		setMeasuredDimension (mPenWidth, mPenHeight); 
	} 
	
	Paint mPenFill = new Paint (); 
	@Override public void onDraw (Canvas canvas) { 
		if (mUseCachedBitmap) { 
			// Just draw the regular way, which draws the cached picture file: 
			super.onDraw (canvas); 
		} 
	} 
	
	void ensureBitmapRightSize () { 
		int needW = getWidth (); 
		int needH = getHeight (); 
		if (mBitmap == null || mBitmap.getWidth () != needW || mBitmap.getHeight () != needH) { 
			if (mBitmap != null) 
				mBitmap.recycle (); 
			mBitmap = Bitmap.createBitmap (needW, needH, Bitmap.Config.ARGB_8888); 
			mCanvas = new Canvas (mBitmap); 
			pixels = new int [needW * needH]; 
		} 
	} 
	
} 
