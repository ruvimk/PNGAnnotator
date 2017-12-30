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
	boolean allowZoomOut = false; 
	float currentScale = 1; 
	OnScaleDone onScaleDone = null; 
	void setOnScaleDoneListener (OnScaleDone listener) { 
		onScaleDone = listener; 
	} 
	public interface OnScaleDone { 
		void onZoomLeave (float pivotX, float pivotY); 
	} 
	public ScaleDetectorContainer (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		mScaleGestureDetector = new ScaleGestureDetector (context, new ScaleGestureDetector.OnScaleGestureListener () { 
			float prevScale = 1; 
			@Override public boolean onScale (ScaleGestureDetector scaleGestureDetector) { 
				float scale = prevScale * scaleGestureDetector.getScaleFactor (); 
				float x = scaleGestureDetector.getFocusX (); 
				float y = scaleGestureDetector.getFocusY (); 
				if (scale < 1 && !allowZoomOut) scale = 1; // If zoom-out not allowed, don't allow scale below 1. 
				setScale (scale, scale, x, y); 
				prevScale = scale; 
				isScaleEvent = true; 
				return true; 
			} 
			
			@Override public boolean onScaleBegin (ScaleGestureDetector scaleGestureDetector) { 
				prevScale = currentScale; 
				return true; 
			} 
			
			@Override public void onScaleEnd (ScaleGestureDetector scaleGestureDetector) { 
				if (currentScale > .75 && currentScale < 1) 
					setScale (1, 1, 0, 0); 
				else if (onScaleDone != null) onScaleDone.onZoomLeave (scaleGestureDetector.getFocusX (), 
						scaleGestureDetector.getFocusY ()); 
				else setScale (1, 1, 0, 0); 
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
	void setScale (float scaleX, float scaleY, float pivotX, float pivotY) { 
		int childCount = getChildCount (); 
		for (int childIndex = 0; childIndex < childCount; childIndex++) { 
			View child = getChildAt (childIndex); 
			child.setScaleX (scaleX); 
			child.setScaleY (scaleY); 
			child.setPivotX (pivotX); 
			child.setPivotY (pivotY); 
		} 
		currentScale = scaleX; 
	} 
} 