package com.gradgoose.pennotepad;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.io.IOException;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PageView extends ImageView implements TouchInfoSetter { 
	static final String TAG = "PageView"; 
	static final int VIEW_LARGE = 0; 
	static final int VIEW_SMALL = 1; 
	
	static final int THUMBNAIL_SIZE = 64; 
	static final float THUMBNAIL_MULTIPLIER = 0.01f; 
	
	static final float GLIDE_USE_PIXEL_LARGE_SIZE = .75f; // Change this to update image load density. 
	static final float GLIDE_USE_PIXEL_SMALL_SIZE = .66f; 
	float GLIDE_LARGE_SIZE_MULT = 0.125f; // This will be changed by the constructor. 
	float GLIDE_SMALL_SIZE_MULT = 0.125f; 
	
	int viewMode = VIEW_LARGE; 
	
	float borderWidth = 1.0f; 
	
	File itemFile = null; 
	int itemPage = 0; 
	
	int tintColor = Color.TRANSPARENT; 
	
	final EditHolder edit = new EditHolder (); 
	final PngEdit.Cache strokeCache = new PngEdit.Cache (); 
	
	static SharedPreferences mMd5Cache = null; 
	
	static class EditHolder { 
		PngEdit value = null; 
	} 
	
	public void setTintColor (int color) { 
		if (color == tintColor) return; 
		tintColor = color; 
		invalidate (); 
	} 
	
	RequestRedraw redrawRequestListener = null; 
	
	public interface RequestRedraw { 
		void requestRedrawPagePDF (PageView pageView, File file, int page, 
								   int putX, int putY, int putWidth, int putHeight, 
								   int wideScaleParameter, boolean skipDrawingIfPutParametersTheSame); 
		void requestRedrawImage (File imageFile, PageView view); 
		void requestClearImage (PageView view); 
		int getPageWidth (int page); 
		int getPageHeight (int page); 
	} 
	
	public interface ErrorCallback { 
		void onBitmapOutOfMemory (); 
		void onBitmapLoadError (); 
	} 
	
	public interface SizeChanged { 
		void onSizeChanged (); 
	} 
	
	ErrorCallback mErrorCallback = null; 
	
	SizeChanged mSizeChangeCallback = null; 
	
	boolean mToolMode = false; 
	WriteDetector mWriteDetector; 
	GestureDetector mOtherGestureDetector; 
	
	boolean mNowWriting = false; 
	boolean mNowErasing = false; 
	boolean mNowWhiting = false; 
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 1.0f; // in mm 
	
	float optimization_minStrokeSpan = 0f; // 0 means don't filter strokes based on their size. Units are out of this view's width. 
	
	float tmpPoints [] = new float [256]; 
	int tmpPointCount = 0; 
	
	void setOptimizationMinStrokeSpan (float span) { 
		optimization_minStrokeSpan = span; 
	} 
	
	float debug_polygons [] [] = null; 
	
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
						boolean isEraser = mTool == NoteActivity.TOOL_ERASER; 
						float brushScale = paperGenerator.getBrushScale ((int) edit.value.windowWidth); 
						synchronized (edit) { 
							int oldSize = edit.value.mEdits.size (); 
							for (WriteDetector.Stroke stroke : params) { 
								if (isEraser) { 
									int pointCount = stroke.count (); 
									float path [] = new float [2 * pointCount]; 
									for (int i = 0; i < pointCount; i++) { 
										path[2 * i + 0] = stroke.getX (i); 
										path[2 * i + 1] = stroke.getY (i); 
									} 
									float polygons [] [] = PngEdit.convertPathToPolygons (path, mBrush / 2 * brushScale); 
									edit.value.erase (polygons); 
									debug_polygons = polygons; 
								} else { 
									PngEdit.LittleEdit littleEdit = new PngEdit.LittleEdit (); 
									littleEdit.color = mColor; 
									littleEdit.brushWidth = mBrush * brushScale; 
									if (stroke.count () == 1) { 
										littleEdit.points = new float [8]; 
										float centerX = stroke.getX (0); 
										float centerY = stroke.getY (0); 
										for (int i = 0; i < 4; i++) { 
											double angle = (double) i / 4 * (2 * Math.PI); 
											littleEdit.points[2 * i + 0] = (float) Math.cos (angle) + centerX; 
											littleEdit.points[2 * i + 1] = (float) Math.sin (angle) + centerY; 
										} 
									} else { 
										littleEdit.points = new float [(stroke.count () - 1) * 4]; 
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
									} 
									edit.value.addEdit (littleEdit); 
								} 
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
								boolean needResize = (int) (edit.value.windowWidth * 100 / edit.value.windowHeight) != 
															 getWidth () * 100 / getHeight () || 
															 (edit.value.srcPageWidth * 100 / edit.value.srcPageHeight) != 
																	 getWidth () * 100 / getHeight (); 
								if (needResize) { 
									float w = edit.value.windowWidth; 
									float h = w * getHeight () / getWidth (); 
									edit.value.setWindowSize (w, h); 
									edit.value.setImageSize (w, h); 
									edit.value.srcPageWidth = (int) w; 
									edit.value.srcPageHeight = (int) h; 
								} 
								if (isEraser || needResize) 
									edit.value.useDifferentialSave = false; // TODO: Take this into a separate thread, as non-differential save *is* slow. 
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
							strokeCache.update (edit.value); 
						} 
						if (!mNowErasing && !mNowWriting && !mNowWhiting) 
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
	void pushStrokesInThisThread (WriteDetector.Stroke ... params) { 
		mGlobalPushStroke.onPreExecute (); 
		String result = mGlobalPushStroke.updateStrokeEdits (params); 
		mGlobalPushStroke.onPostExecute (result); 
	} 
	
	static final int ERASE_COLOR = Color.WHITE; 
	public PageView (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
		metrics = getResources ().getDisplayMetrics (); 
		GLIDE_LARGE_SIZE_MULT = GLIDE_USE_PIXEL_LARGE_SIZE / metrics.density; 
		GLIDE_SMALL_SIZE_MULT = GLIDE_USE_PIXEL_SMALL_SIZE / metrics.density; 
		strokePaint.setStyle (Paint.Style.STROKE); 
		strokePaint.setStrokeCap (Paint.Cap.ROUND); 
		strokePaint.setStrokeJoin (Paint.Join.ROUND); 
		erasePaint.setStyle (Paint.Style.STROKE); 
		erasePaint.setStrokeCap (Paint.Cap.ROUND); 
		erasePaint.setStrokeJoin (Paint.Join.ROUND); 
		erasePaint.setColor (ERASE_COLOR); 
//		erasePaint.setXfermode (new PorterDuffXfermode (PorterDuff.Mode.SRC_OUT)); 
//		setLayerType (LAYER_TYPE_SOFTWARE, null); 
		borderPaint.setStyle (Paint.Style.STROKE); 
		borderPaint.setColor (Color.argb (128, 0, 0, 0)); 
		mOtherGestureDetector = new GestureDetector (getContext (), new GestureDetector.OnGestureListener () { 
			@Override public boolean onDown (MotionEvent motionEvent) { 
				return true; 
			} 
			
			@Override public void onShowPress (MotionEvent motionEvent) { 
				
			} 
			
			@Override public boolean onSingleTapUp (MotionEvent motionEvent) { 
				performClick (); // Just perform a click - tell the View that it was clicked. 
				return true; 
			} 
			
			@Override public boolean onScroll (MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) { 
				return false; 
			} 
			
			@Override public void onLongPress (MotionEvent motionEvent) { 
				if (Build.VERSION.SDK_INT >= 24) 
					performLongClick (motionEvent.getX (), motionEvent.getY ()); // Tell it about a long click. 
				else performLongClick (); 
			} 
			
			@Override public boolean onFling (MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) { 
				return false; 
			} 
		}); 
		mWriteDetector = new WriteDetector (getContext (), new WriteDetector.OnWriteGestureListener () { 
			@Override public boolean onStrokeBegin (int strokeID, float x, float y) { 
				if (edit.value == null) return false; 
				tmpPointCount = 2; 
				tmpPoints[0] = x; 
				tmpPoints[1] = y; 
				if (mTool == NoteActivity.TOOL_ERASER) 
					mNowErasing = true; 
				else if (mTool == NoteActivity.TOOL_WHITEOUT) 
					mNowWhiting = true; 
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
				else if (mTool == NoteActivity.TOOL_WHITEOUT) 
					mNowWhiting = false; 
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
				pushStrokesInThisThread (mWriteDetector.getStroke (strokeID)); 
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
		mode.setMaximumHandleTouchCount (1); // We'll handle zoom from ScaleDetectorContainer. 
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
				inSampleSize = (int) Math.ceil ((float) naturalHeight / (float) requiredHeight); 
			} else if (requiredWidth > 0) { 
				inSampleSize = (int) Math.ceil ((float) naturalWidth / (float) requiredWidth); 
			} 
		} 
		return inSampleSize; 
	} 
	int mBitmapNaturalWidth = 1; 
	int mBitmapNaturalHeight = 1; 
	int mBitmapLoadHeight = 1; 
	boolean isAnnotatedPage = false; 
	boolean isPDF = false; 
	
	final Object mBackgroundBmpMutex = new Object (); 
	Bitmap mBackgroundBitmap = null; // For PDF rendering, as of right now (02/19/2018). 
	
	boolean mAttachedToWindow = false; 
	@Override public void onAttachedToWindow () { 
		super.onAttachedToWindow (); 
		mAttachedToWindow = true; 
		setItemFile (itemFile, itemPage); // Reload bitmaps. 
	} 
	@Override public void onDetachedFromWindow () { 
		super.onDetachedFromWindow (); 
		mAttachedToWindow = false; 
		if (hasGlideImage) { 
//			try { 
//				Glide.with (getContext ()) 
//						.clear (this); 
//			} catch (IllegalArgumentException err) { 
//				Log.e (TAG, err.getLocalizedMessage ()); 
//			} 
			redrawRequestListener.requestClearImage (this); 
		} 
//		setImageBitmap (null); // Safe-guard to make sure we always free up memory we won't need. 
	} 
	
	void cleanUp () { 
		synchronized (mBackgroundBmpMutex) { 
			if (mBackgroundBitmap != null) { 
				mBackgroundBitmap.recycle (); 
				mBackgroundBitmap = null; 
			} 
		} 
		if (hasGlideImage) { 
//			try { 
//				Glide.with (getContext ()).clear (this); 
//			} catch (IllegalArgumentException err) { 
//				Log.e (TAG, err.getLocalizedMessage ()); 
//			} 
			redrawRequestListener.requestClearImage (this); 
			hasGlideImage = false; 
		} 
	} 
	
	private void loadGlideImage (File imageFile) { 
		if (!isAnnotatedPage && !isPDF) { 
//			RequestBuilder builder = Glide.with (getContext ()) 
//											 .load (imageFile) 
//											 .apply (RequestOptions.skipMemoryCacheOf (true)) 
//											 .apply (RequestOptions.diskCacheStrategyOf (DiskCacheStrategy.RESOURCE)) 
//											 .apply (RequestOptions.sizeMultiplierOf (viewMode == VIEW_SMALL ? GLIDE_SMALL_SIZE_MULT : GLIDE_LARGE_SIZE_MULT)) 
//											 .listener (new RequestListener<Drawable> () { 
//												 @Override public boolean onLoadFailed (@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) { 
//													 if (mErrorCallback != null) 
//														 mErrorCallback.onBitmapLoadError (); 
//													 return false; 
//												 } 
//												 @Override public boolean onResourceReady (Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) { 
//													 return false; 
//												 } 
//											 }); 
//			if (viewMode == VIEW_LARGE) 
//				builder.thumbnail (THUMBNAIL_MULTIPLIER); 
//			builder.into (this); 
//			hasGlideImage = true; 
			redrawRequestListener.requestRedrawImage (imageFile, this); 
		} 
		else if (hasGlideImage) { 
//			try { 
//				Glide.with (getContext ()) 
//						.clear (this); 
//			} catch (IllegalArgumentException err) { 
//				Log.e (TAG, err.getLocalizedMessage ()); 
//			} 
			redrawRequestListener.requestClearImage (this); 
			hasGlideImage = false; 
		} 
	} 
	
	boolean hasGlideImage = false; 
	int needRotateAngle = 0; 
	long itemLastModifiedTime = 0; 
	public void setItemFile (File file, int page) { 
		File oldFile = itemFile; // For checking to see if we need to reload the edits or not. 
		int oldPage = itemPage; 
		itemFile = file; 
		itemPage = page; 
		boolean needReload = true; 
		if (file != null) {
			String fileLowerName = file.getName ().toLowerCase (); 
			if (file.equals (oldFile) && page == oldPage) { 
				long lastModified = file.lastModified (); 
				if (lastModified == itemLastModifiedTime) { 
					// Well, the file has not been modified at all since the last load, 
					// and this is the same file, same page; let's just skip this. 
					if (hasGlideImage || // If we *have* the image loaded, 
								(!fileLowerName.endsWith (".png") && // OR: if it's not an image 
										 !fileLowerName.endsWith (".gif") && // (not any image 
										 !fileLowerName.endsWith (".jpg") && // extension we 
										 !fileLowerName.endsWith (".jpeg")) // recognize), 
							) { 
						// Then, it's a file that doesn't need loading ... 
						Log.i (TAG, "File not modified; skipping loading. "); 
						return; // No need reloading at all ... 
					} 
					// Else, need to reload the Glide image, but still no need to reload anything else ... 
					needReload = false; 
				} else itemLastModifiedTime = lastModified; 
			} 
			if (fileLowerName.endsWith (".apg")){ 
					isAnnotatedPage = true; 
			} else if (fileLowerName.endsWith (".pdf")) 
				isPDF = true; 
		} else { 
			isAnnotatedPage = false; 
		} 
		if (!isAnnotatedPage && !isPDF) { 
			// Clear image: 
			setImageDrawable (null); 
			// Load just the image dimensions first: 
			final BitmapFactory.Options options = new BitmapFactory.Options (); 
			options.inJustDecodeBounds = true; 
			if (file != null) { 
				int width = 0, height = 0; 
				try { 
					// Get the image orientation (the camera app takes portrait pictures in landscape for some devices): 
					// (See SO/questions/12933085/android-camera-intent-saving-image-landscape-when-taken-portrait) 
					ExifInterface exif = new ExifInterface (file.getPath ()); 
					String orientString = exif.getAttribute (ExifInterface.TAG_ORIENTATION); 
					width = exif.getAttributeInt (ExifInterface.TAG_IMAGE_WIDTH, 0); 
					height = exif.getAttributeInt (ExifInterface.TAG_IMAGE_LENGTH, 0); 
					int orientation = orientString != null ? Integer.parseInt (orientString) : ExifInterface.ORIENTATION_NORMAL; 
					switch (orientation) { 
						case ExifInterface.ORIENTATION_ROTATE_90: 
							needRotateAngle = 90; 
							break; 
						case ExifInterface.ORIENTATION_ROTATE_180: 
							needRotateAngle = 180; 
							break; 
						case ExifInterface.ORIENTATION_ROTATE_270: 
							needRotateAngle = 270; 
							break; 
						case ExifInterface.ORIENTATION_NORMAL: 
						default: 
							needRotateAngle = 0; 
					} 
				} catch (IOException err) { 
					Log.e (TAG, err.getMessage ()); 
					needRotateAngle = 0; 
				} 
				if (width == 0 || height == 0) { 
					BitmapFactory.decodeFile (file.getPath (), options); 
					width = options.outWidth; 
					height = options.outHeight; 
				} 
				// Set our natural width and height variables to better handle onMeasure (): 
				if (needRotateAngle == 90 || needRotateAngle == 270) { 
					// The orientation requires us to swap the width and the height ... 
					//noinspection SuspiciousNameCombination
					mBitmapNaturalWidth = height; 
					//noinspection SuspiciousNameCombination
					mBitmapNaturalHeight = width; 
				} else { 
					mBitmapNaturalWidth = width; 
					mBitmapNaturalHeight = height; 
				} 
			} else { 
				// file is null 
				Log.i (TAG, "setItemFile () file parameter is null; defaulting to normal orientation (angle = 0)"); 
				needRotateAngle = 0; 
			} 
			// We set natural width and height for the annotated page case after we load the edits. 
		} else if (isPDF) { 
			// Let's load the PDF page size for now ... 
			mBitmapNaturalWidth = redrawRequestListener.getPageWidth (page); 
			mBitmapNaturalHeight = redrawRequestListener.getPageHeight (page); 
		} 
		// Load the image with the Glide library: 
		loadGlideImage (file); 
		// If no need to reload anything else, then exit now: 
		if (!needReload) 
			return; 
		// Take care of PDF redrawing: 
		lastRenderW = lastRenderH = 0; 
		computePageDrawPosition (); 
		// Now load our edits for this picture: 
		if (oldFile == null || !oldFile.equals (itemFile) || oldPage != itemPage) { 
			final File targetFile = file; 
			final int targetPage = page; 
			// Note: This may be a pre-fetch operation, so let's do the loading in a separate thread to keep the UI responsive. 
			(new Thread () { 
				@Override public void run () { 
					try { 
						synchronized (edit) { 
							edit.value = PngEdit.forFile (getContext (), targetFile, targetPage); 
							if (isAnnotatedPage) { 
								mBitmapNaturalWidth = edit.value.srcPageWidth; 
								mBitmapNaturalHeight = edit.value.srcPageHeight; 
							} else { 
								edit.value.srcPageWidth = mBitmapNaturalWidth; 
								edit.value.srcPageHeight = mBitmapNaturalHeight; 
							} 
							edit.value.setWindowSize (mBitmapNaturalWidth, mBitmapNaturalHeight); 
							edit.value.setImageSize (mBitmapNaturalWidth, mBitmapNaturalHeight); 
							strokeCache.update (edit.value); 
							// Make the background: 
							int background = 0; 
							synchronized (edit) { 
								if (edit.value != null) background = edit.value.srcPageBackground; 
							} 
							if (background == 1) paperPoints = paperGenerator.makeGraphPaperLines (getWidth (), getHeight ()); 
							else paperPoints = null; 
						} 
						((Activity) getContext ()).runOnUiThread (new Runnable () { 
							@Override public void run () { 
								requestLayout (); // Size may have changed. 
								invalidate (); 
							} 
						}); 
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
			}).start (); 
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
	
	@Override public void onSizeChanged (final int w, final int h, int oldW, int oldH) { 
		super.onSizeChanged (w, h, oldW, oldH); 
//		Log.d (TAG, "onSizeChanged (): Reloading bitmaps, scaling vectors ..."); 
		// Load blank into this view: 
		if (hasGlideImage) { 
//			try { 
//				Glide.with (getContext ()) 
//						.clear (this); 
//			} catch (IllegalArgumentException err) { 
//				Log.e (TAG, err.getLocalizedMessage ()); 
//			} 
			redrawRequestListener.requestClearImage (this); 
		} 
		// Reload image: 
		loadGlideImage (itemFile); 
		// Notify adapter's listener, etc. (for example, to re-render the PDF page): 
		if (mSizeChangeCallback != null) 
			mSizeChangeCallback.onSizeChanged (); 
		// Update paper lines, if any: 
		if (paperPoints != null) { 
			int fromW = oldW != 0 ? oldW : 1; 
			int fromH = oldH != 0 ? oldH : 1; 
			PaperGenerator.scalePoints (paperPoints, fromW, fromH, w, h); 
		} 
		// Update edits: 
		if (edit.value != null) (new Thread () { 
			@Override public void run () { 
				synchronized (edit) { 
					edit.value.setWindowSize (w, h); 
					edit.value.setImageSize (w, h); 
					strokeCache.update (edit.value); 
					// Now update the background pattern, since it sort of depends on the view size: 
					int background = 0; 
					if (edit.value != null) background = edit.value.srcPageBackground; 
					if (background == 1) paperPoints = paperGenerator.makeGraphPaperLines (getWidth (), getHeight ()); 
					else paperPoints = null; 
				} 
				((Activity) getContext ()).runOnUiThread (new Runnable () { 
					@Override public void run () { 
						invalidate (); 
					} 
				}); 
			} 
		}).start (); 
	} 
	
	boolean mTouchMoved = false; 
	float lastTouchedX = 0; 
	float lastTouchedY = 0; 
	int lastTouchedToolType = 0; 
	@Override public void setLastTouchedPoint (float x, float y) { 
		lastTouchedX = x; 
		lastTouchedY = y; 
	} 
	@Override public void setLastTouchedToolType (int type) { 
		lastTouchedToolType = type; 
	} 
	@Override public float getLastTouchedX () { return lastTouchedX; } 
	@Override public float getLastTouchedY () { return lastTouchedY; } 
	@Override public int getLastTouchedToolType () { return lastTouchedToolType; } 
	@Override public void setTouchMoved (boolean touchMoved) { 
		mTouchMoved = touchMoved; 
	} 
	@Override public boolean hasTouchMoved () { 
		return mTouchMoved; 
	} 
	
	@Override public boolean onTouchEvent (MotionEvent event) { 
		setLastTouchedPoint (event.getX (), event.getY ()); 
		setLastTouchedToolType (event.getToolType (0)); 
		int [] locationScreen = new int [2]; 
		int [] locationWindow = new int [2];
		Rect visible = new Rect (); 
		getLocationInWindow (locationWindow); 
		getLocationOnScreen (locationScreen); 
		getLocalVisibleRect (visible); 
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
				} //else getParent ().requestDisallowInterceptTouchEvent (event.getPointerCount () < 2); 
			} 
			if (!mWriteDetector.isInPenMode ()) 
				getParent ().requestDisallowInterceptTouchEvent (event.getPointerCount () < 2); 
			mOtherGestureDetector.onTouchEvent (event); // For detecting clicks and long-clicks. 
			if (event.getAction () == MotionEvent.ACTION_UP || event.getAction () == MotionEvent.ACTION_CANCEL) 
				getParent ().requestDisallowInterceptTouchEvent (false); 
			return mWriteDetector.onTouchEvent (event); 
		} else return super.onTouchEvent (event); 
	} 
	
	Paint strokePaint = new Paint (); 
	Paint erasePaint = new Paint (); 
	
	float paperPoints [] = null; 
	Paint paperPaint = new Paint (); // For drawing graph paper, etc. 
	PaperGenerator paperGenerator = new PaperGenerator (); 
	
	Paint borderPaint = new Paint (); 
	
	int prevColor = 0; 
	int prevTool = 0; 
	
	DisplayMetrics metrics = new DisplayMetrics (); 
	
	int lastRenderX = 0; 
	int lastRenderY = 0; 
	int lastRenderW = 0; 
	int lastRenderH = 0; 
	
	Rect globalVisible = new Rect (); 
	Rect localVisible = new Rect (); 
	int viewLocation [] = new int [2]; 
	
	float getScaleFactor () {
		float scale = getScaleX ();
		ViewParent parent = getParent (); 
		while (parent instanceof View) { 
			scale *= ((View) parent).getScaleX (); 
			parent = parent.getParent (); 
		} 
		return scale; 
	} 
	
	Rect bmpSource = new Rect (); 
	RectF bmpDest = new RectF (); 
	
	static final int WIDE_SCALE_BUFFER_PARAMETER = 2; 
	
	void forceRedraw () { 
		lastRenderW = lastRenderH = 0; 
		computePageDrawPosition (); 
	} 
	
	void computePageDrawPosition () { 
		int w = getWidth (); 
		int h = mBitmapLoadHeight; 
		int vh = getHeight (); 
		if (w == 0 || h == 0) 
			return; // Do nothing; this view has likely not been laid out yet ... 
		getGlobalVisibleRect (globalVisible); 
		getLocalVisibleRect (localVisible); 
		getLocationOnScreen (viewLocation); 
		float totalScale = getScaleFactor (); 
		float bigW = (float) w * totalScale; 
		float bigH = (float) h * totalScale; 
		int renderX = -localVisible.left; 
		int renderY = -localVisible.top * h / vh; 
		int renderW = (int) bigW; 
		int renderH = (int) bigH; 
		if (renderX > 0) 
			renderX = 0; 
		else if (renderX < w - bigW) 
			renderX = (int) (w - bigW); 
		if (renderY > 0) 
			renderY = 0; 
		else if (renderY < h - bigH) 
			renderY = (int) (h - bigH); 
//		if (renderX != lastRenderX || renderY != lastRenderY || renderW != lastRenderW || renderH != lastRenderH) { 
			if (redrawRequestListener != null) { 
				redrawRequestListener.requestRedrawPagePDF (this, itemFile, itemPage, 
						renderX, renderY, renderW, renderH, WIDE_SCALE_BUFFER_PARAMETER, true); 
			} 
//		} 
	} 
	
	@Override public void onDraw (Canvas canvas) { 
		// Let the superclass draw the target image for us: 
		super.onDraw (canvas); 
		int w = getWidth (); 
		int h = lastRenderW != 0 ? w * lastRenderH / lastRenderW : getHeight (); 
		int vh = getHeight (); 
		float totalScale = getScaleFactor (); 
//		bmpDest.left = localVisible.left / totalScale; 
//		bmpDest.top = localVisible.top / totalScale; 
//		if (bmpDest.top < 0) 
//			bmpDest.top = 0; 
//		else if (bmpDest.top > h - smallH) 
//			bmpDest.top = h - smallH; 
//		bmpDest.right = smallW + bmpDest.left; 
//		bmpDest.bottom = smallH + bmpDest.top; 
		bmpSource.left = 0; 
		bmpSource.top = 0; 
		synchronized (mBackgroundBmpMutex) { 
			if (mBackgroundBitmap != null && lastRenderW > 0 && lastRenderH > 0) { 
				bmpSource.right = mBitmapNaturalWidth; 
				bmpSource.bottom = mBitmapNaturalWidth * h / w; 
				bmpDest.left = -lastRenderX * w / lastRenderW; 
				bmpDest.top = -lastRenderY * vh / lastRenderH; 
				bmpDest.right = bmpDest.left + w * w / lastRenderW; 
				bmpDest.bottom = bmpDest.top + vh * h / lastRenderH; 
//				bmpDest.left = localVisible.left / totalScale; 
//				bmpDest.top = localVisible.top / totalScale; 
//				bmpDest.right = bmpDest.left + w / totalScale; 
//				bmpDest.bottom = bmpDest.top + h / totalScale; 
//				canvas.save (); 
//				canvas.scale ((float) getWidth () / mBitmapNaturalWidth, (float) getWidth () / mBitmapNaturalWidth); 
//				canvas.drawRect (bmpDest, strokePaint); 
				canvas.drawBitmap (mBackgroundBitmap, bmpSource, bmpDest, null); 
//				canvas.restore (); 
			} 
		} 
		// If the target image is a small version, use it: 
		if (paperPoints != null) { 
			paperGenerator.setupGraphPaperPaint (getWidth (), paperPaint); 
			canvas.drawLines (paperPoints, paperPaint); 
		} else { 
				if (isAnnotatedPage) { 
					int background = 0; 
					synchronized (edit) {
						if (edit.value != null) background = edit.value.srcPageBackground;
					} 
					switch (background) {
						case 1:
							paperGenerator.drawGraphPaper (canvas, paperPaint);
							break;
					} 
				} 
		} 
		// Check if the previous tool and color are different, and if so then 
		// it's been a while, and we need to clear our temporary path: 
		if (prevColor != mColor || prevTool != mTool) { 
			tmpPointCount = 0; 
			prevColor = mColor; 
			prevTool = mTool; 
		} 
		// Now draw our annotation edits that the user made: 
		float minSpan = optimization_minStrokeSpan * getWidth (); 
		if (edit.value != null) synchronized (edit) { 
			float brushScale = (float) getWidth () / edit.value.windowWidth; 
			if (minSpan != 0) { 
				for (PngEdit.LittleEdit e : edit.value.mEdits) { 
					if (e.points.length < 8) continue; 
					float x0 = e.points[0]; 
					float x1 = e.points[e.points.length / 2 + 0]; 
					float x2 = e.points[e.points.length - 2]; 
					float xMin = Math.min (x0, Math.min (x1, x2)); 
					float xMax = Math.max (x0, Math.max (x1, x2)); 
					float xSpan = xMax - xMin; 
					boolean dontSkip = xSpan >= minSpan; 
					if (!dontSkip) { 
						float y0 = e.points[1]; 
						float y1 = e.points[e.points.length / 2 + 1]; 
						float y2 = e.points[e.points.length - 1]; 
						float yMin = Math.min (y0, Math.min (y1, y2)); 
						float yMax = Math.max (y0, Math.max (y1, y2)); 
						float ySpan = yMax - yMin; 
						dontSkip = ySpan >= minSpan; 
						if (!dontSkip) { 
							dontSkip = Math.sqrt (xSpan * xSpan + ySpan * ySpan) >= minSpan; 
						} 
					} 
					if (!dontSkip) continue; // Skip drawing this stroke. 
					strokePaint.setColor (e.color); 
					float strokeWidth = e.brushWidth * brushScale; 
					if (strokeWidth < 1f) strokeWidth = 0f; // Thinnest possible. 
					strokePaint.setStrokeWidth (strokeWidth); 
					canvas.drawLines (e.points, strokePaint); 
				} 
			} else 
				for (PngEdit.Cache.Entry entry : strokeCache.mList) {
					strokePaint.setColor (entry.color); 
					float strokeWidth = entry.brushWidth * brushScale; 
					if (strokeWidth < 1f) strokeWidth = 0f; // Thinnest possible. 
					strokePaint.setStrokeWidth (strokeWidth); 
					canvas.drawLines (entry.points, 0, entry.szPoints, strokePaint); 
				} 
		} 
		// Finally, draw the currently being written path: 
		strokePaint.setColor (mNowErasing || mNowWhiting ? getContext ().getResources () 
													.getColor (R.color.colorEraser) : mColor); 
		float strokeWidth = mBrush * paperGenerator.getBrushScale (getWidth ()); 
		if (strokeWidth < 1f) 
			strokeWidth = 0f; 
		strokePaint.setStrokeWidth (strokeWidth); // Just cap this to 1+, for simple one-liner code. 
		canvas.drawLines (tmpPoints, 0, tmpPointCount, strokePaint); 
		// Draw a border around this view: 
		borderPaint.setStrokeWidth (2 * borderWidth); 
		canvas.drawRect (0, 0, getWidth (), getHeight (), borderPaint); 
		// Tint color: 
		if (tintColor != Color.TRANSPARENT) { 
			canvas.drawColor (tintColor); 
		} 
	} 
	
} 
