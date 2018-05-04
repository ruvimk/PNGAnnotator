package com.gradgoose.pennotepad;

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
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PenIcon extends ImageView { 
	static final String TAG = "PenIcon"; 
	
	int mColor = Color.TRANSPARENT; 
	
	Paint mColorPaint = new Paint (); 
	
	public PenIcon (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		TypedArray a = context.getTheme ().obtainStyledAttributes (attributeSet, 
				R.styleable.PenIcon, 0, 0); 
		try { 
			mColor = a.getColor (R.styleable.PenIcon_color, mColor); 
		} finally { 
			a.recycle (); 
		} 
		mColorPaint.setStyle (Paint.Style.FILL); 
	} 
	
	public void setColor (int color) { 
		mColor = color; 
		invalidate (); 
	} 
	public int getColor () { 
		return mColor; 
	} 
	
	private void drawPenColor (Canvas canvas) { 
		int w = getWidth (); 
		int h = getHeight (); 
		mColorPaint.setColor (mColor); 
		canvas.save (); 
		
//		canvas.drawRect (0, 0, 10, 10, mColorPaint); 
		canvas.translate (w * 4 / 10, h * 4 / 10); 
		canvas.rotate (45); 
		canvas.scale (w / 6, h * 5 / 16); 
		canvas.translate (.4f, -.1f); 
		canvas.drawRect (0, 0, 1, 1, mColorPaint); 
		
		canvas.save (); 
		canvas.translate (1, 1); 
		canvas.rotate (23); 
		canvas.scale (1, .5f); 
		canvas.translate (-1, -1); 
		canvas.translate (.1f, 0); 
		canvas.drawRect (0, 0, 1, 1, mColorPaint); 
		canvas.restore (); 
		
		canvas.translate (.31f, 0); 
		canvas.scale (1, .6f); 
		canvas.drawRect (0, 0, 1, 1, mColorPaint); 
		
		canvas.restore (); 
		
		canvas.save (); 
		
		canvas.translate (w / 10, h * 9 / 10); 
		canvas.scale (w / 10, h / 10); 
		canvas.translate (1f, -1.7f); 
		canvas.skew (-.5f, 0); 
		canvas.scale (1.2f, 1.2f); 
		canvas.drawRect (0, 0, 1, 1, mColorPaint); 
		
		canvas.restore (); 
	} 
	
	@Override public void onDraw (Canvas canvas) { 
		drawPenColor (canvas); 
		super.onDraw (canvas); 
	} 
	
} 
