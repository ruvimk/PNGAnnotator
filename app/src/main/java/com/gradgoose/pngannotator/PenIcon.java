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
import android.util.AttributeSet;
import android.widget.ImageView;

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
		setImageResource (R.mipmap.ic_green_pen); 
//		mPenFill.setColorFilter (new PorterDuffColorFilter (Color.GREEN, PorterDuff.Mode.SRC)); 
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
	} 
	public int getColor () { 
		return mColor; 
	} 
	
	Paint mPenFill = new Paint (); 
	@Override public void onDraw (Canvas canvas) { 
		// Initialize, if have not done so yet: 
		ensureBitmapRightSize (); 
		// Let the superclass draw the pen for us: 
		super.onDraw (mCanvas); 
		// Now go and replace pure green (0, 255, 0) with our color: 
		mBitmap.getPixels (pixels, 0, mBitmap.getWidth (), 
				0, 0, mBitmap.getWidth (), mBitmap.getHeight ()); 
		for (int i = 0; i < pixels.length; i++) 
			if (isGreen (pixels[i])) 
				pixels[i] = mColor; 
		mBitmap.setPixels (pixels, 0, mBitmap.getWidth (), 
				0, 0, mBitmap.getWidth (), mBitmap.getHeight ()); 
		canvas.drawBitmap (mBitmap, 0, 0, null); 
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
