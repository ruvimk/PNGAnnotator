package com.gradgoose.pngannotator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PageView extends ImageView { 
	static final String TAG = "PageView"; 
	
	File itemFile = null; 
	
	final EditHolder edit = new EditHolder (); 
	
	static SharedPreferences mMd5Cache = null; 
	
	static class EditHolder { 
		PngEdit value = null; 
	} 
	
	public interface ErrorCallback { 
		void onBitmapOutOfMemory (); 
	} 
	
	ErrorCallback mErrorCallback = null; 
	
	boolean mToolMode = false; 
	WriteDetector mWriteDetector; 
	
	boolean mNowWriting = false; 
	boolean mNowErasing = false; 
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 1.0f; // in mm 
	
	float tmpPoints [] = new float [256]; 
	int tmpPointCount = 0; 
	
	int executingPushes = 0; // To have some sort of synchronization between pushStrokes (); 
		class MyWork { 
					public String updateStrokeEdits (WriteDetector.Stroke... params) { 
						// Wait for any other operations to complete on the strokes. 
						while (executingPushes > 0) { 
							try { 
								Thread.sleep (10); 
							} catch (InterruptedException err) { 
								// Do nothing. It's okay! 
							} 
						} 
						executingPushes++; 
						// Convert the strokes into these little edits of ours: 
						boolean hasErase = false; 
						synchronized (edit) { 
							int oldSize = edit.value.mEdits.size (); 
							for (WriteDetector.Stroke stroke : params) { 
									PngEdit.LittleEdit littleEdit = new PngEdit.LittleEdit (); 
									littleEdit.color = mColor; 
									littleEdit.brushWidth = mBrush * PaperGenerator.getPxPerMm ( 
											mBitmapNaturalWidth, 
											mBitmapNaturalHeight 
									); 
									littleEdit.points = new float[(stroke.count () - 1) * 4]; 
									littleEdit.points[0] = stroke.getX (0); 
									littleEdit.points[1] = stroke.getY (0); 
									int i; 
									for (i = 1; i + 1 < stroke.count (); i++) { 
										littleEdit.points[4 * i - 2] = stroke.getX (i); 
										littleEdit.points[4 * i - 1] = stroke.getY (i); 
										littleEdit.points[4 * i + 0] = stroke.getX (i); 
										littleEdit.points[4 * i + 1] = stroke.getY (i); 
									} 
									littleEdit.points[4 * i - 2] = stroke.getX (i); 
									littleEdit.points[4 * i - 1] = stroke.getY (i); 
									edit.value.addEdit (littleEdit); 
							} 
							// Try to save the strokes: 
							try { 
								// TODO:  IF we end up doing non-append operations 
								// to the LittleEdit list, then we should set 
								// edit.value.useDifferentialSave = false 
								// to RE-SAVE the whole file rather than 
								// just appending the last edits. 
								// SAME applies to UNDO operations, etc., 
								// whenever we're NOT simply appending. 
								edit.value.saveEdits (); // Save. 
								executingPushes--; // Make the counter 0, so strokes can be edited again. 
							} catch (IOException err) { 
								// Log this error: 
								err.printStackTrace (); 
								// Restore all the previous edits (to not fool the user of false 'save'): 
//								if (!hasErase) // (but it gets complicated with erasing, so 
//									edit.value.mEdits.setSize (oldSize); // just undo writes, no erases). 
								edit.value.mEdits.setSize (oldSize); 
								// Make the counter 0, so strokes can be edited by other operations now: 
								executingPushes--; 
								// Return an error code: 
								return "IOException"; 
							} 
						} 
						if (!mNowErasing && !mNowWriting) 
							tmpPointCount = 0; 
						return ""; 
					} 
					
					public void onPreExecute () { 
						// What to do before the task executes. 
						// I believe this runs on the user thread. 
						
					} 
					
					public void onPostExecute (String result) { 
						// What to do once it's done executing. 
						// This is also the user thread. 
						if (result.equals ("IOException")) { 
							// If the result is an I/O error, display the I/O error message: 
							Toast.makeText (getContext (), 
									R.string.error_io_no_edit, 
									Toast.LENGTH_SHORT) 
									.show (); 
						} 
						invalidate (); // Redraw! 
					} 
					
					public void onProgressUpdate (Object... values) { 
						
					} 
				}; 
				MyWork mGlobalPushStroke = new MyWork (); 
	void pushStrokes (WriteDetector.Stroke ... params) { // mGlobalPushStroke
		AsyncTask<WriteDetector.Stroke, Object, String> mPushStroke = 
				new AsyncTask<WriteDetector.Stroke, Object, String> () { 
					@Override protected String doInBackground (WriteDetector.Stroke... params) { 
						return mGlobalPushStroke.updateStrokeEdits (params); 
					} 
					@Override protected void onPreExecute () { 
						mGlobalPushStroke.onPreExecute (); 
					} 
					@Override protected void onPostExecute (String result) { 
						mGlobalPushStroke.onPostExecute (result); 
					} 
					@Override protected void onProgressUpdate (Object... values) { 
						mGlobalPushStroke.onProgressUpdate (values); 
					} 
				}; 
		mPushStroke.execute (params); 
	} 
	void pushStrokesInThisThread (WriteDetector.Stroke ... params) { 
		mGlobalPushStroke.onPreExecute (); 
		String result = mGlobalPushStroke.updateStrokeEdits (params); 
		mGlobalPushStroke.onPostExecute (result); 
	} 
	
	static final int ERASE_COLOR = Color.WHITE; 
	public PageView (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		strokePaint.setStyle (Paint.Style.STROKE); 
		strokePaint.setStrokeCap (Paint.Cap.ROUND); 
		strokePaint.setStrokeJoin (Paint.Join.ROUND); 
		erasePaint.setStyle (Paint.Style.STROKE); 
		erasePaint.setStrokeCap (Paint.Cap.ROUND); 
		erasePaint.setStrokeJoin (Paint.Join.ROUND); 
		erasePaint.setColor (ERASE_COLOR); 
//		erasePaint.setXfermode (new PorterDuffXfermode (PorterDuff.Mode.SRC_OUT)); 
//		setLayerType (LAYER_TYPE_SOFTWARE, null); 
		mWriteDetector = new WriteDetector (getContext (), new WriteDetector.OnWriteGestureListener () { 
			@Override public boolean onStrokeBegin (int strokeID, float x, float y) { 
				if (edit.value == null) return false; 
				tmpPointCount = 2; 
				tmpPoints[0] = x; 
				tmpPoints[1] = y; 
				if (mTool == NoteActivity.TOOL_ERASER) 
					mNowErasing = true; 
				else mNowWriting = true; 
				return true; 
			} 
			
			@Override public boolean onStrokeWrite (int strokeID, 
													float x0, float y0, 
													float x1, float y1) { 
				tmpPoints[tmpPointCount + 0] = x1; 
				tmpPoints[tmpPointCount + 1] = y1; 
				tmpPoints[tmpPointCount + 2] = x1; 
				tmpPoints[tmpPointCount + 3] = y1; 
				tmpPointCount += 4; 
				if (tmpPointCount + 4 > tmpPoints.length) { 
					// Grow the size of the point buffer: 
					float now [] = new float [tmpPoints.length + 256]; 
					System.arraycopy (tmpPoints, 0, now, 0, tmpPointCount); 
					tmpPoints = now; 
				} 
				invalidate (); // Redraw. 
				return true; 
			} 
			
			@Override public void onStrokeEnd (int strokeID, float x, float y) { 
				WriteDetector.Stroke stroke = mWriteDetector.getStroke (strokeID); 
				if (mTool == NoteActivity.TOOL_ERASER) 
					mNowErasing = false; 
				else mNowWriting = false; 
				tmpPointCount -= 2; // The last two coordinates are the beginning 
				// of a new line segment, which is not needed anymore if the stroke is done. 
				pushStrokesInThisThread (stroke); 
			} 
			
			@Override public void onStrokeCancel (int strokeID) { 
				tmpPointCount = 0; 
				mNowWriting = false; 
				invalidate (); 
			} 
			
			@Override public boolean onEraseBegin (int strokeID, float x, float y) { 
				mNowErasing = true; 
				return true; 
			} 
			
			@Override public boolean onEraseMove (int strokeID, 
												  float x0, float y0, 
												  float x1, float y1) { 
				return true; 
			} 
			
			@Override public void onEraseEnd (int strokeID, float x, float y) { 
				mNowErasing = false; 
			} 
			
			@Override public void onEraseCancel (int strokeID) { 
				
			} 
			
			@Override public boolean onStrokeHold (int strokeID) { 
				return false; 
			} 
			
			@Override public boolean onBeginPan (int strokeID, float x0, float y0) { 
				return false; 
			} 
			
			@Override public boolean onSimplePan (int strokeID, 
												  float xInitial, float yInitial, 
												  float xt, float yt, 
												  float elapsedSeconds) { 
				return false; 
			} 
			
			@Override public boolean onPanHint (int strokeID, 
									  float xInitial, float yInitial, 
									  float xt, float yt, 
									  float elapsedSeconds) { 
				return false; 
			} 
			
			@Override public void onPanCancel (int strokeID, 
											   float x0, float y0, 
											   float xNow, float yNow, 
											   float elapsedSeconds) { 
				
			} 
			
			@Override public void onPanDone () { 
				
			} 
			
			@Override public boolean onPinchBegin (int strokeId1, int strokeId2, 
										 float x1, float y1, 
										 float x2, float y2) { 
				return false; 
			} 
			
			@Override public boolean onPinchTransform (int strokeId1, int strokeId2, 
											 float scale, float rotate, 
											 float translateX, float translateY, 
											 float pinchPositionX, float pinchPositionY, 
											 float elapsedSeconds) { 
				return false; 
			} 
			
			@Override public void onPinchCancel () { 
				
			} 
			
			@Override public void onPinchDone () { 
				
			} 
		});
		WriteDetector.Mode mode = mWriteDetector.getMode (); 
		mode.enableErase (true); 
		mode.enablePan (false); // We'll use the default RecyclerView pan, so disable this. 
		mode.enableWrite (true); 
	} 
	public void setPenMode (boolean penMode) { 
		mWriteDetector.setPenMode (penMode); 
	} 
	public void setToolMode (boolean toolMode) { 
		mToolMode = toolMode; 
	} 
	
	static int calculateInSampleSize (int naturalWidth, 
									  int naturalHeight, 
									  int requiredWidth, 
									  int requiredHeight) { 
		int inSampleSize = 1; 
		if (naturalHeight > requiredHeight || naturalWidth > requiredWidth) { 
			if (naturalWidth > naturalHeight && 
					requiredHeight > 0) { 
				inSampleSize = Math.round ((float) naturalHeight / (float) requiredHeight); 
			} else if (requiredWidth > 0) { 
				inSampleSize = Math.round ((float) naturalWidth / (float) requiredWidth); 
			} 
		} 
		return inSampleSize; 
	} 
	String lastLoadedPath = ""; 
	int mBitmapNaturalWidth = 1; 
	int mBitmapNaturalHeight = 1; 
	int knownSmallVersion = 0; 
	private void checkIfMd5Known (String md5) { 
		Resources res = getResources (); 
		if (md5.equals (res.getString (R.string.md5_graph_paper))) { 
			knownSmallVersion = R.drawable.plain_graph_paper_4x4_small; 
			// Make an array of line segments for drawing graph paper: 
			int width = getWidth (); 
			int height = getHeight (); 
			if (width == 0) width = 1; 
			if (height == 0) height = 1; 
			paperPoints = paperGenerator.makeGraphPaperLines (width, height); 
		} 
		else paperPoints = null; 
	} 
	class Step2Thread extends Thread { 
		boolean cancel = false; 
	} 
	
	Bitmap mPreviousSetBitmap = null; 
	@Override public void setImageBitmap (Bitmap bmp) { 
		if (mPreviousSetBitmap != null) { 
			mPreviousSetBitmap.recycle (); 
		} 
		mPreviousSetBitmap = bmp; 
		super.setImageBitmap (bmp); 
	} 
	
	@Override public void onDetachedFromWindow () { 
		super.onDetachedFromWindow (); 
		setImageBitmap (null); // Safe-guard to make sure we always free up memory we won't need. 
	} 
	
	private @Nullable 
	Step2Thread step2setItemFile (final File file) { 
		return step2setItemFile (file, 1); 
	} 
	private @Nullable 
	Step2Thread step2setItemFile (final @Nullable File file, final int attemptNumber) { 
		if (file == null) return null; 
		// Load just the image dimensions first: 
		final BitmapFactory.Options options = new BitmapFactory.Options (); 
		if (knownSmallVersion == 0) { 
			// If this is a different filename from before, 
			if (!file.getPath ().equals (lastLoadedPath)) { 
				// Load a REALLY small version for time time being, while it's loading 
				// (this is to avoid white blanks and confusing the user by showing 
				// them some random picture that they have just seen from a 
				// recycled view): 
				Bitmap littleBitmap; 
				File thumbnail = PngNotesAdapter.getThumbnailFile (getContext (), file); 
				try { 
					if (thumbnail != null && thumbnail.exists ()) { 
						littleBitmap = BitmapFactory.decodeFile (thumbnail.getPath ()); 
					} else { 
						options.inJustDecodeBounds = true; 
						BitmapFactory.decodeFile (file.getPath (), options); // Decode bounds. 
						options.inJustDecodeBounds = false; 
						options.inSampleSize = calculateInSampleSize (options.outWidth, 
								options.outHeight, 
								16, 16); 
						littleBitmap = BitmapFactory.decodeFile (file.getPath (), options); 
					} 
					setImageBitmap (littleBitmap); 
				} catch (OutOfMemoryError err) { 
					Toast.makeText (getContext (), R.string.title_out_of_mem, Toast.LENGTH_SHORT) 
							.show (); 
					err.printStackTrace (); 
				} 
				// Update the last loaded path: 
				lastLoadedPath = file.getPath (); 
			} 
			// If the view size is not known yet, then don't load the big bitmap yet because we don't know how big it should be: 
			if (getWidth () == 0) {
				Log.d (TAG, "step2setItemFile (): View size not known yet. Skipping " + itemFile.getName () + " loading ..."); 
				return null; 
			} else Log.d (TAG, "step2setItemFile (): Loading bitmap " + itemFile.getName () + " ..."); 
			// Load the bitmap in a separate thread: 
			Step2Thread thread; 
			(thread = new Step2Thread () { 
				@Override public void run () {
					BitmapFactory.Options step2options = new BitmapFactory.Options (); // Let's use our own options object in this thread. Stay safe. 
					step2options.inJustDecodeBounds = true; 
					BitmapFactory.decodeFile (file.getPath (), step2options); // In case it changed, get it again. 
					// Calculate the down-sample scale: 
					step2options.inSampleSize = 
							calculateInSampleSize (step2options.outWidth, 
									step2options.outHeight, 
									getWidth (), 
									0); 
					// Now actually load the bitmap, down-sampled if needed: 
					step2options.inJustDecodeBounds = false; 
					if (cancel) 
						return; 
					try { 
						final Bitmap myBitmap = BitmapFactory.decodeFile (file.getPath (), step2options); 
						if (!cancel) 
							((Activity) getContext ()).runOnUiThread (new Runnable () {
								@Override
								public void run () {
									if (!cancel) 
										setImageBitmap (myBitmap); 
									else myBitmap.recycle (); 
								}
							}); 
						else myBitmap.recycle (); 
					} catch (OutOfMemoryError err) { 
						// Print an error stack trace into the log: 
						err.printStackTrace (); 
						if (attemptNumber == 1) { 
							// Try again later ... 
							postDelayed (new Runnable () { 
								@Override 
								public void run () { 
									NoteActivity activity = (NoteActivity) getContext (); 
									if (activity.mPaused) 
										return; // No need to load to a paused activity. 
									step2setItemFile (file, attemptNumber + 1); // Try again. 
								} 
							}, 1000); 
						} else { 
							// Otherwise, get back to the activity ... 
							if (mErrorCallback != null) 
								mErrorCallback.onBitmapOutOfMemory (); 
						} 
					} 
				} 
			}).start (); 
			return thread; 
		} else { 
			// If this 'else' bracket was reached, it means that there is a known 
			// smaller version of the picture. Use IT! 
			// We'll actually be drawing the graph paper ourselves, so 
			// we won't use the blurry small version. 
			setImageBitmap (null); // Clear any previous bitmap. 
		} 
		return null; 
	} 
	public void setItemFile (final File file) { 
		File oldFile = itemFile; // For checking to see if we need to reload the edits or not. 
		Log.d (TAG, "Setting item file; before: " + (oldFile != null ? oldFile.getName () : "") + "; now: " + 
							(file != null ? file.getName () : "") + ";"); 
		itemFile = file; 
		// Load just the image dimensions first: 
		final BitmapFactory.Options options = new BitmapFactory.Options (); 
		options.inJustDecodeBounds = true; 
		if (file != null) BitmapFactory.decodeFile (file.getPath (), options); 
		// Set our natural width and height variables to better handle onMeasure (): 
		mBitmapNaturalWidth = options.outWidth; 
		mBitmapNaturalHeight = options.outHeight; 
		// If this is one of our known files, grab a small version to load just for display: 
		knownSmallVersion = 0; 
		String md5 = file != null ? (mMd5Cache != null ? 
							 mMd5Cache.getString (file.getAbsolutePath (), "") 
							 : "") : ""; 
		boolean md5notFound = md5.isEmpty (); 
		if (md5notFound && file != null) { 
			try { 
				md5 = PngEdit.calculateMD5 (file); 
				if (mMd5Cache != null) 
					mMd5Cache.edit ().putString (file.getAbsolutePath (), md5).apply (); 
				checkIfMd5Known (md5); 
			} catch (IOException err) { 
				// It's okay! 
			} 
		} else checkIfMd5Known (md5); 
		final Step2Thread step2 = step2setItemFile (file); 
		if (!md5notFound) { 
			// In a separate thread, check if the MD5 cache is out-of-date or not: 
			final String md5was = md5; 
			(new Thread () { 
				@Override public void run () { 
					try { 
						String md5now = PngEdit.calculateMD5 (file); 
						if (!md5now.equals (md5was)) { 
							if (step2 != null) 
								step2.cancel = true; 
							if (file != null) mMd5Cache.edit ().putString (file.getAbsolutePath (), md5now).apply (); 
							checkIfMd5Known (md5now); 
							((Activity) getContext ()).runOnUiThread (new Runnable () { 
								@Override public void run () { 
									step2setItemFile (file); 
								} 
							}); 
						} 
					} catch (IOException err) { 
						// It's okay. Do nothing. 
					} 
				} 
			}).start (); 
		} 
		// Now load our edits for this picture: 
		if (oldFile == null || !oldFile.equals (itemFile)) {
			try { 
				synchronized (edit) { 
					edit.value = PngEdit.forFile (getContext (), file); 
					edit.value.setWindowSize (getWidth (), getHeight ()); 
					edit.value.setImageSize (mBitmapNaturalWidth, mBitmapNaturalHeight); 
				} 
			} catch (IOException err) { 
				// Can't edit: 
				synchronized (edit) { 
					edit.value = null; 
				} 
				// Log this error: 
				err.printStackTrace (); 
				// Show a message to the user, telling them that they can't view/save edits: 
				Toast.makeText (getContext (), 
						R.string.error_io_no_edit, 
						Toast.LENGTH_SHORT) 
						.show (); 
			} 
		} 
		// Redraw this view: 
		invalidate (); 
	} 
	
	@Override public void onMeasure (int widthMeasureSpec, int heightMeasureSpec) { 
		if (mBitmapNaturalWidth > 0 && mBitmapNaturalHeight > 0) { 
			int wMode = MeasureSpec.getMode (widthMeasureSpec); 
			int hMode = MeasureSpec.getMode (heightMeasureSpec); 
			int maxHeight = MeasureSpec.getSize (heightMeasureSpec); 
			int needWidth = MeasureSpec.getSize (widthMeasureSpec); 
			int needHeight = maxHeight; 
			boolean changeHeight = 
					wMode == MeasureSpec.EXACTLY || 
							(wMode == MeasureSpec.AT_MOST && hMode != MeasureSpec.EXACTLY); 
			if (changeHeight && hMode != MeasureSpec.EXACTLY) 
				needHeight = needWidth * mBitmapNaturalHeight / mBitmapNaturalWidth; 
			if (hMode == MeasureSpec.AT_MOST && needHeight > maxHeight) { 
				needWidth = needWidth * maxHeight / needHeight; 
				needHeight = maxHeight; 
			} 
			setMeasuredDimension (needWidth, needHeight); 
		} else super.onMeasure (widthMeasureSpec, heightMeasureSpec); 
	} 
	
	@Override public void onSizeChanged (int w, int h, int oldW, int oldH) { 
		super.onSizeChanged (w, h, oldW, oldH); 
		Log.d (TAG, "onSizeChanged (): Reloading bitmaps, scaling vectors ..."); 
		// Update paper lines, if any: 
		if (paperPoints != null) { 
			int fromW = oldW != 0 ? oldW : 1; 
			int fromH = oldH != 0 ? oldH : 1; 
			PaperGenerator.scalePoints (paperPoints, fromW, fromH, w, h); 
		} 
		// Update edits: 
		if (edit.value != null) synchronized (edit) { 
			edit.value.setWindowSize (w, h); 
		} 
		// Get display metrics (the object that allows us to convert CM to DP): 
		metrics = Resources.getSystem ().getDisplayMetrics (); 
		// Reload the item bitmaps: 
		setItemFile (itemFile); // This will load the appropriately-sized bitmap into memory. 
	} 
	
	@Override public boolean onTouchEvent (MotionEvent event) { 
		if (mToolMode) { 
			if (event.getAction () == MotionEvent.ACTION_DOWN || 
					event.getAction () == MotionEvent.ACTION_POINTER_DOWN) { 
				if (mWriteDetector.isInPenMode ()) { 
					// Grab hold of the touches only if one of them is a pen, if it's pen mode: 
					boolean oneIsAPen = false; 
					for (int i = 0; i < event.getPointerCount (); i++) 
						if (event.getToolType (i) == MotionEvent.TOOL_TYPE_STYLUS) { 
							oneIsAPen = true; 
							break; 
						} 
					getParent ().requestDisallowInterceptTouchEvent (oneIsAPen); 
				} else getParent ().requestDisallowInterceptTouchEvent (true); 
			} 
			return mWriteDetector.onTouchEvent (event); 
		} else return super.onTouchEvent (event); 
	} 
	
	Paint strokePaint = new Paint (); 
	Paint erasePaint = new Paint (); 
	
	float paperPoints [] = null; 
	Paint paperPaint = new Paint (); // For drawing graph paper, etc. 
	PaperGenerator paperGenerator = new PaperGenerator (); 
	
	int prevColor = 0; 
	int prevTool = 0; 
	
	DisplayMetrics metrics = new DisplayMetrics (); 
	
	@Override public void onDraw (Canvas canvas) { 
		// Let the superclass draw the target image for us: 
		super.onDraw (canvas); 
		// If the target image is a small version, use it: 
		if (paperPoints != null) { 
			paperGenerator.setupGraphPaperPaint (getWidth (), paperPaint); 
			canvas.drawLines (paperPoints, paperPaint); 
		} else switch (knownSmallVersion) { 
			case R.drawable.plain_graph_paper_4x4_small: 
				paperGenerator.drawGraphPaper (canvas, paperPaint); 
				break; 
		} 
		// Check if the previous tool and color are different, and if so then 
		// it's been a while, and we need to clear our temporary path: 
		if (prevColor != mColor || prevTool != mTool) { 
			tmpPointCount = 0; 
			prevColor = mColor; 
			prevTool = mTool; 
		} 
		// Now draw our annotation edits that the user made: 
		float brushScale = mBitmapNaturalWidth != 0 ? 
								   (float) canvas.getWidth () / mBitmapNaturalWidth 
								   : paperGenerator.getScaleFactor (canvas.getWidth ()); 
		if (edit.value != null) synchronized (edit) { 
			PngEdit.LittleEdit e; 
			for (int i = 0; i < edit.value.mEdits.size (); i++) { 
				e = edit.value.mEdits.elementAt (i); 
				strokePaint.setColor (e.color); 
				strokePaint.setStrokeWidth (e.brushWidth * brushScale); 
				canvas.drawLines (e.points, strokePaint); 
			} 
		} 
		// Finally, draw the currently being written path: 
		strokePaint.setColor (mNowErasing ? getContext ().getResources () 
													.getColor (R.color.colorEraser) : mColor); 
		strokePaint.setStrokeWidth (PaperGenerator.getPxPerMm (mBitmapNaturalWidth, 
				mBitmapNaturalHeight) * mBrush * brushScale); 
		canvas.drawLines (tmpPoints, 0, tmpPointCount, strokePaint); 
	} 
	
} 
