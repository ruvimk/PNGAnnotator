package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.RunnableFuture;

/**
 * Created by Ruvim Kondratyev on 9/16/2017.
 */

public class SwipeableRecyclerView extends RecyclerView { 
	static final String TAG = "SwipeRV"; 
	
	String mNowBrowsingName = ""; 
	Vector<File> mParentFolder = null; 
	File mParentSubfolders [] [] = null; 
	int currentIndex = 0; 
	public SwipeableRecyclerView (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet);
		MIN_DELTA_TO_SWIPE = TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.75f, 
				getResources ().getDisplayMetrics ()); 
		MIN_DELTA_TO_SHOW = MIN_DELTA_TO_SWIPE / 4; 
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
	float decayRate = 0.1f; 
	long timestep = 20; 
	long lastAnimatedT = 0; 
	Runnable mStepAnimation = new Runnable () { 
		@Override public void run () { 
			if (!stillAnimating) return; // Do nothing if animation canceled, etc. 
			long now = System.currentTimeMillis (); 
			float dt = 0.001f * (now - lastAnimatedT); 
			float deltaX = scrollVX * dt; 
			float deltaY = scrollVY * dt; 
			scrollBy ((int) deltaX, (int) deltaY); 
			float scale = (float) Math.pow (decayRate, dt); 
			scrollVX *= scale; 
			scrollVY *= scale; 
			swipeDelta *= scale; 
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
			else stillAnimating = false; 
		} 
	}; 
	void finishScrollAnimation () { 
		mStepAnimation.run (); 
	} 
	void updateSwipePosition () { 
		boolean horizontal = isHorizontalOrientation (); 
		float nowX = horizontal ? 0 : -swipeDelta; 
		float nowY = horizontal ? -swipeDelta : 0; 
		ViewGroup.LayoutParams lp = getLayoutParams (); 
		LinearLayout.LayoutParams llParams = lp instanceof LinearLayout.LayoutParams ? 
													 (LinearLayout.LayoutParams) lp : null; 
		if (llParams != null) { 
			llParams.leftMargin = (int) nowX; 
			llParams.rightMargin = - (int) nowX; 
			llParams.topMargin = (int) nowY; 
			llParams.bottomMargin = - (int) nowY; 
		} 
		requestLayout (); 
		invalidate (); 
	} 
	// Touch event data: 
	float firstCoordinate = 0; 
	float currentCoordinate = 0; 
	float firstX = 0; 
	float firstY = 0; 
	float prevX = 0; 
	float prevY = 0; 
	long prevT = 0; 
	float scrollVX = 0; 
	float scrollVY = 0; 
	// Swipe data: 
	float swipeDelta = 0; 
	boolean stillSwiping = false; 
	boolean stillAnimating = false; 
	float MIN_DELTA_TO_SWIPE; 
	float MIN_DELTA_TO_SHOW; 
	@Override public boolean onTouchEvent (MotionEvent event) { 
		if (canSwipe ()) { 
			boolean horizontal = isHorizontalOrientation (); 
			float coordinate = horizontal ? event.getY () + getTop () 
									   : event.getX () + getLeft (); 
			int action = event.getAction (); 
			if (action == MotionEvent.ACTION_DOWN || 
						(action == MotionEvent.ACTION_MOVE && !stillSwiping)) { 
				firstCoordinate = coordinate; 
				firstX = prevX = event.getX (); 
				firstY = prevY = event.getY (); 
				prevT = System.currentTimeMillis (); 
				Log.d (TAG, "First coordinate: " + coordinate); 
				stillSwiping = true; 
				stillAnimating = false; 
			} else if (action == MotionEvent.ACTION_MOVE) { 
				currentCoordinate = coordinate; 
				swipeDelta = firstCoordinate - currentCoordinate; 
				float x = event.getX (); 
				float y = event.getY (); 
				if (horizontal) 
					scrollBy ((int) (prevX - x), 0); 
				else scrollBy (0, (int) (prevY - y)); 
				long now = System.currentTimeMillis (); 
				float dt = (float) (now - prevT) / 1e3f; 
				scrollVX = horizontal ? (prevX - x) / dt : 0; 
				scrollVY = horizontal ? 0 :  (prevY - y) / dt; 
				prevX = x; 
				prevY = y; 
				prevT = now; 
				Log.d (TAG, "Swipe delta: " + swipeDelta + " = " + firstCoordinate + " - " + 
					currentCoordinate); 
				updateSwipePosition (); 
			} else if (action == MotionEvent.ACTION_UP) { 
				stillSwiping = false; 
				stillAnimating = true; 
				finishScrollAnimation (); 
			} 
			getParent ().requestDisallowInterceptTouchEvent (true); 
			return true; 
		} else return super.onTouchEvent (event); 
	} 
//	@Override public void onDraw (Canvas canvas) { 
//		canvas.save (); 
//		boolean horizontal = isHorizontalOrientation (); 
//		if (horizontal) 
//			canvas.translate (0, -swipeDelta); 
//		else canvas.translate (-swipeDelta, 0); 
//		super.onDraw (canvas); 
//		canvas.restore (); 
//	} 
	boolean isHorizontalOrientation () { 
		LayoutManager manager = getLayoutManager (); 
		boolean isHorizontalScrollOrientation = false; 
		if (manager instanceof LinearLayoutManager) 
			isHorizontalScrollOrientation = ((LinearLayoutManager) manager).getOrientation () 
													== LinearLayoutManager.HORIZONTAL; 
		return isHorizontalScrollOrientation; 
	} 
	public boolean canSwipe () { 
		return mParentFolder != null && mParentSubfolders != null && 
					   mParentSubfolders.length > 1; 
	} 
} 
