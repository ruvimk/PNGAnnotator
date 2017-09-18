package com.gradgoose.pngannotator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
		MIN_DISPLACEMENT_TO_SCROLL = TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.75f, 
				getResources ().getDisplayMetrics ()); 
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
	float decayRate = 0.2f; 
	long timestep = 20; 
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
	float lastJumpX = 0; 
	float lastJumpY = 0; 
	float prevX = 0; 
	float prevY = 0; 
	long prevT = 0; 
	float scrollVX = 0; 
	float scrollVY = 0; 
	// Swipe data: 
	float swipeDelta = 0; 
	boolean stillSwiping = false; 
	boolean stillAnimating = false; 
	boolean manualScroll = false; 
	float MIN_DELTA_TO_SWIPE; 
	float MIN_DISPLACEMENT_TO_SCROLL; 
	@Override public boolean onTouchEvent (MotionEvent event) { 
		if (canSwipe ()) { 
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
				Log.d (TAG, "First coordinate: " + coordinate); 
				stillSwiping = true; 
				stillAnimating = false; 
			} else if (action == MotionEvent.ACTION_MOVE) { 
				currentCoordinate = coordinate; 
				swipeDelta = firstCoordinate - currentCoordinate; 
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
				if (swipeDelta >= MIN_DELTA_TO_SWIPE) { 
					// Restore the scroll: 
					if (horizontal) 
						scrollBy ((int) (x - firstX), 0); 
					else scrollBy (0, (int) (y - firstY)); 
					// Open the next folder inside this folder's parent: 
					int nextIndex = (currentIndex + 1) % mParentSubfolders.length; 
					go (nextIndex); 
				} else if (swipeDelta <= -MIN_DELTA_TO_SWIPE) { 
					// Restore the scroll: 
					if (horizontal) 
						scrollBy ((int) (x - firstX), 0); 
					else scrollBy (0, (int) (y - firstY)); 
					// Open the previous folder inside this folder's parent: 
					int nextIndex = currentIndex - 1; 
					if (nextIndex < 0) 
						nextIndex = mParentSubfolders.length - 1; 
					go (nextIndex); 
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
	@Override public boolean onInterceptTouchEvent (MotionEvent event) {
		float x = event.getX (); 
		float y = event.getY (); 
		if (event.getAction () == MotionEvent.ACTION_DOWN) { 
			firstInterceptX = x; 
			firstInterceptY = y; 
		} 
		return (canSwipe () && 
						Math.abs (x - firstInterceptX) > Math.abs (y - firstInterceptY) && 
							 Math.sqrt ((x - firstInterceptX) * (x - firstInterceptX) + 
												(y - firstInterceptY) * (y - firstInterceptY)) 
									 >= MIN_DISPLACEMENT_TO_SCROLL) || 
					   super.onInterceptTouchEvent (event); 
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
	void go (int index) {
		Intent intent = new Intent (getContext (), NoteActivity.class); 
		String current [] = new String [mParentSubfolders[index].length]; 
		String parent [] = new String [mParentFolder.size ()]; 
		for (int i = 0; i < mParentSubfolders[index].length; i++) 
			current[i] = mParentSubfolders[index][i].getAbsolutePath (); 
		for (int i = 0; i < mParentFolder.size (); i++) 
			parent[i] = mParentFolder.elementAt (i).getAbsolutePath (); 
		intent.putExtra (NoteActivity.STATE_BROWSING_PATH, current); 
		intent.putExtra (NoteActivity.STATE_PARENT_BROWSE, parent); 
		Activity activity = (Activity) getContext (); 
		activity.startActivity (intent); 
		activity.finish (); 
	} 
	public boolean canSwipe () { 
		return mParentFolder != null && mParentSubfolders != null && 
					   mParentSubfolders.length > 1; 
	} 
} 
