package com.gradgoose.pennotepad;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/16/2017.
 */

public class SwipeableRecyclerView extends RecyclerView implements TouchInfoSetter { 
	static final String TAG = "SwipeRV"; 
	
	SettingsManager mSettingsManager = null; 
	
	float decayRate = 0.05f; 
	long timestep = 20; 
	
	final float MAX_DISPLACEMENT_FOR_CLICK; 
	final int PIXELS_PER_INCH; 
	
	final int touchSlop; 
	
	public interface SwipeCallback { 
		void swipeComplete (int direction); 
		boolean canSwipe (int direction); 
	} 
	public interface ScrollCallback { 
		void onScrollRecyclerView (int dx, int dy); 
	} 
	SwipeCallback mCallback = null; 
	ScrollCallback mScrollCallback = null; 
	void setSwipeCallback (SwipeCallback callback) { 
		mCallback = callback; 
	} 
	void setScrollCallback (ScrollCallback callback) { 
		mScrollCallback = callback; 
	} 
	
//	ScaleGestureDetector mScaleGestureDetector = null; 
	
	String mNowBrowsingName = ""; 
	Vector<File> mParentFolder = null; 
	File mParentSubfolders [] [] = null; 
	int currentIndex = 0; 
	public SwipeableRecyclerView (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		DisplayMetrics metrics = getResources ().getDisplayMetrics (); 
		MIN_DELTA_TO_SWIPE = Math.min (metrics.widthPixels / 4,
				TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.75f,
						metrics)); 
		MIN_DISPLACEMENT_TO_SCROLL = Math.min (metrics.widthPixels / 4, 
				TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.75f, 
				metrics)); 
		MAX_DISPLACEMENT_FOR_CLICK = TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 0.5f, 
				metrics); 
		PIXELS_PER_INCH = (int) TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_IN, 1f, metrics); 
		ViewConfiguration viewConf = ViewConfiguration.get (context); 
		touchSlop = viewConf.getScaledTouchSlop (); 
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
		addOnScrollListener (new OnScrollListener () { 
			@Override public void onScrollStateChanged (RecyclerView recyclerView, int newState) { 
				super.onScrollStateChanged (recyclerView, newState); 
			} 
			@Override public void onScrolled (RecyclerView recyclerView, int dx, int dy) { 
				super.onScrolled (recyclerView, dx, dy); 
				if (mScrollCallback != null) 
					mScrollCallback.onScrollRecyclerView (dx, dy); 
			} 
		}); 
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
						FileListCache.getFileLists (SelectionManager.mFilterJustFolders, 
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
		float firstDeltaCoordinate = (horizontal ? prevX - firstInterceptY : prevY - firstInterceptX); 
		float effectQuotient = firstDeltaCoordinate == 0 ? 1 : Math.abs (swipeDelta / firstDeltaCoordinate); 
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
	
	boolean mTouchMoved = false; 
	float lastTouchedX = 0; 
	float lastTouchedY = 0; 
	int lastTouchedTool = 0; 
	public void setLastTouchedPoint (float x, float y) { 
		lastTouchedX = x; 
		lastTouchedY = y; 
	} 
	public void setTouchMoved (boolean touchMoved) { 
		mTouchMoved = touchMoved; 
	} 
	public float getLastTouchedX () { 
		return lastTouchedX; 
	} 
	public float getLastTouchedY () { 
		return lastTouchedY; 
	} 
	public void setLastTouchedToolType (int type) { 
		lastTouchedTool = type; 
	} 
	public int getLastTouchedToolType () { 
		return lastTouchedTool; 
	} 
	public boolean hasTouchMoved () { 
		return mTouchMoved; 
	} 
	final View.OnTouchListener touchInfoChecker = new View.OnTouchListener () { 
		float firstX = 0; 
		float firstY = 0; 
		boolean noDisallowIntercept = false; 
		@Override public boolean onTouch (View view, MotionEvent motionEvent) { 
			float x = motionEvent.getX (); 
			float y = motionEvent.getY (); 
			setLastTouchedPoint (x, y); 
			setLastTouchedToolType (motionEvent.getToolType (0)); 
			if (motionEvent.getAction () == MotionEvent.ACTION_DOWN) { 
				firstX = x; 
				firstY = y; 
				setTouchMoved (false); 
			} 
			if (!noDisallowIntercept && Math.sqrt ((x - firstX) * (x - firstX) 
														   + (y - firstY) * (y - firstY) 
			) > touchSlop) { 
				setTouchMoved (true); 
			} 
			return false; 
		} 
	}; 
	
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
		touchInfoChecker.onTouch (this, event); 
		if (event.getAction () == MotionEvent.ACTION_UP) isScaleEvent = false; 
		if (handleTouch ()) { 
			if (isScaleEvent) { 
				// Reset swipe position, and exit. 
				swipeDelta = 0; 
				updateSwipePosition (); 
				getParent ().requestDisallowInterceptTouchEvent (false); 
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
				firstDelta = swipeDelta = (horizontal ? firstInterceptY - firstY : firstInterceptX - firstX); // Initial delta ... 
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
				if (Math.abs (swipeDelta / (horizontal ? x - firstInterceptX : y - firstInterceptY)) > 1) { // i.e., we're not scrolling vertically while swiping horizontally ... 
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
				} else if (swipeDelta == 0) {
					Log.i (TAG, "MotionEvent: swipeDelta = 0; "); 
					checkGlobalClick (x, y, event.getToolType (0)); 
				} else { 
					swipeDelta = (swipeDelta > 0 ? 1 : -1) * MIN_DELTA_TO_SWIPE * 0.9f; 
				} 
				finishScrollAnimation (); 
			} else if (action == MotionEvent.ACTION_CANCEL) { 
				swipeDelta = 0; 
				updateSwipePosition (); 
			} 
			super.onTouchEvent (event); 
			getParent ().requestDisallowInterceptTouchEvent (event.getPointerCount () == 1); 
			return true; 
		} else return super.onTouchEvent (event); 
	} 
	static final Rect checkBoundsG = new Rect (); 
	static final Rect checkBoundsL = new Rect (); 
	static boolean checkClick (int globalX, int globalY, int toolType, ViewGroup viewGroup) { 
		for (int i = 0; i < viewGroup.getChildCount (); i++) { 
			View child = viewGroup.getChildAt (i); 
			if (child instanceof ViewGroup) { 
				if (checkClick (globalX, globalY, toolType, (ViewGroup) child)) { 
					child.performClick (); 
					return true; 
				} 
			} else { 
				synchronized (checkBoundsG) { 
					child.getGlobalVisibleRect (checkBoundsG); 
					if (!checkBoundsG.contains (globalX, globalY)) 
						continue; 
					child.getLocalVisibleRect (checkBoundsL); 
					int left = checkBoundsG.left - checkBoundsL.left; 
					int top = checkBoundsG.top - checkBoundsL.top; 
					if (child instanceof TouchInfoSetter) { 
						TouchInfoSetter infoSetter = (TouchInfoSetter) child; 
						infoSetter.setLastTouchedPoint (globalX - left, globalY - top); 
						infoSetter.setLastTouchedToolType (toolType); 
					} 
					child.performClick (); 
					return true; 
				} 
			} 
		} 
		return false; 
	} 
	float firstInterceptX = 0; 
	float firstInterceptY = 0; 
	boolean isScaleEvent = false; 
	void checkGlobalClick (float x, float y, int toolType) { 
		Log.i (TAG, "checkGlobalClick (" + x + ", " + y + ")"); 
		if (hasTouchMoved ()) { 
			Log.i (TAG, "Touch has moved since ACTION_DOWN; skipping click checking ..."); 
			return; 
		} 
		Rect meG = new Rect (); 
		Rect meL = new Rect (); 
		getGlobalVisibleRect (meG); 
		int left = meG.left - meL.left; 
		int top = meG.top - meL.top; 
		if (mSettingsManager != null && mSettingsManager.isInvisiblePageNavTapEnabled ()) { 
			// The following code checks if the click was within invisible_size of the top or bottom 
			// of this view, and if it was, then this is a click on the PAGE_UP or PAGE_DOWN invisible 
			// button. In that case, we scroll in the appropriate direction, but also ask our parent 
			// ScaleDetectorContainer to forget that it registered a click just now: otherwise when 
			// the user tries to tap multiple times to scroll through multiple pages, the parent 
			// container will interpret that as double-taps and try to zoom in on the tapped area. 
			int navInvisibleBtnSize = getContext ().getResources ().getDimensionPixelSize (R.dimen.pg_up_down_invisible_size); 
			ViewParent parent = getParent (); 
			ScaleDetectorContainer container = parent instanceof ScaleDetectorContainer ? (ScaleDetectorContainer) parent : null; 
			if (y < navInvisibleBtnSize) { 
				pageUp (); 
				if (container != null) 
					container.forgetLastClickTime (); 
				return; 
			} 
			if (y >= getHeight () - navInvisibleBtnSize) { 
				pageDown (); 
				if (container != null) 
					container.forgetLastClickTime (); 
				return; 
			} 
		} 
		checkClick ((int) x + left, (int) y + top, toolType, this); 
	} 
	@Override public boolean onInterceptTouchEvent (MotionEvent event) { 
		if (!allowTouch) return false; 
		touchInfoChecker.onTouch (this, event); 
		float x = event.getX (); 
		float y = event.getY (); 
		if (event.getAction () == MotionEvent.ACTION_DOWN) { 
			firstInterceptX = x; 
			firstInterceptY = y; 
			swipeDelta = 0; 
			firstDelta = 0; 
		} else if (event.getAction () == MotionEvent.ACTION_UP) { 
			if (swipeDelta == 0) { 
//				ViewParent parent = getParent (); 
//				if (parent instanceof ScaleDetectorContainer) { 
//					((ScaleDetectorContainer) parent).checkClick (); 
//				} 
				checkGlobalClick (x, y, event.getToolType (0)); 
			} 
		} 
		boolean horizontal = isHorizontalOrientation (); 
		float delta = horizontal ? firstInterceptY - y : firstInterceptX - x; 
//		if (!isScaleEvent) mScaleGestureDetector.onTouchEvent (event); // Just let the detector know about this touch ... 
//		else getParent ().requestDisallowInterceptTouchEvent (true); 
		// The scale detector will set isScaleEvent, we will set it, if we detect a scale. 
		return (/*isScaleEvent || */(event.getAction () != MotionEvent.ACTION_DOWN && 
											 canSwipe (delta) //&& 
											 /*Math.abs (delta) > 0.25f * (horizontal ? x - firstInterceptX : y - firstInterceptY)*/ /*&& 
											 (Math.abs (delta) >= MIN_DELTA_TO_SWIPE || 
											 Math.sqrt ((x - firstInterceptX) * (x - firstInterceptX) + 
												(y - firstInterceptY) * (y - firstInterceptY)) 
									 >= MIN_DISPLACEMENT_TO_SCROLL) */
											&& event.getPointerCount () == 1 
		) 
											|| 
					   super.onInterceptTouchEvent (event)); 
	} 
	public void pageUp () { 
		LayoutManager lm = getLayoutManager (); 
		if (lm.getChildCount () == 0) 
			return; 
		int skipCount = getPageSkipCount (); 
		if (skipCount > 1) { 
			int nextItem = getPageUpScrollPosition (); 
			if (nextItem >= 0) { 
				scrollToPosition (nextItem); 
				return; 
			} 
		} 
		View child = lm.getChildCount () > 1 ? 
							 lm.getChildAt (1) 
							 : lm.getChildAt (0); 
		pageScroll (-skipCount * child.getWidth (), -skipCount * child.getHeight ()); 
	} 
	public void pageDown () { 
		LayoutManager lm = getLayoutManager (); 
		if (lm.getChildCount () == 0) 
			return; 
		int skipCount = getPageSkipCount (); 
		if (skipCount > 1) { 
			int nextItem = getPageDownScrollPosition (); 
			if (nextItem >= 0 && lm instanceof LinearLayoutManager) { 
				((LinearLayoutManager) lm).scrollToPositionWithOffset (nextItem, 0); 
				return; 
			} 
		} 
		View child = lm.getChildCount () > 1 ? 
							 lm.getChildAt (1) 
							 : lm.getChildAt (0); 
		pageScroll (+skipCount * child.getWidth (), +skipCount * child.getHeight ()); 
	} 
	private int getPageSkipCount () { 
		LayoutManager lm = getLayoutManager (); 
		int skipCount = 1; 
		if (lm instanceof LinearLayoutManager) { 
			// This logic makes it easier to skip pages if multiple pages are completely visible ... 
			// (e.g., think: grid-view, where there's like 2 rows of completely visible pages, 
			// and hitting 'page down' would otherwise awkwardly just scroll one row down, 
			// rather than scrolling one page down). 
			LinearLayoutManager llm = (LinearLayoutManager) lm; 
			int spanCount = 1; 
			if (llm instanceof GridLayoutManager) { 
				spanCount = ((GridLayoutManager) llm).getSpanCount (); 
			} 
			int count = 1 + (llm.findLastCompletelyVisibleItemPosition () - 
								llm.findFirstCompletelyVisibleItemPosition ()) 
					/ spanCount; 
			if (count > 1) 
				skipCount = count; 
		} 
		return skipCount; 
	} 
	private int getPageDownScrollPosition () { 
		LayoutManager lm = getLayoutManager (); 
		if (lm instanceof LinearLayoutManager) { 
			// This logic makes it easier to skip pages if multiple pages are completely visible ... 
			// (e.g., think: grid-view, where there's like 2 rows of completely visible pages, 
			// and hitting 'page down' would otherwise awkwardly just scroll one row down, 
			// rather than scrolling one page down). 
			LinearLayoutManager llm = (LinearLayoutManager) lm; 
			int spanCount = 1; 
			if (llm instanceof GridLayoutManager) { 
				spanCount = ((GridLayoutManager) llm).getSpanCount (); 
			} 
			int lastPosition = llm.findLastCompletelyVisibleItemPosition (); 
			return lastPosition - (lastPosition % spanCount) + spanCount; 
		} 
		return -1; 
	}
	private int getPageUpScrollPosition () { 
		LayoutManager lm = getLayoutManager (); 
		if (lm instanceof LinearLayoutManager) { 
			// This logic makes it easier to skip pages if multiple pages are completely visible ... 
			// (e.g., think: grid-view, where there's like 2 rows of completely visible pages, 
			// and hitting 'page down' would otherwise awkwardly just scroll one row down, 
			// rather than scrolling one page down). 
			LinearLayoutManager llm = (LinearLayoutManager) lm; 
			int spanCount = 1; 
			if (llm instanceof GridLayoutManager) { 
				spanCount = ((GridLayoutManager) llm).getSpanCount (); 
			} 
			int lastPosition = llm.findLastCompletelyVisibleItemPosition (); 
			int nowPosition = llm.findFirstCompletelyVisibleItemPosition (); 
			int difference = lastPosition - nowPosition; 
			return nowPosition - difference + difference % spanCount; 
		} 
		return -1; 
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
	@Override public void invalidate () { 
		super.invalidate (); 
		Adapter adapter = getAdapter (); 
		if (adapter != null && adapter instanceof PngNotesAdapter) { 
			((PngNotesAdapter) adapter).refreshViews (); 
		} 
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
