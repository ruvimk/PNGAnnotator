package com.gradgoose.pngannotator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/16/2017.
 */

public class SwipeableRecyclerView extends RecyclerView { 
	static final String TAG = "SwipeRV"; 
	
	float decayRate = 0.05f; 
	long timestep = 20; 
	
	final float MAX_DISPLACEMENT_FOR_CLICK; 
	
	public interface SwipeCallback { 
		void swipeComplete (int direction); 
		boolean canSwipe (int direction); 
	} 
	SwipeCallback mCallback = null; 
	void setSwipeCallback (SwipeCallback callback) { 
		mCallback = callback; 
	} 
	
//	ScaleGestureDetector mScaleGestureDetector = null; 
	
	String mNowBrowsingName = ""; 
	Vector<File> mParentFolder = null; 
	File mParentSubfolders [] [] = null; 
	int currentIndex = 0; 
	public SwipeableRecyclerView (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		DisplayMetrics metrics = getResources ().getDisplayMetrics (); 
		MIN_DELTA_TO_SWIPE = Math.min (metrics.widthPixels / 6,
				TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.75f,
						metrics)); 
		MIN_DISPLACEMENT_TO_SCROLL = Math.min (metrics.widthPixels / 8, 
				TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.75f, 
				metrics)); 
		MAX_DISPLACEMENT_FOR_CLICK = TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.5f, 
				metrics); 
//		mScaleGestureDetector = new ScaleGestureDetector (context, new ScaleGestureDetector.OnScaleGestureListener () { 
//			float prevScale = 1; 
//			@Override public boolean onScale (ScaleGestureDetector scaleGestureDetector) { 
//				float scale = prevScale * scaleGestureDetector.getScaleFactor (); 
//				setScaleX (scale); 
//				setScaleY (scale); 
//				setPivotX (scaleGestureDetector.getFocusX ()); 
//				setPivotY (scaleGestureDetector.getFocusY ()); 
//				prevScale = scale; 
//				isScaleEvent = true; 
//				return true; 
//			} 
//			
//			@Override public boolean onScaleBegin (ScaleGestureDetector scaleGestureDetector) { 
//				return true; 
//			} 
//			
//			@Override public void onScaleEnd (ScaleGestureDetector scaleGestureDetector) { 
//				setScaleX (1); 
//				setScaleY (1); 
//				isScaleEvent = false; 
//			} 
//		}); 
	} 
	public void setParentFolder (Vector<File> browsingParentFolder, String nowBrowsingFolderName) { 
		mParentFolder = browsingParentFolder; 
		mNowBrowsingName = nowBrowsingFolderName; 
		loadParentFolderSubfolders (); 
	} 
	private void loadParentFolderSubfolders () { 
		(new Thread () { 
			@Override public void run () { 
				// Grab the subfolders: 
				mParentSubfolders = 
						FileListCache.getFileLists (SubfoldersAdapter.mFilterJustFolders, 
						mParentFolder); 
				// Sort it, so that it's in order: 
				Arrays.sort (mParentSubfolders, SubfoldersAdapter.mFileComparator); 
				// Find which one we are looking at right now: 
				for (int i = 0; i < mParentSubfolders.length; i++) { 
					if (!mParentSubfolders[i][0].getName ().equals (mNowBrowsingName)) 
						continue; 
					currentIndex = i; 
					break; 
				} 
			} 
		}).start (); 
	} 
	long lastAnimatedT = 0; 
	Runnable mStepAnimation = new Runnable () { 
		@Override public void run () { 
			if (!stillAnimating) return; // Do nothing if animation canceled, etc. 
			long now = System.currentTimeMillis (); 
			float dt = 0.001f * (now - lastAnimatedT); 
			float deltaX = scrollVX * dt; 
			float deltaY = scrollVY * dt; 
			float scale = (float) Math.pow (decayRate, dt); 
			scrollVX *= scale; 
			scrollVY *= scale; 
			if (Math.abs (swipeDelta) >= MIN_DELTA_TO_SWIPE) 
				swipeDelta /= scale; 
			else swipeDelta *= scale; 
			updateSwipePosition (); 
			// Continue: 
			lastAnimatedT = now; 
			float leftOver = (float) Math.sqrt ( 
					deltaX * deltaX + 
							deltaY * deltaY + 
							swipeDelta * swipeDelta 
			); 
			if (leftOver > 1) 
				postDelayed (this, timestep); 
			else { 
				stillAnimating = false; 
				swipeDelta = 0; 
			} 
		} 
	}; 
	void finishScrollAnimation () { 
		mStepAnimation.run (); 
	} 
	void updateSwipePosition () { 
		boolean horizontal = isHorizontalOrientation (); 
		float effectQuotient = Math.abs (swipeDelta / (horizontal ? prevX - firstInterceptY : prevY - firstInterceptX)); 
		float effectScale = effectQuotient > 1 ? 1 : effectQuotient * effectQuotient; 
		float nowX = horizontal ? 0 : -swipeDelta * effectScale; 
		float nowY = horizontal ? -swipeDelta * effectScale: 0; 
		ViewGroup.LayoutParams lp = getLayoutParams (); 
		FrameLayout.LayoutParams flParams = lp instanceof FrameLayout.LayoutParams ? 
													 (FrameLayout.LayoutParams) lp : null; 
		if (flParams != null) { 
			flParams.leftMargin = (int) nowX; 
			flParams.rightMargin = - (int) nowX; 
			flParams.topMargin = (int) nowY; 
			flParams.bottomMargin = - (int) nowY; 
		} else { 
			LinearLayout.LayoutParams llParams = lp instanceof LinearLayout.LayoutParams ? 
														(LinearLayout.LayoutParams) lp : null; 
			if (llParams != null) {
				llParams.leftMargin = (int) nowX; 
				llParams.rightMargin = - (int) nowX; 
				llParams.topMargin = (int) nowY; 
				llParams.bottomMargin = - (int) nowY; 
			} 
		} 
		requestLayout (); 
		invalidate (); 
	} 
	// Touch override: 
	boolean allowTouch = true; 
	// Touch event data: 
	float firstCoordinate = 0; 
	float currentCoordinate = 0; 
	float firstX = 0; 
	float firstY = 0; 
	float firstDelta = 0; 
	float lastJumpX = 0; 
	float lastJumpY = 0; 
	float prevX = 0; 
	float prevY = 0; 
	long prevT = 0; 
	float scrollVX = 0; 
	float scrollVY = 0; 
	float touchTraveledDistance = 0; 
	// Swipe data: 
	float swipeDelta = 0; 
	boolean stillSwiping = false; 
	boolean stillAnimating = false; 
	float MIN_DELTA_TO_SWIPE; 
	float MIN_DISPLACEMENT_TO_SCROLL; 
	@Override public boolean onTouchEvent (MotionEvent event) { 
//		if (isScaleEvent) mScaleGestureDetector.onTouchEvent (event); 
		if (!allowTouch) return false; 
		if (event.getAction () == MotionEvent.ACTION_UP) isScaleEvent = false; 
		if (handleTouch ()) { 
			if (isScaleEvent) { 
				// Reset swipe position, and exit. 
				swipeDelta = 0; 
				updateSwipePosition (); 
				return true; 
			} 
			float x = event.getX (); 
			float y = event.getY (); 
			boolean horizontal = isHorizontalOrientation (); 
			float coordinate = horizontal ? event.getY () + getTop () 
									   : event.getX () + getLeft (); 
			int action = event.getAction (); 
			if (action == MotionEvent.ACTION_DOWN || 
						(action == MotionEvent.ACTION_MOVE && !stillSwiping)) { 
				firstCoordinate = coordinate; 
				firstX = lastJumpX = prevX = event.getX (); 
				firstY = lastJumpY = prevY = event.getY (); 
				prevT = System.currentTimeMillis (); 
				touchTraveledDistance = 0; // I know we may have multi-touch, but for simplicity assume one. 
				Log.d (TAG, "First coordinate: " + coordinate); 
				stillSwiping = true; 
				stillAnimating = false; 
				firstDelta = swipeDelta = (horizontal ? firstInterceptX - firstX : firstInterceptY - firstY); // Initial delta ... 
				if (canSwipe (swipeDelta)) { 
					updateSwipePosition (); 
				} 
			} else if (action == MotionEvent.ACTION_MOVE) { 
				currentCoordinate = coordinate; 
				swipeDelta = (canSwipe (firstCoordinate - currentCoordinate) ? firstCoordinate - currentCoordinate : 0) + firstDelta; 
				long now = System.currentTimeMillis (); 
				float dt = (float) (now - prevT) / 1e3f; 
				scrollVX = horizontal ? (prevX - x) / dt : 0; 
				scrollVY = horizontal ? 0 :  (prevY - y) / dt; 
				touchTraveledDistance += (float) Math.sqrt ((x - prevX) * (x - prevX) + 
																	(y - prevY) * (y - prevY)); 
				prevX = x; 
				prevY = y; 
				prevT = now; 
				Log.d (TAG, "Swipe delta: " + swipeDelta + " = " + firstCoordinate + " - " + 
					currentCoordinate); 
				if (canSwipe (swipeDelta)) 
					updateSwipePosition (); 
			} else if (action == MotionEvent.ACTION_UP) { 
				stillSwiping = false; 
				stillAnimating = true; 
				if (Math.abs (swipeDelta / (horizontal ? y - firstInterceptY : x - firstInterceptX)) > 1) { // i.e., we're not scrolling vertically while swiping horizontally ... 
					if (swipeDelta >= MIN_DELTA_TO_SWIPE && canSwipe (swipeDelta)) { 
						// Restore the scroll: 
						if (horizontal) 
							scrollBy ((int) (x - firstX), 0); 
						else scrollBy (0, (int) (y - firstY)); 
						// Open the next folder inside this folder's parent: 
//					int nextIndex = (currentIndex + 1) % mParentSubfolders.length; 
//					go (nextIndex); 
						go (+1); 
					} else if (swipeDelta <= -MIN_DELTA_TO_SWIPE && canSwipe (swipeDelta)) { 
						// Restore the scroll: 
						if (horizontal) 
							scrollBy ((int) (x - firstX), 0); 
						else scrollBy (0, (int) (y - firstY)); 
						// Open the previous folder inside this folder's parent: 
//					int nextIndex = currentIndex - 1; 
//					if (nextIndex < 0) 
//						nextIndex = mParentSubfolders.length - 1; 
//					go (nextIndex); 
						go (-1); 
					} 
				} 
				finishScrollAnimation (); 
			} else if (action == MotionEvent.ACTION_CANCEL) { 
				swipeDelta = 0; 
				updateSwipePosition (); 
			} 
			super.onTouchEvent (event); 
			getParent ().requestDisallowInterceptTouchEvent (true); 
			return true; 
		} else return super.onTouchEvent (event); 
	} 
	float firstInterceptX = 0; 
	float firstInterceptY = 0; 
	boolean isScaleEvent = false; 
	@Override public boolean onInterceptTouchEvent (MotionEvent event) { 
		if (!allowTouch) return false; 
		float x = event.getX (); 
		float y = event.getY (); 
		if (event.getAction () == MotionEvent.ACTION_DOWN) { 
			firstInterceptX = x; 
			firstInterceptY = y; 
			swipeDelta = 0; 
			firstDelta = 0; 
		} 
//		if (!isScaleEvent) mScaleGestureDetector.onTouchEvent (event); // Just let the detector know about this touch ... 
//		else getParent ().requestDisallowInterceptTouchEvent (true); 
		// The scale detector will set isScaleEvent, we will set it, if we detect a scale. 
		return (/*isScaleEvent || */(canSwipe (firstInterceptX - x) && 
						Math.abs (x - firstInterceptX) > Math.abs (y - firstInterceptY) && 
											 (Math.abs (x - firstInterceptX) >= MIN_DELTA_TO_SWIPE || 
											 Math.sqrt ((x - firstInterceptX) * (x - firstInterceptX) + 
												(y - firstInterceptY) * (y - firstInterceptY)) 
									 >= MIN_DISPLACEMENT_TO_SCROLL) 
		) 
											|| 
					   super.onInterceptTouchEvent (event)); 
	} 
	public void pageUp () { 
		LayoutManager lm = getLayoutManager (); 
		if (lm.getChildCount () == 0) 
			return; 
		View child = lm.getChildCount () > 1 ? 
							 lm.getChildAt (1) 
							 : lm.getChildAt (0); 
		pageScroll (-child.getWidth (), -child.getHeight ()); 
	} 
	public void pageDown () { 
		LayoutManager lm = getLayoutManager (); 
		if (lm.getChildCount () == 0) 
			return; 
		View child = lm.getChildCount () > 1 ? 
							 lm.getChildAt (1) 
							 : lm.getChildAt (0); 
		pageScroll (child.getWidth (), child.getHeight ()); 
	} 
	public void pageHome () { 
		scrollToPosition (0); 
	} 
	public void pageEnd () {  
		scrollToPosition (getAdapter ().getItemCount () - 1); 
	} 
	public void pageScroll (int width, int height) { 
		if (isHorizontalOrientation ()) 
			scrollBy (width, 0); 
		else scrollBy (0, height); 
	} 
	boolean isHorizontalOrientation () { 
		LayoutManager manager = getLayoutManager (); 
		boolean isHorizontalScrollOrientation = false; 
		if (manager instanceof LinearLayoutManager) 
			isHorizontalScrollOrientation = ((LinearLayoutManager) manager).getOrientation () 
													== LinearLayoutManager.HORIZONTAL; 
		return isHorizontalScrollOrientation; 
	} 
	void go (int direction) { 
		if (mCallback != null) 
			mCallback.swipeComplete (direction); 
	} 
	boolean canSwipe (float direction) { 
		return mCallback != null && mCallback.canSwipe (direction == 0 ? 0 : (direction > 0 ? +1 : -1)); 
	} 
//	void go (int index) {
//		Intent intent = new Intent (getContext (), NoteActivity.class); 
//		String current [] = new String [mParentSubfolders[index].length]; 
//		String parent [] = new String [mParentFolder.size ()]; 
//		for (int i = 0; i < mParentSubfolders[index].length; i++) 
//			current[i] = mParentSubfolders[index][i].getAbsolutePath (); 
//		for (int i = 0; i < mParentFolder.size (); i++) 
//			parent[i] = mParentFolder.elementAt (i).getAbsolutePath (); 
//		intent.putExtra (NoteActivity.STATE_BROWSING_PATH, current); 
//		intent.putExtra (NoteActivity.STATE_PARENT_BROWSE, parent); 
//		Activity activity = (Activity) getContext (); 
//		activity.startActivity (intent); 
//		activity.finish (); 
//	} 
//	public boolean canSwipe () { 
//		return mParentFolder != null && mParentSubfolders != null && 
//					   mParentSubfolders.length > 1; 
//	} 
	protected boolean handleTouch () { 
		return swipeDelta == 0f || canSwipe (swipeDelta); 
	} 
} 
