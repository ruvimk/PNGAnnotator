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
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 3.0f; 
	
	Path tmpPath = new Path (); 
	
	void pushStrokes (WriteDetector.Stroke ... params) { 
		AsyncTask<WriteDetector.Stroke, Object, String> mPushStroke = 
				new AsyncTask<WriteDetector.Stroke, Object, String> () { 
					@Override protected String doInBackground (WriteDetector.Stroke... params) { 
						Vector<PngEdit.LittleEdit> mJustNowEdits = new Vector<> (params.length); 
						for (WriteDetector.Stroke stroke : params) { 
							if (mTool == NoteActivity.TOOL_ERASER) { 
								
							} else { 
								PngEdit.LittleEdit littleEdit = new PngEdit.LittleEdit (); 
								littleEdit.color = mColor; 
								littleEdit.brushWidth = mBrush; 
								littleEdit.points = new float[(stroke.count () - 1) * 4]; 
								littleEdit.points[0] = stroke.getX (0); 
								littleEdit.points[1] = stroke.getY (0); 
								int i; 
								for (i = 1; i + 1 < stroke.count (); i++) { 
									littleEdit.points[2 * i + 0] = stroke.getX (i); 
									littleEdit.points[2 * i + 1] = stroke.getY (i); 
									littleEdit.points[2 * i + 2] = stroke.getX (i); 
									littleEdit.points[2 * i + 3] = stroke.getY (i); 
								} 
								littleEdit.points[2 * i + 0] = stroke.getX (i); 
								littleEdit.points[2 * i + 1] = stroke.getY (i); 
								mJustNowEdits.add (littleEdit); 
								edit.addEdit (littleEdit); 
							} 
						} 
						try { 
							edit.saveEdits (); 
						} catch (IOException err) { 
							// Log this error: 
							err.printStackTrace (); 
							// Take out the last edits (to not fool the user of false 'save'): 
							for (PngEdit.LittleEdit e : mJustNowEdits) 
								edit.removeEdit (e); 
							// Return an error code: 
							return "IOException"; 
						} 
						return ""; 
					} 
					
					@Override protected void onPreExecute () { 
						
					} 
					
					@Override protected void onPostExecute (String result) { 
						if (result.equals ("IOException")) { 
							Toast.makeText (getContext (), 
									R.string.error_io_no_edit, 
									Toast.LENGTH_SHORT) 
									.show (); 
						} else invalidate (); // Redraw! 
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
				mNowWriting = true; 
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
				mNowWriting = false; 
			} 
			
			@Override public void onStrokeCancel (int strokeID) { 
				tmpPath.rewind (); 
				mNowWriting = false; 
				invalidate (); 
			} 
			
			@Override public boolean onEraseBegin (int strokeID, float x, float y) { 
				return true; 
			} 
			
			@Override public boolean onEraseMove (int strokeID, 
												  float x0, float y0, 
												  float x1, float y1) { 
				return true; 
			} 
			
			@Override public void onEraseEnd (int strokeID, float x, float y) { 
				
			} 
			
			@Override public void onEraseCancel (int strokeID) { 
				
			} 
			
			@Override public boolean onStrokeHold (int strokeID) { 
				return false; 
			} 
			
			@Override public boolean onBeginPan (int strokeID, float x0, float y0) { 
				return !mWriteDetector.isInPenMode (); // Only pan with finger in pen mode. 
			} 
			
			@Override public boolean onSimplePan (int strokeID, 
												  float xInitial, float yInitial, 
												  float xt, float yt, 
												  float elapsedSeconds) { 
				return !mWriteDetector.isInPenMode (); 
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
