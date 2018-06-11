package com.gradgoose.pennotepad;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.Scroller;

/**
 * Created by Ruvim Kondratyev on 12/23/2017.
 */

public class ScaleDetectorContainer extends FrameLayout { 
	static final String TAG = "ScaleDetectorContainer"; 
	ScaleGestureDetector mScaleGestureDetector = null; 
	GestureDetector mGeneralGestureDetector = null; 
	EdgeEffect mOverscrollEdgeEffect1 = null; 
	EdgeEffect mOverscrollEdgeEffect2 = null; 
	int VERTICAL_PAN_CAP = 2000; 
	int HORIZONTAL_PAN_PADDING = 1000; 
	boolean isScaleEvent = false; 
	boolean allowZoomOut = false; 
	boolean allowZoomIn = true; 
	float lastStayingScale = 1; 
	float currentScale = 1; 
	float nowPivotX = 0; 
	float nowPivotY = 0; 
	float zoomedInScale = 2; 
	void setZoomedInScale (float scale) { 
		zoomedInScale = scale; 
	} 
	OnScaleDone onScaleDone = null; 
	void setOnScaleDoneListener (OnScaleDone listener) { 
		onScaleDone = listener; 
	} 
	public interface OnScaleDone { 
		void onZoomLeave (float pivotX, float pivotY); 
		void onVerticalPanState (boolean panning); 
		void onZoomChanged (float nowScale); 
	} 
	float xp0 = 0; 
	float yp0 = 0; 
	float x1 = 0; 
	float y1 = 0; 
	float dx1 = 0; 
	float dy1 = 0; 
	final float mTouchSlop; 
	public ScaleDetectorContainer (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		mTouchSlop = ViewConfiguration.get (context).getScaledTouchSlop (); 
		mFlingScroller = new Scroller (context); 
		mOverscrollEdgeEffect1 = new EdgeEffect (context); 
		mOverscrollEdgeEffect2 = new EdgeEffect (context); 
		if (Build.VERSION.SDK_INT >= 21) { 
			mOverscrollEdgeEffect1.setColor (Color.BLACK); 
			mOverscrollEdgeEffect2.setColor (Color.BLACK); 
		} 
		setWillNotDraw (false); // Make invalidate () call onDraw (). 
		mScaleGestureDetector = new ScaleGestureDetector (context, new ScaleGestureDetector.OnScaleGestureListener () { 
			float prevScale = 1; 
			float orgScale = 1; 
			@Override public boolean onScale (ScaleGestureDetector scaleGestureDetector) { 
				float scale = prevScale * scaleGestureDetector.getScaleFactor (); 
				if (scale > 1 && !allowZoomIn) scale = 1; 
				if (scale < 1 && !allowZoomOut) scale = 1; // If zoom-out not allowed, don't allow scale below 1. 
				if (orgScale > 1 && scale < 1) scale = 1; // If we're zooming out from a zoomed in position, don't allow overshooting. 
				setScale (scale, scale, (xp0 - x1 * scale + dx1) / (1 - scale), 
						(yp0 - y1 * scale + dy1) / (1 - scale)); 
				prevScale = scale; 
				isScaleEvent = true; 
				return true; 
			} 
			
			@Override public boolean onScaleBegin (ScaleGestureDetector scaleGestureDetector) { 
				prevScale = currentScale; 
				orgScale = currentScale; 
				isScaleEvent = true; 
				return true; 
			} 
			
			@Override public void onScaleEnd (ScaleGestureDetector scaleGestureDetector) { 
				OnScaleDone listener = onScaleDone; 
				if (currentScale > .75 && currentScale <= 1.25) 
					setScale (1, 1, 0, 0); 
				else if (currentScale > 1.25) { 
					// Don't change currentScale; leave it zoomed in. 
					isScaleEvent = false; 
					refreshViews (); 
					if (listener != null) 
						listener.onZoomChanged (currentScale); 
					return; 
				} else if (listener != null) listener.onZoomLeave (scaleGestureDetector.getFocusX (), 
						scaleGestureDetector.getFocusY ()); 
				else setScale (1, 1, 0, 0); 
				currentScale = 1; 
				isScaleEvent = false; 
				refreshViews (); 
				if (listener != null) 
					listener.onZoomChanged (1); 
			} 
		}); 
		mGeneralGestureDetector = new GestureDetector (context, new GestureDetector.OnGestureListener () { 
			@Override public boolean onDown (MotionEvent motionEvent) { 
				return true; 
			} 
			@Override public void onShowPress (MotionEvent motionEvent) { 
				
			} 
			@Override public boolean onSingleTapUp (MotionEvent motionEvent) { 
				return false; 
			} 
			@Override public boolean onScroll (MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) { 
				return true; 
			} 
			@Override public void onLongPress (MotionEvent motionEvent) { 
				
			} 
			@Override public boolean onFling (MotionEvent motionEvent, MotionEvent motionEvent1, float vx, float vy) { 
				panVX = -vx; 
				panVY = -vy; 
				finishFlingAnimation (); 
				return true; 
			} 
		}); 
	} 
	private boolean disallowScale = false; 
	@Override public boolean onTouchEvent (MotionEvent event) { 
		boolean result = true; 
		mGeneralGestureDetector.onTouchEvent (event); // Listen for fling velocity. 
		if (event.getPointerCount () >= 3) { 
			isScaleEvent = false; // Don't allow scale if three or more fingers are touching the screen. 
			setScale (lastStayingScale, lastStayingScale, nowPivotX, nowPivotY); 
			disallowScale = true; 
		} 
		if (isScaleEvent && !disallowScale) result = mScaleGestureDetector.onTouchEvent (event); 
		if (event.getPointerCount () <= 3) { // If this is called when scale == 1, that's OK; it means we intercepted the touch, and the RecyclerView won't get this event anyway ... 
			// This may be a pan event. 
			handlePan (event); 
			if (onScaleDone != null) 
				onScaleDone.onVerticalPanState (verticalPanChanged); 
		} 
		if (!isScaleEvent) result = super.onTouchEvent (event); 
		if (event.getAction () == MotionEvent.ACTION_UP || event.getAction () == MotionEvent.ACTION_CANCEL) { 
			if (onScaleDone != null) 
				onScaleDone.onVerticalPanState (false); 
			checkClick (); 
			disallowScale = false; 
			getParent ().requestDisallowInterceptTouchEvent (false); 
			mOverscrollEdgeEffect1.onRelease (); 
			mOverscrollEdgeEffect2.onRelease (); 
			refreshViews (); 
		} 
		return result; 
	} 
	@Override public boolean onInterceptTouchEvent (MotionEvent event) { 
		mGeneralGestureDetector.onTouchEvent (event); // Listen for fling velocity. 
		if (event.getAction () == MotionEvent.ACTION_DOWN) { 
			cancelFlingAnimation (); // Cancel any animations running ... 
			isPanEvent = false; 
			touchDownTime = System.currentTimeMillis (); 
			panVX = panVY = 0; 
			nowPivotX = clamp (nowPivotX, 0, getWidth ()); 
			nowPivotY = clamp (nowPivotY, 0, getHeight ()); 
			orgPointerId = -1; // Set this like that so we don't confuse the pointers; we want to prevent unnecessary jumpiness ... 
			calculateInitialFigures (event); 
			handlePan (event); 
			disallowScale = false; // Sometimes this does not get reset with ACTION_UP or ACTION_CANCEL, so reset it here. 
			lastStayingScale = currentScale; 
		} 
		if (!isScaleEvent && !disallowScale && event.getPointerCount () < 3) { 
			mScaleGestureDetector.onTouchEvent (event); // Just let the detector know about this touch ... 
		} else { 
			getParent ().requestDisallowInterceptTouchEvent (true); 
		} 
		if (event.getPointerCount () >= 3) { 
			isScaleEvent = false; // Don't allow scale if three or more fingers are touching the screen. 
			setScale (lastStayingScale, lastStayingScale, nowPivotX, nowPivotY); 
			disallowScale = true; 
		} 
		if (currentScale > 1 && event.getPointerCount () <= 3) { 
			// May be a pan event. 
			handlePan (event); 
			if (!isScaleEvent) { 
				if (onScaleDone != null) 
					onScaleDone.onVerticalPanState (verticalPanChanged); 
			} 
		} 
		if (event.getAction () == MotionEvent.ACTION_UP || event.getAction () == MotionEvent.ACTION_CANCEL) { 
			if (onScaleDone != null) 
				onScaleDone.onVerticalPanState (false); 
			checkClick (); 
			disallowScale = false; 
			getParent ().requestDisallowInterceptTouchEvent (false); 
			refreshViews (); 
		} 
		return isScaleEvent || isPanEvent; 
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
	float panVX = 0; 
	float panVY = 0; 
	float prevCenterX = 0; 
	float prevCenterY = 0; 
	float orgCenterX = 0; 
	float orgCenterY = 0; 
	int orgPointerId = 0; 
	long orgPointerT = 0; 
	long prevPointerT = 0; 
	boolean verticalPanChanged = false; 
	boolean isPanEvent = false; 
	long touchDownTime = 0; 
	long lastClickTime = 0; 
	int clickCount = 0; 
	void checkClick () { 
		long now = System.currentTimeMillis (); 
		if (lastClickTime != 0 && now - lastClickTime > 500) 
			clickCount = 0; // Not a double-click. 
		float dx = prevCenterX - orgCenterX; 
		float dy = prevCenterY - orgCenterY; 
		double dist = Math.sqrt (dx * dx + dy * dy);
		ViewConfiguration conf = ViewConfiguration.get (getContext ()); 
		int touchSlop = conf.getScaledTouchSlop (); 
		if (dist <= touchSlop) { 
			clickCount++; 
			lastClickTime = now; 
			click (); 
		} 
	} 
	void resetTouchVariables (float x, float y, MotionEvent event) {
		resetTouchVariables (x, y); 
		orgPointerId = event.getPointerId (0); 
	} 
	void resetTouchVariables (float x, float y) {
		orgCenterX = x; 
		orgCenterY = y; 
		prevCenterX = x; 
		prevCenterY = y; 
		orgPointerT = System.currentTimeMillis (); 
		prevPointerT = orgPointerT; 
		dx1 = 0; 
		dy1 = 0; 
		calculateInitialFigures (x, y); 
	} 
	public void scrollByFloat (float offsetX, float offsetY) { 
		if (offsetX == 0 && offsetY == 0) return; // Let's just say: calling this method with (0, 0) has no effect, not even on velocity calculations. 
		if (currentScale <= 1) { 
			for (int i = 0; i < getChildCount (); i++) { 
				View child = getChildAt (i); 
				child.scrollBy ((int) offsetX, (int) offsetY); 
			} 
			return; 
		} 
		float needPivotX = nowPivotX + offsetX / (currentScale - 1); 
		float needPivotY = nowPivotY + offsetY / (currentScale - 1); 
		verticalPanChanged = needPivotY != nowPivotY; 
		setPivot (needPivotX, needPivotY); 
		if ((needPivotY > getHeight () || needPivotY < 0) && getChildCount () > 0) { 
			int direction = offsetY < 0 ? -1 : +1; 
			boolean canScroll = _childrenCanScrollVertically (direction); 
			if (canScroll) { 
				float h = (needPivotY > 0 ? needPivotY - getHeight () : needPivotY) * (currentScale - 1); 
//				int sY = h * (offsetY < 0 ? -1 : +1); 
//				float syp = (float) sY * currentScale; 
				float oldPivotY = needPivotY; 
//				needPivotY = clamp ((yp0 + deltaYP + syp - y1 * currentScale) / (1 - currentScale), 0, getHeight ()); 
				needPivotY -= h / (currentScale - 1); 
				Log.i (TAG, "End of pivot space reached; scrolling child views by " + h + "; old pivot Y: " + oldPivotY + "; now pivot Y: " + needPivotY); 
				for (int i = 0; i < getChildCount (); i++) { 
					View view = getChildAt (i); 
					view.scrollBy (0, (int) (h / currentScale)); 
				} 
				setPivot (needPivotX, needPivotY); 
				resetTouchVariables (prevCenterX, prevCenterY); 
			} else { 
				// TODO: Show feedback shadow thing that the user can't scroll anymore. 
			} 
		} 
	} 
	@Override public void scrollBy (int offsetX, int offsetY) { 
		scrollByFloat (offsetX, offsetY); 
	} 
	@Override public boolean canScrollVertically (int direction) { 
		return _childrenCanScrollVertically (direction) || 
					   (currentScale > 1 && (direction > 0 ? nowPivotY < getHeight () : nowPivotY > 0)); 
	} 
	private boolean _childrenCanScrollVertically (int direction) { 
		boolean canScroll = true; 
		for (int i = 0; i < getChildCount (); i++) 
			canScroll &= getChildAt (i).canScrollVertically (direction); 
		return canScroll; 
	} 
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
		long now = System.currentTimeMillis (); 
		float dt = (now - prevPointerT) * 1e-3f; 
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
			resetTouchVariables (x, y, event); 
		} else { 
			float deltaXP = x - orgCenterX; 
			float deltaYP = y - orgCenterY; 
			dx1 = deltaXP; 
			dy1 = deltaYP; 
			isPanEvent |= Math.abs (deltaYP) > mTouchSlop || Math.abs (deltaXP) > mTouchSlop; 
			scrollByFloat (prevCenterX - x, prevCenterY - y); 
			if (nowPivotX <= 0) { 
				mOverscrollEdgeEffect1.onPull (-nowPivotX); 
				mOverscrollDirection1 = 1; 
				invalidate (); 
			} else if (nowPivotX >= getWidth ()) { 
				mOverscrollEdgeEffect1.onPull (nowPivotX - getWidth ()); 
				mOverscrollDirection1 = 3; 
				invalidate (); 
			} 
			if (nowPivotY <= 0) { 
				mOverscrollEdgeEffect2.onPull (-nowPivotY); 
				mOverscrollDirection2 = 0; 
				invalidate (); 
			} else if (nowPivotY >= getHeight ()) { 
				mOverscrollEdgeEffect2.onPull (nowPivotY - getHeight ()); 
				mOverscrollDirection2 = 2; 
				invalidate (); 
			} 
		} 
		prevCenterX = x; 
		prevCenterY = y; 
		prevPointerT = now; 
	} 
	void finishFlingAnimation () { 
		if (isScaleEvent || !isPanEvent) return; 
		synchronized (mFlingMutex) { 
			mFlingCancel = false; 
			mFlingPrevT = System.currentTimeMillis (); 
			mFlingScroller.forceFinished (true); 
			mFlingPrevX = nowPivotX; 
			mFlingPrevY = 0; 
			mFlingScroller.fling ((int) nowPivotX, 0, (int) panVX, (int) panVY, 
					-HORIZONTAL_PAN_PADDING, getWidth () + HORIZONTAL_PAN_PADDING, -VERTICAL_PAN_CAP, +VERTICAL_PAN_CAP 
					); 
			if (!mFlingRunning) { 
				mFlingRunning = true; 
				post (mRunFling); 
			} 
		} 
	} 
	void cancelFlingAnimation () { 
		synchronized (mFlingMutex) { 
			if (mFlingRunning) 
				mFlingCancel = true; 
			mFlingScroller.forceFinished (true); 
		} 
	} 
	final Object mFlingMutex = new Object (); 
	boolean mFlingRunning = false; 
	boolean mFlingCancel = false; 
	float mFlingPrevX = 0; 
	float mFlingPrevY = 0; 
	long mFlingPrevT = 0; 
	final Scroller mFlingScroller; 
	Runnable mRunFling = new Runnable () { 
		@Override public void run () { 
			synchronized (mFlingMutex) { 
				if (mFlingCancel) { 
					mFlingRunning = false; 
					refreshViews (); 
					return; 
				} 
				if (mFlingScroller.computeScrollOffset ()) { 
					float nowX = mFlingScroller.getCurrX (); 
					float nowY = mFlingScroller.getCurrY (); 
					scrollByFloat (nowX - mFlingPrevX, nowY - mFlingPrevY); 
					if (nowPivotX <= 0) { 
						mOverscrollEdgeEffect1.onAbsorb ((int) mFlingScroller.getCurrVelocity ());  
						mOverscrollDirection1 = 1; 
						invalidate (); 
					} else if (nowPivotX >= getWidth ()) { 
						mOverscrollEdgeEffect1.onAbsorb ((int) mFlingScroller.getCurrVelocity ()); 
						mOverscrollDirection1 = 3; 
						invalidate (); 
					} 
					if (nowPivotY <= 0) { 
						mOverscrollEdgeEffect2.onAbsorb ((int) mFlingScroller.getCurrVelocity ());  
						mOverscrollDirection2 = 0; 
						invalidate (); 
					} else if (nowPivotY >= getHeight ()) { 
						mOverscrollEdgeEffect2.onAbsorb ((int) mFlingScroller.getCurrVelocity ()); 
						mOverscrollDirection2 = 2; 
						invalidate (); 
					} 
					mFlingPrevX = nowX; 
					mFlingPrevY = nowY; 
				} 
				if (!mFlingScroller.isFinished ()) { 
					postDelayed (this, 25); 
				} else { 
					mFlingRunning = false; 
					refreshViews (); 
				} 
			} 
		} 
	}; 
	static float clamp (float number, float low, float high) { 
		return Math.max (Math.min (number, high), low); 
	} 
	void click () { 
		// Check if it's a double-click, and if so, perform zoom. 
		if (clickCount == 2) { 
			// It's a double-click. 
			if (currentScale > 1) { 
				// Zoomed in. Need to zoom out. 
				zoomedInScale = currentScale; 
				initiateZoomAnimation (nowPivotX, nowPivotY, currentScale, 
						nowPivotX, nowPivotY, 1, 
						500); 
				OnScaleDone listener = onScaleDone; 
				if (listener != null)
					listener.onZoomChanged (1); 
			} else if (allowZoomIn) { 
				// Zoomed out. Need to zoom in. 
				nowPivotX = prevCenterX; 
				nowPivotY = prevCenterY; 
				initiateZoomAnimation (nowPivotX, nowPivotY, currentScale, 
						nowPivotX, nowPivotY, zoomedInScale, 
						500); 
				OnScaleDone listener = onScaleDone; 
				if (listener != null) 
					listener.onZoomChanged (zoomedInScale); 
			} 
		} else performClick (); 
	} 
	void initiateZoomAnimation (float fromPivotX, float fromPivotY, float fromZoom,
								float toPivotX, float toPivotY, float toZoom, 
								long duration) { 
		long now = System.currentTimeMillis (); 
		initiateZoomAnimation (fromPivotX, fromPivotY, fromZoom, 
				toPivotX, toPivotY, toZoom, 
				now, now + duration); 
	} 
	void initiateZoomAnimation (float fromPivotX, float fromPivotY, float fromZoom,
								float toPivotX, float toPivotY, float toZoom, 
								long timeStart, long timeStop) { 
		synchronized (mAnimateZoomMutex) { 
			mAnimatePivotFromX = fromPivotX; 
			mAnimatePivotFromY = fromPivotY; 
			mAnimateScaleFrom = fromZoom; 
			mAnimatePivotToX = toPivotX; 
			mAnimatePivotToY = toPivotY; 
			mAnimateScaleTo = toZoom; 
			mAnimateStart = timeStart; 
			mAnimateStop = timeStop; 
		} 
		mAnimateZoomInOut.run (); 
	} 
	final Object mAnimateZoomMutex = new Object ();
	float mAnimatePivotFromX = 0;
	float mAnimatePivotFromY = 0; 
	float mAnimateScaleFrom = 1;
	float mAnimatePivotToX = 0;
	float mAnimatePivotToY = 0; 
	float mAnimateScaleTo = 1; 
	long mAnimateStart = 0; 
	long mAnimateStop = 0; 
	Runnable mAnimateZoomInOut = new Runnable () { 
		@Override public void run () { 
			synchronized (mAnimateZoomMutex) { 
				long now = System.currentTimeMillis (); 
				if (now >= mAnimateStop) { 
					setScale (mAnimateScaleTo, mAnimateScaleTo, mAnimatePivotToX, mAnimatePivotToY); 
					refreshViews (); 
					return; 
				} 
				float t = (float) (now - mAnimateStart) / (float) (mAnimateStop - mAnimateStart); 
				float nowScale = mAnimateScaleTo * t + mAnimateScaleFrom * (1 - t); 
				float pivotX = mAnimatePivotToX * t + mAnimatePivotFromX * (1 - t); 
				float pivotY = mAnimatePivotToY * t + mAnimatePivotFromY * (1 - t); 
				setScale (nowScale, nowScale, pivotX, pivotY); 
				getHandler ().postDelayed (this, 25); 
			} 
		} 
	}; 
	void setScale () { 
		
		setScale (currentScale, currentScale, nowPivotX, nowPivotY); 
	} 
	void setScale (float scale) { 
		setScale (scale, scale, 0, 0); 
	} 
	void setScale (float scaleX, float scaleY, float pivotX, float pivotY) { 
		int childCount = getChildCount (); 
		for (int childIndex = 0; childIndex < childCount; childIndex++) { 
			View child = getChildAt (childIndex); 
			child.setScaleX (scaleX); 
			child.setScaleY (scaleY); 
			child.setPivotX (clamp (pivotX, 0, getWidth ())); 
			child.setPivotY (clamp (pivotY, 0, getHeight ())); 
		} 
		nowPivotX = pivotX; 
		nowPivotY = pivotY; 
		currentScale = scaleX; 
	} 
	void setPivot (float pivotX, float pivotY) { 
		setScale (currentScale, currentScale, pivotX, pivotY); 
	} 
	void refreshViews () { 
		int childCount = getChildCount (); 
		for (int childIndex = 0; childIndex < childCount; childIndex++) { 
			View child = getChildAt (childIndex); 
			child.invalidate (); 
		} 
	} 
	Runnable mSimpleRequestRedraw = new Runnable () { 
		@Override public void run () { 
			invalidate (); 
		} 
	}; 
	Runnable mRequestRedrawOnUiThread = new Runnable () { 
		@Override public void run () { 
			if (getContext () instanceof Activity) 
				((Activity) getContext ()).runOnUiThread (mSimpleRequestRedraw); 
		} 
	}; 
	int mOverscrollDirection1 = 0; // 0 = up, 1 = left, 2 = down, 3 = right; 
	int mOverscrollDirection2 = 0; 
	@Override public void onDraw (Canvas canvas) { 
		super.onDraw (canvas); 
		canvas.save (); 
		canvas.rotate (mOverscrollDirection1 * 90); 
		mOverscrollEdgeEffect1.draw (canvas); 
		canvas.restore (); 
		canvas.save (); 
		canvas.rotate (mOverscrollDirection2 * 90); 
		mOverscrollEdgeEffect2.draw (canvas); 
		canvas.restore (); 
		if (!mOverscrollEdgeEffect1.isFinished () || !mOverscrollEdgeEffect2.isFinished ()) 
			postDelayed (mRequestRedrawOnUiThread, 20); 
	} 
} 
