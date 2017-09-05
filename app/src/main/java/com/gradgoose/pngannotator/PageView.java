package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Bitmap;
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
import java.io.InterruptedIOException;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PageView extends ImageView { 
	
	File itemFile = null; 
	
	PngEdit edit = null; 
	
	boolean mToolMode = false; 
	WriteDetector mWriteDetector; 
	
	boolean mNowWriting = false; 
	boolean mNowErasing = false; 
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 3.0f; 
	
	Path tmpPath = new Path (); 
	
	int executingPushes = 0; // To have some sort of synchronization between pushStrokes (); 
	void pushStrokes (WriteDetector.Stroke ... params) { 
		AsyncTask<WriteDetector.Stroke, Object, String> mPushStroke = 
				new AsyncTask<WriteDetector.Stroke, Object, String> () { 
					@Override protected String doInBackground (WriteDetector.Stroke... params) { 
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
						int oldSize = edit.mEdits.size (); 
						for (WriteDetector.Stroke stroke : params) { 
							if (mTool == NoteActivity.TOOL_ERASER || 
									stroke.getType () == WriteDetector.Stroke.TYPE_ERASE) { 
								// This was an ERASE stroke. 
								
							} else { 
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
								edit.addEdit (littleEdit); 
							} 
						} 
						// Try to save the strokes: 
						try { 
							edit.saveEdits (); // Save. 
							executingPushes--; // Make the counter 0, so strokes can be edited again. 
						} catch (IOException err) { 
							// Log this error: 
							err.printStackTrace (); 
							// Restore all the previous edits (to not fool the user of false 'save'): 
							edit.mEdits.setSize (oldSize); 
							// Make the counter 0, so strokes can be edited by other operations now: 
							executingPushes--; 
							// Return an error code: 
							return "IOException"; 
						} 
						return ""; 
					} 
					
					@Override protected void onPreExecute () { 
						// What to do before the task executes. 
						// I believe this runs on the user thread. 
						
					} 
					
					@Override protected void onPostExecute (String result) { 
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
					
					@Override protected void onProgressUpdate (Object... values) { 
						
					} 
				}; 
		mPushStroke.execute (params); 
	} 
	
	public PageView (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		strokePaint.setStyle (Paint.Style.STROKE); 
		strokePaint.setStrokeCap (Paint.Cap.ROUND); 
		strokePaint.setStrokeJoin (Paint.Join.ROUND); 
		mWriteDetector = new WriteDetector (getContext (), new WriteDetector.OnWriteGestureListener () { 
			@Override public boolean onStrokeBegin (int strokeID, float x, float y) { 
				if (edit == null) return false; 
				tmpPath.rewind (); 
				tmpPath.moveTo (x, y); 
				if (mTool == NoteActivity.TOOL_ERASER) 
					mNowErasing = true; 
				else mNowWriting = true; 
				return true; 
			} 
			
			@Override public boolean onStrokeWrite (int strokeID, 
													float x0, float y0, 
													float x1, float y1) { 
				tmpPath.lineTo (x1, y1); 
				invalidate (); // Redraw. 
				return true; 
			} 
			
			@Override public void onStrokeEnd (int strokeID, float x, float y) { 
				WriteDetector.Stroke stroke = mWriteDetector.getStroke (strokeID); 
				pushStrokes (stroke); 
				tmpPath.rewind (); 
				if (mTool == NoteActivity.TOOL_ERASER) 
					mNowErasing = false; 
				else mNowWriting = false; 
			} 
			
			@Override public void onStrokeCancel (int strokeID) { 
				tmpPath.rewind (); 
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
//				return !mToolMode && 
//							   !mWriteDetector.isInPenMode (); // Only pan with finger in pen mode. 
			} 
			
			@Override public boolean onSimplePan (int strokeID, 
												  float xInitial, float yInitial, 
												  float xt, float yt, 
												  float elapsedSeconds) { 
				return false; 
//				return !mWriteDetector.isInPenMode (); 
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
		mode.enablePan (false); 
		mode.enableWrite (true); 
	} 
	public void setPenMode (boolean penMode) { 
		mWriteDetector.setPenMode (penMode); 
	} 
	public void setToolMode (boolean toolMode) { 
		mToolMode = toolMode; 
	} 
	
	public void setItemFile (File file) { 
		itemFile = file; 
		setImageURI (Uri.fromFile (file)); 
		try { 
			edit = PngEdit.forFile (getContext (), file); 
			edit.setWindowSize (getWidth (), getHeight ()); 
		} catch (IOException err) { 
			// Can't edit: 
			edit = null; 
			// Log this error: 
			err.printStackTrace (); 
			// Show a message to the user, telling them that they can't view/save edits: 
			Toast.makeText (getContext (), 
					R.string.error_io_no_edit, 
					Toast.LENGTH_SHORT) 
					.show (); 
		} 
	} 
	
	@Override public void onSizeChanged (int w, int h, int oldW, int oldH) { 
		super.onSizeChanged (w, h, oldW, oldH); 
		if (edit != null) 
			edit.setWindowSize (w, h); 
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
	Bitmap mBitmap = null; 
	Canvas mCanvas = null; 
	
	void ensureBitmapRightSize () { 
		int needW = getWidth (); 
		int needH = getHeight (); 
		if (mBitmap == null || mBitmap.getWidth () != needW || mBitmap.getHeight () != needH) { 
			if (mBitmap != null) 
				mBitmap.recycle (); 
			mBitmap = Bitmap.createBitmap (needW, needH, Bitmap.Config.ARGB_8888); 
			mCanvas = new Canvas (mBitmap); 
		} 
	} 
	
	@Override public void onDraw (Canvas canvas) { 
		// Let the superclass draw the target image for us: 
		super.onDraw (canvas); 
		// Now draw our annotation edits that the user made: 
		if (edit != null) for (PngEdit.LittleEdit e : edit.mEdits) { 
			strokePaint.setColor (e.color); 
			strokePaint.setStrokeWidth (e.brushWidth); 
			canvas.drawLines (e.points, strokePaint); 
		} 
		// Finally, draw the currently being written path: 
		strokePaint.setColor (mColor); 
		strokePaint.setStrokeWidth (mBrush); 
		canvas.drawPath (tmpPath, strokePaint); 
	} 
	
} 
