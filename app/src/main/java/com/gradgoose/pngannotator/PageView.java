package com.gradgoose.pngannotator;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PageView extends ImageView { 
	
	File itemFile = null; 
	
	final EditHolder edit = new EditHolder (); 
	
	static class EditHolder { 
		PngEdit value = null; 
	} 
	
	boolean mToolMode = false; 
	WriteDetector mWriteDetector; 
	
	boolean mNowWriting = false; 
	boolean mNowErasing = false; 
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 3.0f; 
	
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
									littleEdit.brushWidth = mBrush; 
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
	public void setItemFile (final File file) { 
		itemFile = file; 
		// Load just the image dimensions first: 
		final BitmapFactory.Options options = new BitmapFactory.Options (); 
		options.inJustDecodeBounds = true; 
		BitmapFactory.decodeFile (file.getPath (), options); 
		// Set our natural width and height variables to better handle onMeasure (): 
		mBitmapNaturalWidth = options.outWidth; 
		mBitmapNaturalHeight = options.outHeight; 
		// If this is one of our known files, grab a small version to load just for display: 
		int knownSmallVersion = 0; 
		try { 
			String md5 = PngEdit.calculateMD5 (file);
			Resources res = getResources (); 
			if (md5.equals (res.getString (R.string.md5_graph_paper))) { 
				knownSmallVersion = R.drawable.plain_graph_paper_4x4_small; 
			} 
		} catch (IOException err) { 
			// Do nothing. It's okay! 
		} 
		if (knownSmallVersion == 0) { 
			// If this is a different filename from before, 
			if (!file.getPath ().equals (lastLoadedPath)) { 
				// Load a REALLY small version for time time being, while it's loading 
				// (this is to avoid white blanks and confusing the user by showing 
				// them some random picture that they have just seen from a 
				// recycled view): 
				Bitmap littleBitmap; 
				File thumbnail = PngNotesAdapter.getThumbnailFile (getContext (), file); 
				if (thumbnail != null && thumbnail.exists ()) { 
					littleBitmap = BitmapFactory.decodeFile (thumbnail.getPath ()); 
				} else { 
					options.inJustDecodeBounds = false; 
					options.inSampleSize = calculateInSampleSize (options.outWidth, 
							options.outHeight, 
							16, 16); 
					littleBitmap = BitmapFactory.decodeFile (file.getPath (), options); 
				} 
				setImageBitmap (littleBitmap); 
				// Update the last loaded path: 
				lastLoadedPath = file.getPath (); 
			} 
			// Load the bitmap in a separate thread: 
			(new Thread () { 
				@Override public void run () { 
					// Calculate the down-sample scale: 
					options.inSampleSize = 
							calculateInSampleSize (options.outWidth, 
									options.outHeight, 
									getWidth (), 
									0); 
					// Now actually load the bitmap, down-sampled if needed: 
					options.inJustDecodeBounds = false; 
					final Bitmap myBitmap = BitmapFactory.decodeFile (file.getPath (), options); 
					((Activity) getContext ()).runOnUiThread (new Runnable () { 
						@Override public void run () { 
							setImageBitmap (myBitmap); 
						} 
					}); 
				} 
			}).start (); 
		} else { 
			// If this 'else' bracket was reached, it means that there is a known 
			// smaller version of the picture. Use IT! 
			setImageResource (knownSmallVersion); 
		} 
		// Now load our edits for this picture: 
		try { 
			synchronized (edit) { 
				edit.value = PngEdit.forFile (getContext (), file); 
				edit.value.setWindowSize (getWidth (), getHeight ()); 
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
		if (edit.value != null) synchronized (edit) { 
			edit.value.setWindowSize (w, h); 
		} 
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
	
	int prevColor = 0; 
	int prevTool = 0; 
	
	@Override public void onDraw (Canvas canvas) { 
		// Let the superclass draw the target image for us: 
		super.onDraw (canvas); 
		// Check if the previous tool and color are different, and if so then 
		// it's been a while, and we need to clear our temporary path: 
		if (prevColor != mColor || prevTool != mTool) { 
			tmpPointCount = 0; 
			prevColor = mColor; 
			prevTool = mTool; 
		} 
		// Now draw our annotation edits that the user made: 
		if (edit.value != null) synchronized (edit) {
			PngEdit.LittleEdit e; 
			for (int i = 0; i < edit.value.mEdits.size (); i++) { 
				e = edit.value.mEdits.elementAt (i); 
				strokePaint.setColor (e.color); 
				strokePaint.setStrokeWidth (e.brushWidth); 
				canvas.drawLines (e.points, strokePaint); 
			} 
		} 
		// Finally, draw the currently being written path: 
		strokePaint.setColor (mNowErasing ? getContext ().getResources () 
													.getColor (R.color.colorEraser) : mColor); 
		strokePaint.setStrokeWidth (mBrush); 
		canvas.drawLines (tmpPoints, 0, tmpPointCount, strokePaint); 
	} 
	
} 
