package com.gradgoose.pngannotator;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
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
	boolean allowZoomIn = true; 
	float currentScale = 1; 
	float nowPivotX = 0; 
	float nowPivotY = 0; 
	OnScaleDone onScaleDone = null; 
	void setOnScaleDoneListener (OnScaleDone listener) { 
		onScaleDone = listener; 
	} 
	public interface OnScaleDone { 
		void onZoomLeave (float pivotX, float pivotY); 
		void onVerticalPanState (boolean panning); 
	} 
	float xp0 = 0; 
	float yp0 = 0; 
	float x1 = 0; 
	float y1 = 0; 
	public ScaleDetectorContainer (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		mScaleGestureDetector = new ScaleGestureDetector (context, new ScaleGestureDetector.OnScaleGestureListener () { 
			float prevScale = 1; 
			float orgScale = 1; 
			@Override public boolean onScale (ScaleGestureDetector scaleGestureDetector) { 
				float scale = prevScale * scaleGestureDetector.getScaleFactor (); 
				if (scale > 1 && !allowZoomIn) scale = 1; 
				if (scale < 1 && !allowZoomOut) scale = 1; // If zoom-out not allowed, don't allow scale below 1. 
				if (orgScale > 1 && scale < 1) scale = 1; // If we're zooming out from a zoomed in position, don't allow overshooting. 
				setScale (scale, scale, (xp0 - x1 * scale) / (1 - scale), 
						(yp0 - y1 * scale) / (1 - scale)); 
				prevScale = scale; 
				isScaleEvent = true; 
				return true; 
			} 
			
			@Override public boolean onScaleBegin (ScaleGestureDetector scaleGestureDetector) { 
				prevScale = currentScale; 
				orgScale = currentScale; 
				return true; 
			} 
			
			@Override public void onScaleEnd (ScaleGestureDetector scaleGestureDetector) { 
				if (currentScale > .75 && currentScale <= 1) 
					setScale (1, 1, 0, 0); 
				else if (currentScale > 1) { 
					// Don't change currentScale; leave it zoomed in. 
					isScaleEvent = false; 
					return; 
				} else if (onScaleDone != null) onScaleDone.onZoomLeave (scaleGestureDetector.getFocusX (), 
						scaleGestureDetector.getFocusY ()); 
				else setScale (1, 1, 0, 0); 
				currentScale = 1; 
				isScaleEvent = false; 
			} 
		}); 
	} 
	@Override public boolean onTouchEvent (MotionEvent event) { 
		boolean result = true; 
		if (isScaleEvent) result = mScaleGestureDetector.onTouchEvent (event); 
		if (currentScale > 1 && event.getPointerCount () <= 2) { 
			// This may be a pan event. 
			handlePan (event); 
			if (onScaleDone != null) 
				onScaleDone.onVerticalPanState (verticalPanChanged); 
		} 
		if (!isScaleEvent) result = super.onTouchEvent (event); 
		if (event.getAction () == MotionEvent.ACTION_UP) { 
			if (onScaleDone != null) 
				onScaleDone.onVerticalPanState (false); 
		} 
		return result; 
	} 
	@Override public boolean onInterceptTouchEvent (MotionEvent event) { 
		if (event.getAction () == MotionEvent.ACTION_DOWN) { 
			calculateInitialFigures (event); 
			handlePan (event); 
		} 
		if (!isScaleEvent) { 
			mScaleGestureDetector.onTouchEvent (event); // Just let the detector know about this touch ... 
		} else { 
			getParent ().requestDisallowInterceptTouchEvent (true); 
		} 
		if (currentScale > 1 && event.getPointerCount () <= 2) { 
			// May be a pan event. 
			if (!isScaleEvent) { 
				handlePan (event); 
				if (onScaleDone != null) 
					onScaleDone.onVerticalPanState (verticalPanChanged); 
			} 
		} 
		if (event.getAction () == MotionEvent.ACTION_UP) { 
			if (onScaleDone != null) 
				onScaleDone.onVerticalPanState (false); 
		} 
		return isScaleEvent; 
	} 
	void calculateInitialFigures (MotionEvent event) { 
		float centerX = 0; 
		float centerY = 0; 
		for (int i = 0; i < event.getPointerCount () && i < 2; i++) { 
			centerX += event.getX (i); 
			centerY += event.getY (i); 
		} 
		centerX /= Math.min (event.getPointerCount (), 2); 
		centerY /= Math.min (event.getPointerCount (), 2); 
		calculateInitialFigures (centerX, centerY); 
//		Log.e ("ScaleDetector", "calculateInitialFigures (); p0: (" + xp0 + ", " + yp0 + "); (x1, y1): (" + x1 + ", " + y1 + "); "); 
	} 
	void calculateInitialFigures (float focusX, float focusY) { 
		xp0 = focusX; 
		yp0 = focusY; 
		x1 = (xp0 - nowPivotX) / currentScale + nowPivotX; 
		y1 = (yp0 - nowPivotY) / currentScale + nowPivotY; 
	} 
	float prevCenterX = 0; 
	float prevCenterY = 0; 
	float orgCenterX = 0; 
	float orgCenterY = 0; 
	int orgPointerId = 0; 
	boolean verticalPanChanged = false; 
	void handlePan (MotionEvent event) { 
//		float centerX = 0; 
//		float centerY = 0; 
//		for (int i = 0; i < event.getPointerCount () && i < 2; i++) { 
//			centerX += event.getX (i); 
//			centerY += event.getY (i); 
//		} 
//		centerX /= Math.min (event.getPointerCount (), 2); 
//		centerY /= Math.min (event.getPointerCount (), 2); 
		float x = event.getX (0); 
		float y = event.getY (0); 
		int pointerId = event.getPointerId (0); 
		if (pointerId != orgPointerId) { 
			for (int i = 1; i < event.getPointerCount (); i++) { 
				int id = event.getPointerId (i); 
				if (id != orgPointerId) continue; 
				x = event.getX (i); 
				y = event.getY (i); 
				pointerId = orgPointerId; 
				break; 
			} 
		} 
		if (event.getAction () == MotionEvent.ACTION_DOWN || pointerId != orgPointerId) { 
			orgCenterX = x; 
			orgCenterY = y; 
			orgPointerId = event.getPointerId (0); 
		} else { 
			float deltaXP = x - orgCenterX; 
			float deltaYP = y - orgCenterY; 
			float needPivotY = clamp ((yp0 + deltaYP - y1 * currentScale) / (1 - currentScale), 0, getHeight ()); 
			verticalPanChanged = needPivotY != nowPivotY; 
			setPivot (clamp ((xp0 + deltaXP - x1 * currentScale) / (1 - currentScale), 0, getWidth ()), 
					needPivotY); 
		} 
		prevCenterX = x; 
		prevCenterY = y; 
	} 
	static float clamp (float number, float low, float high) { 
		return Math.max (Math.min (number, high), low); 
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
		nowPivotX = pivotX; 
		nowPivotY = pivotY; 
		currentScale = scaleX; 
	} 
	void setPivot (float pivotX, float pivotY) { 
		setScale (currentScale, currentScale, pivotX, pivotY); 
	} 
} 
