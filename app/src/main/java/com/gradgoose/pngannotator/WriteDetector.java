package com.gradgoose.pngannotator; 

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.MotionEvent;

import java.util.Vector;

/**
 * Created by SpongeBob on 31.12.2015.
 */
public class WriteDetector { 
	private final Context mContext; 
	private OnWriteGestureListener mListener; 
	
	private boolean mCurrentWriteStrokeCancelled = false; 
	
	private Mode mMode = new Mode (); 
	
	public interface OnWriteGestureListener { 
		boolean onStrokeBegin (int strokeID, float x, float y); 
		boolean onStrokeWrite (int strokeID, float x0, float y0, float x1, float y1); 
		void onStrokeEnd (int strokeID, float x, float y); 
		void onStrokeCancel (int strokeID); 
		boolean onEraseBegin (int strokeID, float x, float y); 
		boolean onEraseMove (int strokeID, float x0, float y0, float x1, float y1); 
		void onEraseEnd (int strokeID, float x, float y); 
		void onEraseCancel (int strokeID); 
		boolean onStrokeHold (int strokeID); 
		boolean onBeginPan (int strokeID, float x0, float y0); 
		boolean onSimplePan (int strokeID, float xInitial, float yInitial, float xt, float yt, 
							 float elapsedSeconds); 
		boolean onPanHint (int strokeID, float xInitial, float yInitial, float xt, float yt, 
						   float elapsedSeconds); 
		void onPanCancel (int strokeID, float x0, float y0, float xNow, float yNow, 
						  float elapsedSeconds); 
		void onPanDone (); 
		boolean onPinchBegin (int strokeId1, int strokeId2, float x1, float y1, float x2, float y2); 
		boolean onPinchTransform (int strokeId1, int strokeId2, 
								  float scale, 
								  float rotate, 
								  float translateX, 
								  float translateY, 
								  float pinchPositionX, 
								  float pinchPositionY, 
								  float elapsedSeconds); 
		void onPinchCancel (); 
		void onPinchDone (); 
	} 
	public static class Mode { 
		private boolean inPenMode = false; 
		private boolean writeEnabled = true; 
		private boolean eraseEnabled = true; 
		private boolean pinchEnabled = false; 
		private boolean panEnabled = true; 
		private boolean palmRejection = true; 
		private boolean penModePan = true; 
		private boolean secondaryButtonIsEraser = true; 
		private long msSecondaryButtonDeadline = 250; 
		private long dppsPalmJerkSpeed = (long) 10e3; // dp per second 
		private long dpMinPenModePanDistance = 40; 
		private long maxRejectPointCount = 3; 
		private float maxRejectDeltaT = 0.05f; 
		private int maxHandleTouchCount = 10; 
		public Mode setPenMode (boolean mode) { inPenMode = mode; return this; } 
		public Mode enableWrite (boolean enable) { writeEnabled = enable; return this; } 
		public Mode enableErase (boolean enable) { eraseEnabled = enable; return this; } 
		public Mode enablePinch (boolean enable) { pinchEnabled = enable; return this; } 
		public Mode enablePan (boolean enable) { panEnabled = enable; return this; } 
		public Mode enablePalmRejection (boolean enable) { palmRejection = enable; return this; } 
		public Mode enablePenModePan (boolean enable) { penModePan = enable; return this; } 
		public Mode setSecondaryButtonAsEraser (boolean whetherEraser) { secondaryButtonIsEraser = whetherEraser; return this; } 
		public Mode setSecondaryButtonDeadline (long ms) { msSecondaryButtonDeadline = ms; return this; } 
		public Mode setPalmJerkSpeed (long speed) { dppsPalmJerkSpeed = speed; return this; } 
		public Mode setMinimumPenModePanDistance (long distance) { dpMinPenModePanDistance = distance; return this; } 
		public Mode setMaximumRejectPointCount (long count) { maxRejectPointCount = count; return this; } 
		public Mode setMaximumRejectDeltaT (float fraction) { maxRejectDeltaT = fraction; return this; } 
		public Mode setMaximumHandleTouchCount (int count) { maxHandleTouchCount = count; return this; } 
		public boolean isInPenMode () { return inPenMode; } 
		public boolean isWriteEnabled () { return writeEnabled; } 
		public boolean isEraseEnabled () { return eraseEnabled; } 
		public boolean isPinchEnabled () { return pinchEnabled; } 
		public boolean isPanEnabled () { return panEnabled; } 
		public boolean isPalmRejectionEnabled () { return palmRejection; } 
		public boolean isPenModePanEnabled () { return penModePan; } 
		public boolean isSecondaryButtonEraser () { return secondaryButtonIsEraser; } 
		public long getSecondaryButtonDeadline () { return msSecondaryButtonDeadline; } 
		public long getPalmJerkSpeed () { return dppsPalmJerkSpeed; } 
		public long getMinimumPenModePanDistance () { return dpMinPenModePanDistance; } 
		public long getMaximumRejectPointCount () { return maxRejectPointCount; } 
		public float getMaximumRejectDeltaT () { return maxRejectDeltaT; } 
		public int getMaximumHandleTouchCount () { return maxHandleTouchCount; } 
		// Serialization: 
		private static final String PREFIX = "writedetector.mode."; 
		private static final String STATE_IN_PEN_MODE     = PREFIX + "inpenmode"; 
		private static final String STATE_WRITE_ENABLED   = PREFIX + "writeenabled"; 
		private static final String STATE_ZOOM_ENABLED    = PREFIX + "zoomenabled"; 
		private static final String STATE_PAN_ENABLED     = PREFIX + "panenabled"; 
		private static final String STATE_TWO_PAN_ENABLED = PREFIX + "twofingerpanenabled"; 
		private static final String STATE_PALM_REJECTION  = PREFIX + "palmrejection"; 
		private static final String STATE_PEN_MODE_PAN    = PREFIX + "penmodepan"; 
		private static final String STATE_SECONDARY_ERASE = PREFIX + "secondaryerase"; 
		private static final String STATE_SECONDARY_DDLN  = PREFIX + "secondarydeadline"; 
		private static final String STATE_PALM_JERK_SPEED = PREFIX + "palmjerkspeed"; 
		private static final String STATE_MIN_PAN_DIST    = PREFIX + "minpenpandist"; 
		private static final String STATE_MAX_REJECT_PC   = PREFIX + "maxrejectptct"; 
		private static final String STATE_MAX_REJECT_DT   = PREFIX + "minrejectlenf"; 
		public Bundle saveState () { 
			Bundle outState = new Bundle (); 
			outState.putBoolean (STATE_IN_PEN_MODE, inPenMode); 
			outState.putBoolean (STATE_WRITE_ENABLED, writeEnabled); 
			outState.putBoolean (STATE_ZOOM_ENABLED, pinchEnabled); 
			outState.putBoolean (STATE_PAN_ENABLED, panEnabled); 
			outState.putBoolean (STATE_PALM_REJECTION, palmRejection); 
			outState.putBoolean (STATE_PEN_MODE_PAN, penModePan); 
			outState.putBoolean (STATE_SECONDARY_ERASE, secondaryButtonIsEraser); 
			outState.putLong (STATE_SECONDARY_DDLN, msSecondaryButtonDeadline); 
			outState.putLong (STATE_PALM_JERK_SPEED, dppsPalmJerkSpeed); 
			outState.putLong (STATE_MIN_PAN_DIST, dpMinPenModePanDistance); 
			outState.putLong (STATE_MAX_REJECT_PC, maxRejectPointCount); 
			outState.putFloat (STATE_MAX_REJECT_DT, maxRejectDeltaT); 
			return outState; 
		} 
		public void loadState (Bundle inState) { 
			if (inState == null) return; 
			inPenMode = inState.getBoolean (STATE_IN_PEN_MODE); 
			writeEnabled = inState.getBoolean (STATE_WRITE_ENABLED); 
			pinchEnabled = inState.getBoolean (STATE_ZOOM_ENABLED); 
			panEnabled = inState.getBoolean (STATE_PAN_ENABLED); 
			palmRejection = inState.getBoolean (STATE_PALM_REJECTION); 
			penModePan = inState.getBoolean (STATE_PEN_MODE_PAN); 
			secondaryButtonIsEraser = inState.getBoolean (STATE_SECONDARY_ERASE); 
			msSecondaryButtonDeadline = inState.getLong (STATE_SECONDARY_DDLN); 
			dppsPalmJerkSpeed = inState.getLong (STATE_PALM_JERK_SPEED); 
			dpMinPenModePanDistance = inState.getLong (STATE_MIN_PAN_DIST); 
			maxRejectPointCount = inState.getLong (STATE_MAX_REJECT_PC); 
			maxRejectDeltaT = inState.getFloat (STATE_MAX_REJECT_DT); 
		} 
	} 
	public Mode getMode () { return mMode; } 
	
	public void setPenMode (boolean penMode) { 
		mMode.setPenMode (penMode); 
	} 
	public boolean isInPenMode () { 
		return mMode.isInPenMode (); 
	} 
	
	public WriteDetector (Context context, OnWriteGestureListener listener) { 
		mContext = context; 
		mListener = listener; 
		Stroke.pxOverDP = TypedValue.applyDimension (TypedValue.COMPLEX_UNIT_DIP, 
				1f, 
				mContext.getResources ().getDisplayMetrics ()); 
		// Warning:  Above will only work if all contexts 
		// in the app are on the same screen density. 
	} 
	
	static int touchDownCount = 0; 
	boolean mFingerCanceled = false; 
	boolean mPinchCanceled = false; 
	boolean mPinchInProgress = false; 
	private boolean isSpecialToolType (int toolType) {
		return toolType == MotionEvent.TOOL_TYPE_ERASER || 
				toolType == MotionEvent.TOOL_TYPE_STYLUS; 
	} 
	
	// Third-party constants: 
	private static final int SPEN_ACTION_DOWN = 211; 
	private static final int SPEN_ACTION_UP = 212; 
	private static final int SPEN_ACTION_MOVE = 213; 
	private static final int SPEN_ACTION_CANCEL = 214; 
	// (S-Pen constants, as in: 
	// https://src.chromium.org/svn/trunk/src/content/public/android/java/src/org/chromium/content/browser/SPenSupport.java
	// ) 
	
	long wentDownAt = 0; 
	public boolean onTouchEvent (MotionEvent event) { 
		if (mCurrentWriteStrokeCancelled) { 
			if (event.getPointerCount () < 1) 
				mCurrentWriteStrokeCancelled = false; 
			return false; 
		} 
		Stroke stroke; 
		int pCount = event.getPointerCount (); 
		int aIndex = event.getActionIndex (); 
		int specialToolCount = 0; 
		boolean specialToolType; 
		for (int i = 0; i < event.getPointerCount (); i++) { 
			specialToolType = isSpecialToolType (event.getToolType (i)); 
			if (specialToolType) specialToolCount++; 
		} 
		if (pCount > mMode.maxHandleTouchCount) { 
			if (mListener != null) 
				for (Stroke s : mStrokeList) { 
					s.cancel (mListener); 
				} 
			return false; 
		} 
		switch (event.getActionMasked ()) { 
			case MotionEvent.ACTION_CANCEL: 
			case SPEN_ACTION_CANCEL: 
			case MotionEvent.ACTION_UP: 
			case SPEN_ACTION_UP: 
				if (mPinchInProgress && !mFingerCanceled && !mPinchCanceled && mListener != null) 
					mListener.onPinchDone (); 
				mPinchCanceled = false; 
				mFingerCanceled = false; 
				mPinchInProgress = false; 
			case MotionEvent.ACTION_POINTER_UP: 
				stroke = findStroke (event.getPointerId (aIndex), pCount); 
				if (stroke == null) 
					break; 
				stroke.onUp (event.getX (aIndex), 
						event.getY (aIndex), 
						mListener); 
				if (mListener != null) 
					stroke.dispatchUp (mListener); 
				break; 
			case MotionEvent.ACTION_DOWN: 
			case SPEN_ACTION_DOWN: 
				wentDownAt = System.currentTimeMillis (); 
				touchDownCount++; 
			case MotionEvent.ACTION_POINTER_DOWN: 
				if (mMode.isPinchEnabled () && event.getPointerCount () == 2) { 
					for (int i = mStrokeList.size () - 1; i > 0; i--) { 
						// This 'for' loop is to find and truncate the last active stroke; 
						// this is necessary because otherwise there would be a weird 
						// transformation thing when drawing, then cancelling by touching 
						// down with an additional finger. 
						Stroke s = mStrokeList.get (i); 
						if (!s.isActive ()) 
							continue; 
						s.truncate (); 
						i = 0; // Exit loop; better efficiency, but only works for two-finger pinch; 
					} 
				} 
				mStrokeList.add (stroke = new Stroke (mContext, mMode).onDown (event.getX (aIndex), 
						event.getY (aIndex), 
						event.getToolType (aIndex), 
						event.getPointerId (aIndex),
						Build.VERSION.SDK_INT >= 21 && event.isButtonPressed (MotionEvent.BUTTON_PRIMARY),
						Build.VERSION.SDK_INT >= 21 && event.isButtonPressed (MotionEvent.BUTTON_SECONDARY)) 
				.setHoldListener (mListener)); 
				if (stroke.getType () == Stroke.TYPE_WRITE) { 
					if (mMode.isPanEnabled ()) 
						stroke.setType (Stroke.TYPE_PAN); 
					else if (mMode.isPenModePanEnabled () && 
								 mMode.isInPenMode ()) { 
						stroke.setType (Stroke.TYPE_PAN); 
						stroke.mHintOnly = true; 
					} 
				} 
				stroke.dispatchDown (mListener); 
				stroke.transaction = touchDownCount; 
				break; 
			case MotionEvent.ACTION_MOVE: 
			case SPEN_ACTION_MOVE: 
				Stroke firstStroke = null; 
				Stroke secondStroke = null; 
				for (int i = 0; i < pCount; i++) { 
					stroke = findStroke (event.getPointerId (i), pCount); 
					if (stroke == null) 
						continue; 
					specialToolType = isSpecialToolType (event.getToolType (i)); 
					if (specialToolType) { 
						// Cancel finger events on special tool type; 
						if (!mFingerCanceled && mListener != null) 
							mListener.onPinchCancel (); 
						mFingerCanceled = true; 
					} 
					if (mMode.isPinchEnabled () && !mFingerCanceled) { 
						if (pCount - specialToolCount == 2) { 
							if (firstStroke != null) {
								firstStroke.partner = stroke; 
								stroke.partner = firstStroke; 
							} 
							if (stroke.getType () == Stroke.TYPE_WRITE) { 
								if (mListener != null) 
									mListener.onStrokeCancel (stroke.strokeID); 
								stroke.setType (Stroke.TYPE_PAN); 
							} 
						} else if (pCount - specialToolCount > 2) { 
							mFingerCanceled = true; 
							mListener.onPinchCancel (); 
							continue; 
						} 
					} 
					if (firstStroke == null) 
						firstStroke = stroke; 
					else if (secondStroke == null) 
						secondStroke = stroke; 
					stroke.onMove (event.getX (i), event.getY (i), 
							Build.VERSION.SDK_INT >= 21 && event.isButtonPressed (MotionEvent.BUTTON_PRIMARY), 
							Build.VERSION.SDK_INT >= 21 && event.isButtonPressed (MotionEvent.BUTTON_SECONDARY), 
							mListener); 
					if (!mPinchInProgress || specialToolType) 
						stroke.dispatchLastMove (mListener); 
				} 
				if (!mPinchCanceled && !mFingerCanceled && mMode.isPinchEnabled () && 
							pCount - specialToolCount == 2 && 
							mListener != null && firstStroke != null && secondStroke != null) { 
					if (!mPinchInProgress) 
						mPinchCanceled |= ! 
						mListener.onPinchBegin (firstStroke.strokeID, secondStroke.strokeID, 
								firstStroke.getFirstPoint ().x (), firstStroke.getFirstPoint ().y (), 
								secondStroke.getFirstPoint ().x (), secondStroke.getFirstPoint ().y ()); 
					long now = System.currentTimeMillis (); 
					float elapsed = (float)(now - wentDownAt) / 1e3f; 
					float dx0 = firstStroke.getFirstPoint ().x () - secondStroke.getFirstPoint ().x (); 
					float dy0 = firstStroke.getFirstPoint ().y () - secondStroke.getFirstPoint ().y (); 
					float dx1 = firstStroke.getLastPoint ().x () - secondStroke.getLastPoint ().x (); 
					float dy1 = firstStroke.getLastPoint ().y () - secondStroke.getLastPoint ().y (); 
					float dist0 = (float) Math.sqrt (dx0 * dx0 + dy0 * dy0); 
					float dist1 = (float) Math.sqrt (dx1 * dx1 + dy1 * dy1); 
					float scale = dist1 / dist0; 
					double angle0 = Math.atan2 (dy0, dx0); 
					double angle1 = Math.atan2 (dy1, dx1); 
					float rotate = (float) evalAngleDifference (angle1, angle0); 
					float dxFirst = firstStroke.getLastPoint ().x () - firstStroke.getFirstPoint ().x (); 
					float dyFirst = firstStroke.getLastPoint ().y () - firstStroke.getFirstPoint ().y (); 
					float dxSecond = secondStroke.getLastPoint ().x () - secondStroke.getFirstPoint ().x (); 
					float dySecond = secondStroke.getLastPoint ().y () - secondStroke.getFirstPoint ().y (); 
					float dxHalf = (dxFirst + dxSecond) / 2; 
					float dyHalf = (dyFirst + dySecond) / 2; 
					float xMiddle0 = (firstStroke.getFirstPoint ().x () + secondStroke.getFirstPoint ().x ()) / 2; 
					float yMiddle0 = (firstStroke.getFirstPoint ().y () + secondStroke.getFirstPoint ().y ()) / 2; 
					mPinchCanceled |= !mListener.onPinchTransform (firstStroke.strokeID, 
							secondStroke.strokeID, 
							scale, rotate, 
							dxHalf, dyHalf, 
							xMiddle0, yMiddle0, 
							elapsed); 
					for (int i = 0; i < pCount; i++) { 
						// Cancel, but the individual strokes only, not the finger: 
						stroke = findStroke (event.getPointerId (i), pCount); 
						if (!stroke.mPinchOnly) 
							stroke.makePinchOnly (mListener); 
					} 
					mPinchInProgress = true; 
				} 
				break; 
			default: 
				return false; 
		} 
		return true; 
	} 
	Stroke findStroke (int pointerId, int activeCount) { 
		int activeCountedSoFar = 0; 
		for (int i = mStrokeList.size () - 1; i >= 0 && activeCountedSoFar < activeCount; i--) { 
			Stroke stroke = mStrokeList.get (i); 
			if (!stroke.isActive ()) 
				continue; 
			activeCountedSoFar++; 
			if (stroke.pointerID == pointerId) 
				return stroke; 
		} 
		return null; 
	} 
	Stroke getStroke (int strokeID) { 
		if (mStrokeList.isEmpty ()) 
			return null; 
		int index = strokeID - mStrokeList.get (0).getID (); 
		if (index < mStrokeList.size () && mStrokeList.get (index).getID () == strokeID) 
			return mStrokeList.get (index); 
		for (int i = mStrokeList.size (); i > 0; i--) 
			if (mStrokeList.get (i - 1).getID () == strokeID) 
				return mStrokeList.get (i - 1); 
		return null; 
	} 
	Vector<Stroke> getStrokeList () { return mStrokeList; } 
	
	private Vector<Stroke> mStrokeList = new Vector<> (); 
	
	public static class Stroke { 
		public static float pxOverDP = 1.0f; 
		// Types: 
		public static final int TYPE_WRITE = 1; 
		public static final int TYPE_PAN = 2; 
		public static final int TYPE_STYLUS_WRITE = 3; 
		public static final int TYPE_ERASE = 4; 
		// Main Properties: 
		private int strokeID = 0; 
		private int pointerID = 0; 
		private int mToolType = 0; 
		private Vector<Point> points = new Vector<> (); 
		// Probabilities and Context: 
		private final Context mContext; 
		private final Mode mode; 
		private boolean mActive = false; // Whether stroke is still being drawn. 
		private boolean mCanceled = false; 
		private boolean mComplete = false; 
		private boolean mHintOnly = false; 
		private boolean mPinchOnly = false; 
		private int mType = TYPE_WRITE; 
		Stroke partner = null; 
		int transaction = 0; 
		// Handler: 
		private static Handler hStrokeMisc = new Handler ();
		private OnWriteGestureListener onHoldListener = null;
		private long lastHoldSent = 0; 
		private Runnable rCheckStrokeHold = new Runnable () { 
			@Override
			public void run () { 
				if (mCanceled || mComplete) 
					return; 
				float minX, maxX, minY, maxY; 
				minX = maxX = points.lastElement ().x (); 
				minY = maxY = points.lastElement ().y (); 
				float maxTime = mContext.getResources ().getFraction (R.fraction.writedetector_hold_mintime, 
						1, 1); 
				float maxPx = mContext.getResources ().getDimensionPixelOffset (R.dimen.writedetector_hold_threshold); 
				long now = System.currentTimeMillis (); 
				float dLast = (float) (now - lastHoldSent) * 1e-3f; 
				if (dLast >= maxTime) 
					for (int i = points.size () - 2; i > 0; i--) {
						float dT = (float) (now - points.get (i).T ()) * 1e-3f; 
						if (dT > maxTime) { 
							if (maxX - minX > maxPx || maxY - minY > maxPx) 
								break; 
							// Hold Gesture Detected!: 
							if (onHoldListener != null) 
								mComplete = onHoldListener.onStrokeHold (strokeID); 
							lastHoldSent = now; 
							break; 
						} 
						float x = points.get (i).x (); 
						float y = points.get (i).y (); 
						if (x > maxX) 
							maxX = x; 
						else if (x < minX) 
							minX = x; 
						if (y > maxY) 
							maxY = y; 
						else if (y < minY) 
							minY = y; 
					} 
				if (isActive ()) 
					hStrokeMisc.postDelayed (this, 100); 
			} 
		}; 
		// Classes: 
		class Point { 
			long timestamp = System.currentTimeMillis (); 
			private float mX = 0; 
			private float mY = 0; 
			Point set (float x, float y) { 
				mX = x; 
				mY = y; 
				return this; 
			} 
			public long T () { return timestamp; } 
			public float x () { return mX; } 
			public float y () { return mY; } 
		} 
		// Construct: 
		public Stroke (Context ctx, Mode argMode) { mContext = ctx; mode = argMode; } 
		// Methods: 
		void translate (float dx, float dy) { 
			for (Point point : points) { 
				point.mX += dx; 
				point.mY += dy; 
			} 
		} 
		void scale (float sx, float sy) { 
			float rX = getTopLeftX (0, count ()); 
			float rY = getTopLeftY (0, count ()); 
			for (Point point : points) { 
				point.mX = (point.mX - rX) * sx + rX; 
				point.mY = (point.mY - rY) * sy + rY; 
			} 
		} 
		int getID () { return strokeID; } 
		int getType () { return mType; } 
		void setType (int type) { mType = type; } 
		boolean isActive () { return mActive; } 
		void truncate () { 
			Point last = getLastPoint (); 
			points.clear (); 
			points.add (last); 
		} 
		Stroke onDown (float x, float y, int toolType, int pointerId, 
					   boolean primaryButtonDown, boolean secondaryButtonDown) { 
			strokeID = (++strokeCount); 
			pointerID = pointerId; 
			mToolType = toolType; 
			points.add (new Point ().set (x, y)); 
			mActive = true; 
			hStrokeMisc.postDelayed (rCheckStrokeHold, 100); 
			if (toolType == MotionEvent.TOOL_TYPE_ERASER ||
						(mode.secondaryButtonIsEraser && 
								 toolType == MotionEvent.TOOL_TYPE_STYLUS && 
								 secondaryButtonDown)) 
				mType = TYPE_ERASE; 
			else if (toolType == MotionEvent.TOOL_TYPE_STYLUS) 
				mType = TYPE_STYLUS_WRITE; 
			return this; 
		} 
		void onMove (float x, float y, 
					 boolean primaryButtonDown, 
					 boolean secondaryButtonDown, 
					 OnWriteGestureListener listener) { 
			if (mCanceled || mComplete) 
				return; 
			if (mode.secondaryButtonIsEraser && 
						System.currentTimeMillis () - points.firstElement ().T () <= 
								mode.msSecondaryButtonDeadline) { 
				if (mType != TYPE_ERASE && secondaryButtonDown) { 
					mType = TYPE_ERASE; 
					cancelWriteInitiateErase (listener); 
				} 
			} 
			Point prev = points.lastElement (); 
			if (prev.x () == x && prev.y () == y) 
				return; 
			points.add (new Point ().set (x, y)); 
			checkPalm (x, y, listener); 
			checkScroll (); 
		} 
		void onUp (float x, float y, OnWriteGestureListener listener) { 
			mActive = false; 
			if (mCanceled || mComplete) 
				return; 
			Point prev = points.lastElement (); 
			if (prev.x () == x && prev.y () == y) 
				return; 
			points.add (new Point ().set (x, y)); 
			checkPalm (x, y, listener); 
			checkScroll (); 
		} 
		void checkPalm (float x, float y, OnWriteGestureListener listener) { 
			if (mType == TYPE_STYLUS_WRITE || mType == TYPE_ERASE) 
				return; // Do trust the actual tools with this. 
			if (points.size () < 2) return; 
			Point prev = points.get (points.size () - 2); 
			if (!mode.palmRejection) 
				return; 
//			float dpTotal = pxLength (); 
			float dpLen = pxLength (-2) / pxOverDP; 
//			float lenF = dpLen / dpTotal; 
//			float ptF = 2 / (count () - 1); 
//			if (ptF >= mode.maxRejectDeltaT) 
//				return; 
			if (count () > mode.maxRejectPointCount) 
				return; 
			float deltaT = (points.lastElement ().T () - 
									prev.T ()) / 1e3f; 
			float dppsSpeed = dpLen / deltaT; 
			if (dppsSpeed >= (float) mode.dppsPalmJerkSpeed && 
					deltaT <= mode.maxRejectDeltaT) 
				cancel (listener); 
		} 
		void checkScroll () { 
			if (mType != TYPE_PAN) return; 
			if (!mHintOnly) return; 
			float dpLen = pxLength () / pxOverDP; 
			if (dpLen < mode.getMinimumPenModePanDistance ()) 
				return; // Do not convert to a PAN yet if not enough info. 
			mHintOnly = false; 
		} 
		private void cancelWriteInitiateErase (OnWriteGestureListener listener) { 
			if (listener == null) 
				return; 
			// Cancel Whatever It Was: 
			dispatchCancelWrite (listener); 
			// Change Type to Erase: 
			mType = TYPE_ERASE; 
			// Redo All Points: 
			dispatchDown (listener); 
			for (int i = 1; i < points.size (); i++) 
				dispatchMove (listener, i); 
		} 
		Stroke dispatchDown (OnWriteGestureListener listener) { 
			if (listener == null) 
				return this; 
			if (mType == TYPE_PAN && (mode.isPanEnabled () ||
											  (mode.isPenModePanEnabled () &&
													   mode.isInPenMode ()))) 
				mCanceled = !listener.onBeginPan (strokeID, points.get (0).x (), points.get (0).y ());
			if (isWrite () && mode.isWriteEnabled ()) 
				mCanceled = !listener.onStrokeBegin (strokeID, points.get (0).x (), points.get (0).y ()); 
			if (mType == TYPE_ERASE && mode.isEraseEnabled ()) 
				mCanceled = !listener.onEraseBegin (strokeID, points.get (0).x (), points.get (0).y ()); 
			return this; 
		} 
		void dispatchLastMove (OnWriteGestureListener listener) { 
			dispatchMove (listener, points.size () - 1); 
		} 
		void dispatchMove (OnWriteGestureListener listener, int pointIndex) { 
			if (mCanceled || mComplete || mPinchOnly) 
				return; 
			float x1 = points.elementAt (pointIndex).x (); 
			float y1 = points.elementAt (pointIndex).y (); 
			float x0, y0; 
			int i = points.size () - 2; 
			do { 
				if (i < 0) 
					return; 
				x0 = points.get (i).x (); 
				y0 = points.get (i).y (); 
				i--; 
			} while (x0 == x1 && y0 == y1); 
			if (listener != null) { 
				if (isWrite () && mode.isWriteEnabled ()) 
					mCanceled |= !listener.onStrokeWrite (strokeID, x0, y0, x1, y1); 
				if (mType == TYPE_PAN) {
					if (mHintOnly)
						mCanceled |= !listener.onPanHint (strokeID, 
								points.firstElement ().x (), 
								points.firstElement ().y (), 
								x1, y1, (float) (points.elementAt (pointIndex).T () - points.firstElement ().T ()) / 1e3f); 
					else 
					if (mode.isPanEnabled () || 
								(mode.isPenModePanEnabled () && 
								mode.isInPenMode ())) 
						mCanceled |= !listener.onSimplePan (strokeID, 
								points.firstElement ().x (), 
								points.firstElement ().y (), 
								x1, y1, (float) (points.elementAt (pointIndex).T () - points.firstElement ().T ()) / 1e3f); 
				} 
				if (mType == TYPE_ERASE && mode.isEraseEnabled ()) 
					mCanceled |= !listener.onEraseMove (strokeID, 
							x0, y0, 
							x1, y1); 
			} 
		} 
		void dispatchUp (OnWriteGestureListener listener) { 
			if (mCanceled || mComplete || mPinchOnly) 
				return; 
//			if (!isWrite () || !mode.isWriteEnabled ()) 
//				return; 
			if (listener != null) { 
				if (mType == TYPE_PAN) 
					listener.onPanDone (); 
				else if (isWrite () && mode.isWriteEnabled ()) 
					listener.onStrokeEnd (strokeID, 
							points.lastElement ().x (), 
							points.lastElement ().y ()); 
				else if (mType == TYPE_ERASE && mode.isEraseEnabled ()) 
					listener.onEraseEnd (strokeID, 
							points.lastElement ().x (), 
							points.lastElement ().y ()); 
			} 
		} 
		void dispatchCancel (OnWriteGestureListener listener) { 
			if (listener == null) 
				return; 
			if (mType == TYPE_PAN) 
				listener.onPanCancel (strokeID, points.firstElement ().x (), 
						points.firstElement ().y (), 
						points.lastElement ().x (), 
						points.lastElement ().y (), 
						(float) (points.lastElement ().T () - points.firstElement ().T ()) / 1e3f); 
			else if ((mType == TYPE_WRITE || mType == TYPE_STYLUS_WRITE) && 
							 mode.isWriteEnabled ()) 
				listener.onStrokeCancel (strokeID); 
			else if (mType == TYPE_ERASE && mode.isEraseEnabled ()) 
				listener.onEraseCancel (strokeID); 
		} 
		void dispatchCancelWrite (OnWriteGestureListener listener) { 
			listener.onStrokeCancel (strokeID); 
		} 
		Stroke setHoldListener (OnWriteGestureListener listener) { 
			onHoldListener = listener; 
			if (!isActive ()) 
				hStrokeMisc.postDelayed (rCheckStrokeHold, 25); 
			return this; 
		} 
		public void cancel (OnWriteGestureListener listener) { 
			dispatchCancel (listener); 
			mCanceled = true; 
		} 
		public void makePinchOnly (OnWriteGestureListener listener) { 
			dispatchCancel (listener); 
			mPinchOnly = true; 
		} 
		public Point getFirstPoint () { return points.firstElement (); } 
		public Point getLastPoint () { return points.lastElement (); } 
		public Vector<Point> getPointList () { return points; } 
		public float getX (int position) { 
			return points.get (position).x (); 
		} 
		public float getY (int position) { 
			return points.get (position).y (); 
		} 
		public int count () { 
			return points.size (); 
		} 
		public float getTopLeftX (int startPosition, int stopPosition) { 
			if (count () < 1) return 0; 
			float minX = getX (startPosition); float x; 
			for (int i = startPosition + 1; i < Math.min (count (), stopPosition); i++) 
				if ((x = getX (i)) < minX) 
					minX = x; 
			return minX; 
		} 
		public float getTopLeftY (int startPosition, int stopPosition) { 
			if (count () < 1) return 0; 
			float minY = getY (startPosition); float y; 
			for (int i = startPosition + 1; i < Math.min (count (), stopPosition); i++) 
				if ((y = getY (i)) < minY) 
					minY = y; 
			return minY; 
		} 
		public float getBottomRightX (int startPosition, int stopPosition) { 
			if (count () < 1) return 0; 
			float maxX = getX (startPosition); float x; 
			for (int i = startPosition + 1; i < Math.min (count (), stopPosition); i++) 
				if ((x = getX (i)) > maxX) 
					maxX = x; 
			return maxX; 
		} 
		public float getBottomRightY (int startPosition, int stopPosition) { 
			if (count () < 1) return 0; 
			float maxY = getY (startPosition); float y; 
			for (int i = startPosition + 1; i < Math.min (count (), stopPosition); i++) 
				if ((y = getY (i)) > maxY) 
					maxY = y; 
			return maxY; 
		} 
		public boolean isInScreenCoordinateSpace () { 
			return true; 
		} 
		public boolean isWrite () { 
			return mType == TYPE_WRITE || (mToolType == MotionEvent.TOOL_TYPE_STYLUS && 
												   mType == TYPE_STYLUS_WRITE); 
		} 
		public float pxLength (int countFrom) { 
			float s = 0; 
			float ds, dx, dy; 
			if (countFrom < 0) 
				countFrom += count (); 
			for (int i = 1 + countFrom; i < count (); i++) { 
				dx = getX (i) - getX (i - 1); 
				dy = getY (i) - getY (i - 1); 
				ds = (float) Math.sqrt (dx * dx + dy * dy); 
				s += ds; 
			} 
			return s; 
		} 
		public float pxLength () { 
			return pxLength (0); 
		} 
		// Static: 
		static int strokeCount = 0; 
	} 
	public static double normpdf (double x, double mu, double sigma) { 
		return Math.exp ((x - mu) * (mu - x) / (2 * sigma * sigma)) / 
					   (sigma * Math.sqrt (2 * Math.PI)); 
	} 
	public static double evalAngleDifference (double a, double b) { 
		double result = a - b; 
		while (result > +Math.PI) 
			result -= 2 * Math.PI; 
		while (result < -Math.PI) 
			result += 2 * Math.PI; 
		return result; 
	} 
} 
