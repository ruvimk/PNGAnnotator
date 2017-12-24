package com.gradgoose.pngannotator;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Created by Ruvim Kondratyev on 12/23/2017.
 */

public class ScaleDetectorContainer extends FrameLayout { 
	ScaleGestureDetector mScaleGestureDetector = null; 
	boolean isScaleEvent = false; 
	public ScaleDetectorContainer (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		mScaleGestureDetector = new ScaleGestureDetector (context, new ScaleGestureDetector.OnScaleGestureListener () { 
			float prevScale = 1; 
			@Override public boolean onScale (ScaleGestureDetector scaleGestureDetector) { 
				float scale = prevScale * scaleGestureDetector.getScaleFactor (); 
				float x = scaleGestureDetector.getFocusX (); 
				float y = scaleGestureDetector.getFocusY (); 
				int childCount = getChildCount (); 
				for (int childIndex = 0; childIndex < childCount; childIndex++) { 
					View child = getChildAt (childIndex); 
					child.setScaleX (scale); 
					child.setScaleY (scale); 
					child.setPivotX (x); 
					child.setPivotY (y); 
				} 
				prevScale = scale; 
				isScaleEvent = true; 
				return true; 
			} 
			
			@Override public boolean onScaleBegin (ScaleGestureDetector scaleGestureDetector) { 
				prevScale = 1; 
				return true; 
			} 
			
			@Override public void onScaleEnd (ScaleGestureDetector scaleGestureDetector) { 
				int childCount = getChildCount (); 
				for (int childIndex = 0; childIndex < childCount; childIndex++) { 
					View child = getChildAt (childIndex); 
					child.setScaleX (1); 
					child.setScaleY (1); 
				} 
				isScaleEvent = false; 
			} 
		}); 
	} 
	@Override public boolean onTouchEvent (MotionEvent event) { 
		if (isScaleEvent) return mScaleGestureDetector.onTouchEvent (event); 
		else return super.onTouchEvent (event); 
	} 
	@Override public boolean onInterceptTouchEvent (MotionEvent event) { 
		if (!isScaleEvent) mScaleGestureDetector.onTouchEvent (event); // Just let the detector know about this touch ... 
		else getParent ().requestDisallowInterceptTouchEvent (true); 
		return isScaleEvent; 
	} 
} 
