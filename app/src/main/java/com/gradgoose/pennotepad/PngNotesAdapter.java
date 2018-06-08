package com.gradgoose.pennotepad;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PngNotesAdapter extends RecyclerView.Adapter { 
	static final String TAG = "PngNotesAdapter"; 
	
	final Context mContext; 
	final Vector<File> mBrowsingFolder; 
	final HashMap<String, Long> mStableIds; 
	
	boolean mPenMode = false; 
	boolean mToolMode = false; 
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 3.0f; 
	
	int mViewMode = PageView.VIEW_LARGE; 
	
	PageView.ErrorCallback mErrorCallback = null; 
	
	boolean mActivityRunning = true; 
	
	boolean mAllowLinks = true; 
	
	OnNoteInteractListener mOnNoteInteractListener = null; 
	
	long headerPersistantIdStart = 0; 
	View mHeaderItemViews [] = null; 
	
	FileListCache mCache = null; 
	File mList [] = new File [0]; 
	
	boolean mIsPDF = false; 
	
	final PdfiumCore pdfiumCore; 
	PdfDocument pdfDocument = null; 
	int mPdfPageCount = 0; 
	
	RecyclerView mAttachedRecyclerView = null; 
	@Override public void onAttachedToRecyclerView (RecyclerView recyclerView) { 
		super.onAttachedToRecyclerView (recyclerView); 
		mAttachedRecyclerView = recyclerView; 
	} 
	
	void setViewMode (int mode) { 
		mViewMode = mode; 
	} 
	
	void setNoteInteractListener (OnNoteInteractListener listener) { 
		mOnNoteInteractListener = listener; 
	} 
	public interface OnNoteInteractListener { 
		void onNotePageClicked (File itemFile, int listPosition); 
		boolean onNotePageLongClicked (File itemFile, int listPosition); 
		boolean onPageLinkClicked (int pageIndex); 
		boolean onUriLinkClicked (String linkURI); 
	} 
	
	public void setHeaderItemViews (View list []) { 
		mHeaderItemViews = list; 
		long maximum = 1; 
		for (Long l : mStableIds.values ()) 
			if (l > maximum) 
				maximum = l; 
		headerPersistantIdStart = maximum + list.length; 
		mStableIds.put ("header", headerPersistantIdStart); 
		notifyDataSetChanged (); 
	} 
	
	@Nullable static File getThumbnailFile (Context context, File targetFile) { 
		File cacheDir = context.getCacheDir (); 
		File thumbnailDir = new File (cacheDir, "Thumbnails"); 
		if (!thumbnailDir.exists () && !thumbnailDir.mkdirs ()) return null; 
		String fullFileName; 
		try { 
			fullFileName = PngEdit.getFullFileName (context, targetFile, ".png", 1); 
		} catch (IOException err) { 
			return null; 
		} 
		return new File (thumbnailDir, fullFileName); 
	} 
	@Nullable static File getTileFile (Context context, File targetFile) { 
		File cacheDir = context.getCacheDir (); 
		File tileDir = new File (cacheDir, "Tiles"); 
		if (!tileDir.exists () && !tileDir.mkdirs ()) return null; 
		String fullFileName; 
		try { 
			fullFileName = PngEdit.getFullFileName (context, targetFile, ".png", 1); 
		} catch (IOException err) { 
			return null; 
		} 
		return new File (tileDir, fullFileName); 
	} 
	static File [] getFlattenedList (File list [] []) { 
		int total = 0; 
		for (File l [] : list) 
			total += l.length; 
		File list2 [] = new File [total]; 
		int c = 0; 
		for (File [] possible : list) { 
			for (File file : possible) { 
				list2[c] = file; 
				c++; 
			} 
		} 
		return list2; 
	} 
	
	FileListCache.OnFilesChangedListener mOnFilesChangedListener = null; 
	
	public File [] prepareFileList () { 
		File list [] [] = mCache.asyncListFiles (mFilterJustImages, 
				new FileListCache.OnFilesChangedListener () { 
					@Override public void onFilesChanged (File [][] list) { 
						mList = getFlattenedList (list); 
						// Callback: 
						if (mOnFilesChangedListener != null) 
							mOnFilesChangedListener.onFilesChanged (list); 
//						updateCache (); 
						// Update views: 
						notifyDataSetChanged (); 
					} 
					@Override public void onFilesNoChange (File [] [] list) { 
						// Callback: 
						if (mOnFilesChangedListener != null) 
							mOnFilesChangedListener.onFilesNoChange (list); 
//						updateCache (); 
					} 
				}); 
		return mList = getFlattenedList (list); 
	} 
	public void preparePageList () { 
		if (pdfDocument != null) { 
			pdfiumCore.closeDocument (pdfDocument); 
			pdfDocument = null; 
		} 
		ParcelFileDescriptor fd = null; 
		try { 
			fd = ParcelFileDescriptor.open (mBrowsingFolder.elementAt (0), ParcelFileDescriptor.MODE_READ_ONLY); 
		} catch (FileNotFoundException err) { 
			Log.e (TAG, "Error finding PDF document. " + err.toString ()); 
		} 
		if (fd != null) try { 
			pdfDocument = pdfiumCore.newDocument (fd); 
			mPdfPageCount = pdfiumCore.getPageCount (pdfDocument); 
		} catch (IOException err) { 
			Log.e (TAG, "Error opening PDF document. " + err.toString ()); 
		} 
	} 
	
	public void reloadList () { 
		if (mIsPDF) 
			preparePageList (); 
		else prepareFileList (); 
		loadIds (); 
		notifyDataSetChanged (); 
	} 
	
	private File getItemFile (int position) { 
		int headerCount = mHeaderItemViews != null ? mHeaderItemViews.length : 0; 
		return position < headerCount ? 
					   null : 
					   (mIsPDF ? mBrowsingFolder.elementAt (0) : (position - headerCount < mList.length ? 
						mList[position - headerCount] : null)); 
	} 
	
	private void loadIds (File list []) { 
		long maximum = 1; 
		for (Long l : mStableIds.values ()) 
			if (l > maximum) 
				maximum = l; 
		if (mIsPDF) { 
			String pdfPath = mBrowsingFolder.elementAt (0).getAbsolutePath (); 
			for (int position = 0; position < mPdfPageCount; position++) { 
				int pageNumber = mIsPDF ? position - countHeaderViews () + 1 : 1; 
				String path = pdfPath + ":" + pageNumber; 
				mStableIds.put (path, ++maximum); 
			} 
		} else 
		for (File file : list) 
			if (!mStableIds.containsKey (file.getAbsolutePath ())) 
				mStableIds.put (file.getAbsolutePath (), ++maximum); 
	} 
	private void loadIds () { 
		if (mIsPDF) 
			preparePageList (); 
		else 
			prepareFileList (); 
		loadIds (mList); 
	} 
	
	static boolean hasImages (Vector<File> folders) { 
		for (File folder : folders) 
			if (hasImages (folder)) 
				return true; 
		return false; 
	} 
	static boolean hasImages (File folder) { 
		File list [] = folder.listFiles (); 
		for (File file : list) 
			if (mFilterJustImages.accept (file)) 
				return true; 
		return false; 
	} 
	boolean hasImages () { 
		return mList.length > 0; 
	} 
	static FileFilter mFilterJustImages = new FileFilter () { 
		@Override public boolean accept (File file) { 
			String lowerName = file.getName ().toLowerCase (); 
			return file.isFile () && ( 
					lowerName.endsWith (".png") || 
							lowerName.endsWith (".jpg") || 
							lowerName.endsWith (".jpeg") || 
							lowerName.endsWith (".gif") || 
							lowerName.endsWith (".apg") // Edits/notes file 
			); 
		} 
	}; 
	
	public PngNotesAdapter (Context context, Vector<File> browsingDir, 
							FileListCache.OnFilesChangedListener onFilesChangedListener) { 
		super (); 
		mContext = context; 
		mBrowsingFolder = browsingDir; 
		mOnFilesChangedListener = onFilesChangedListener; 
		mIsPDF = browsingDir.elementAt (0).getName ().toLowerCase ().endsWith (".pdf"); 
		if (mIsPDF) { 
			pdfiumCore = new PdfiumCore (context); 
			preparePageList (); 
		} else { 
			pdfiumCore = null; 
			mCache = new FileListCache (browsingDir, context.getFilesDir ()); 
			prepareFileList (); 
		} 
		mStableIds = new HashMap<> (mList.length); 
		loadIds (mList); 
		notifyDataSetChanged (); 
		setHasStableIds (true); 
		mRedrawCode.start (); 
	} 
	
	Vector<PageView> mAllPageViews = new Vector<> (); 
	final Vector<RedrawParams> mAllRedrawParams = new Vector<> (); 
	
	// Thread checks for dirty RedrawParams, and redraws them: 
	private Thread mRedrawCode = new Thread () { 
		@Override public void run () { 
			boolean hasDirty; 
			while (mActivityRunning) { 
				hasDirty = false; 
				int cnt = mAllRedrawParams.size (); 
				for (int i = 0; i < cnt; i++) { 
					final RedrawParams params = mAllRedrawParams.elementAt (i); 
					if (!params.dirty) continue; 
					if (params.pageView.getWidth () == 0 || params.pageView.getHeight () == 0) 
						continue; 
					hasDirty = true; 
					// Go redraw it: 
					params.dirty = false; 
					if (mIsPDF) { 
						params.holder.renderPage (params.page, 
								params.putX, params.putY, params.putWidth, params.putHeight, 
								params.wideScaleParameter, params.skipDrawingIfPutParametersTheSame); 
					} else if (!params.pageView.isAnnotatedPage && mContext instanceof Activity && 
																	   (Build.VERSION.SDK_INT < 17 || !((Activity) mContext).isDestroyed ())) { 
						if (params.pageView.itemFile != null && 
								params.pageView.itemFile.equals (params.file) && 
								params.pageView.hasGlideImage) 
							continue; 
						RequestBuilder builder = Glide.with (mContext) 
														 .asBitmap () 
														 .load (params.file) 
														 .apply (RequestOptions.skipMemoryCacheOf (true)) 
														 .apply (RequestOptions.diskCacheStrategyOf (DiskCacheStrategy.RESOURCE)) 
														 .apply (RequestOptions.sizeMultiplierOf (params.pageView.viewMode == PageView.VIEW_SMALL ? 
																										  params.pageView.GLIDE_SMALL_SIZE_MULT : 
																										  params.pageView.GLIDE_LARGE_SIZE_MULT)) 
														 .listener (new RequestListener<Bitmap> () { 
															 @Override public boolean onLoadFailed (@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) { 
																 if (mErrorCallback != null) 
																	 mErrorCallback.onBitmapLoadError (); 
																 return false; 
															 } 
															 @Override public boolean onResourceReady (Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) { 
																 return false; 
															 } 
														 }); 
						if (params.pageView.viewMode == PageView.VIEW_LARGE) 
							builder.thumbnail (PageView.THUMBNAIL_MULTIPLIER); 
						params.clearTarget (); 
						final FutureTarget target = builder.submit (params.pageView.getWidth (), params.pageView.getHeight ()); 
						final Object obj; 
						try { 
							obj = target.get (); 
						} catch (Throwable err) { 
							Log.e (TAG, "Error loading Glide target. "); 
							err.printStackTrace (); 
							continue; 
						} 
						if (!(obj instanceof Bitmap)) 
							continue; 
						((Activity) params.pageView.getContext ()).runOnUiThread (new Runnable () { 
							@Override public void run () { 
								params.pageView.setImageBitmap ((Bitmap) obj); 
								params.target = target; 
								params.pageView.hasGlideImage = true; 
							} 
						}); 
					} 
				} 
				if (!hasDirty) { 
					try { sleep (32); } catch (InterruptedException err) { err.printStackTrace (); } 
				} 
			} 
		} 
	}; 
	
	public class Plain extends RecyclerView.ViewHolder { 
		public Plain (View root) { 
			super (root); 
		} 
	} 
	public static class RedrawParams { 
		Holder holder; 
		FutureTarget target = null; 
		PageView pageView; 
		File file; 
		int page; 
		int putX; 
		int putY; 
		int putWidth; 
		int putHeight; 
		int wideScaleParameter; 
		boolean skipDrawingIfPutParametersTheSame; 
		boolean dirty = false; 
		RedrawParams (Holder holder) { this.holder = holder; } 
		synchronized void clearTarget () { 
			FutureTarget t = target; 
			target = null; 
			if (t != null) { 
				((Activity) holder.pageView.getContext ()).runOnUiThread (new Runnable () { 
					@Override public void run () { 
						holder.pageView.setImageBitmap (null); 
					} 
				}); 
				Glide.with (holder.pageView.getContext ()) 
						.clear (t); 
			} 
		} 
	} 
	public class Holder extends RecyclerView.ViewHolder { 
		final PageView pageView; 
		final TextView titleView; 
		final TextView topRightView; 
		final View tileContainer; 
		final View.OnClickListener onClickListener = new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (mAllowLinks && clickLink (pageView.itemPage, 
						pageView.getWidth (), pageView.getHeight (), 
						pageView.lastTouchedX, pageView.lastTouchedY)) { 
					Log.i (TAG, "clickLink () returned 'true';"); 
					return; 
				} 
				if (mOnNoteInteractListener != null) mOnNoteInteractListener.onNotePageClicked (mItemFile, mListPosition); 
			} 
		}; 
		final View.OnLongClickListener onLongClickListener = new View.OnLongClickListener () { 
			@Override public boolean onLongClick (View view) { 
				return mOnNoteInteractListener != null && mOnNoteInteractListener.onNotePageLongClicked (mItemFile, mListPosition); 
			} 
		}; 
		File mItemFile; 
		int mListPosition; 
		final RedrawParams mRedrawParams = new RedrawParams (this); 
		PageView.RequestRedraw mRedrawListener = new PageView.RequestRedraw () { 
			@Override public void requestRedrawPagePDF (final PageView pageView, final File file, final int page, 
														final int putX, final int putY, final int putWidth, final int putHeight, 
														final int wideScaleParameter,
														final boolean skipDrawingIfPutParametersTheSame) { 
				mRedrawParams.pageView = pageView; 
				mRedrawParams.file = file; 
				mRedrawParams.page = page; 
				mRedrawParams.putX = putX; 
				mRedrawParams.putY = putY; 
				mRedrawParams.putWidth = putWidth; 
				mRedrawParams.putHeight = putHeight; 
				mRedrawParams.wideScaleParameter = wideScaleParameter; 
				mRedrawParams.skipDrawingIfPutParametersTheSame = skipDrawingIfPutParametersTheSame; 
				mRedrawParams.dirty = true; 
//				new Thread () { 
//					@Override public void run () { 
//						renderPage (page, putX, putY, putWidth, putHeight, wideScaleParameter, skipDrawingIfPutParametersTheSame); 
//					} 
//				}.start (); 
			} 
			@Override public void requestRedrawImage (File imageFile, PageView pageView) {
				mRedrawParams.pageView = pageView; 
				mRedrawParams.file = imageFile; 
				mRedrawParams.dirty = true; 
			} 
		}; 
		public Holder (View root) { 
			super (root); 
			pageView = root.findViewById (R.id.pvBigPage); 
			titleView = root.findViewById (R.id.tvPageTitle); 
			topRightView = root.findViewById (R.id.tvTopRightCornerText); 
			tileContainer = root.findViewById (R.id.flPageTile); 
			mAllPageViews.add (pageView); 
			synchronized (mAllRedrawParams) { 
				mAllRedrawParams.add (mRedrawParams); 
			} 
		} 
		public void bind (File itemFile, int positionInList) { 
			final int pageIndex = mIsPDF ? positionInList - countHeaderViews () : 1; 
			String pageNumberLabel = mIsPDF ? String.format (Locale.US, mContext.getString (R.string.label_pg_of), pageIndex + 1, mPdfPageCount) : ""; 
			titleView.setText (itemFile.getName ()); 
			topRightView.setText (pageNumberLabel); 
			topRightView.setVisibility (pageNumberLabel.isEmpty () ? View.GONE : View.VISIBLE); 
			mItemFile = itemFile; 
			mListPosition = positionInList; 
			pageView.setOnClickListener (onClickListener); 
			pageView.setOnLongClickListener (onLongClickListener); 
//			tileContainer.setBackgroundResource (mUsePictureFrameBackground ? android.R.drawable.picture_frame : 0); 
//			if (!mUsePictureFrameBackground) tileContainer.setPadding (0, 0, 0, 0); 
			pageView.mErrorCallback = mErrorCallback; 
			pageView.viewMode = mViewMode; 
//			renderPage (pageIndex, 0, 0, 0, 0, 1, false); 
			pageView.lastRenderW = pageView.lastRenderH = 0; 
			pageView.computePageDrawPosition (); // It'll request PDF redraw by itself if it needs to. 
			pageView.redrawRequestListener = mRedrawListener; 
			pageView.setItemFile (itemFile, pageIndex); 
			pageView.setPenMode (mPenMode); 
			pageView.setToolMode (mToolMode); 
			pageView.mTool = mTool; 
			pageView.mColor = mColor; 
			pageView.mBrush = mBrush; 
			if (mIsPDF) pageView.mSizeChangeCallback = new PageView.SizeChanged () { 
				@Override public void onSizeChanged () { 
//					renderPage (pageIndex, 0, 0, 0, 0, 1, true); 
					pageView.computePageDrawPosition (); // It'll request PDF redraw if a redraw is necessary. 
				} 
			}; 
			else pageView.mSizeChangeCallback = null; 
		} 
		int downscalePutX (int putX, int targetWidth, int wideScaleParameter) { 
			return (putX - targetWidth / 2) / wideScaleParameter + targetWidth / 2; 
		} 
		int downscalePutY (int putY, int targetHeight, int wideScaleParameter) { 
			return (putY - targetHeight / 2) / wideScaleParameter + targetHeight / 2; 
		} 
		synchronized boolean clickLink (int pageIndex, int viewWidth, int viewHeight, float touchX, float touchY) { 
			if (mIsPDF && pdfDocument != null) synchronized (pdfiumCore) { 
				pdfiumCore.openPage (pdfDocument, pageIndex); 
				List<PdfDocument.Link> links = pdfiumCore.getPageLinks (pdfDocument, pageIndex); 
				Log.i (TAG, "Clicked at: [" + touchX + ", " + touchY + "]"); 
				for (PdfDocument.Link link : links) { 
					RectF bounds = link.getBounds (); 
					RectF mapped = pdfiumCore.mapRectToDevice (pdfDocument, pageIndex, 
							0, 0, viewWidth, viewHeight, 
							0, 
							bounds); 
					Log.i (TAG, "Link: [" + bounds.toString () + "] -> [" + mapped.toString () + "]"); 
					if (mapped.contains (touchX, touchY)) { 
						Log.i (TAG, "This is the link!"); 
						Integer destPageIdx = link.getDestPageIdx (); 
						if (destPageIdx != null) { 
							if (mOnNoteInteractListener != null) 
								return mOnNoteInteractListener.onPageLinkClicked (destPageIdx); 
						} else { 
							if (mOnNoteInteractListener != null) 
								return mOnNoteInteractListener.onUriLinkClicked (link.getUri ()); 
						} 
						return false; 
					} 
				} 
			} 
			return false; 
		} 
		synchronized void renderPage (int pageIndex, int putX, int putY, int putWidth, int putHeight, 
									  int wideScaleParameter, boolean skipRenderIfSamePutParams) { 
			if (mIsPDF && pdfDocument != null) { 
				synchronized (pdfiumCore) { 
					pdfiumCore.openPage (pdfDocument, pageIndex); 
					int rvW = 1; 
					int rvH = 1; 
					if (mAttachedRecyclerView != null) { 
						rvW = mAttachedRecyclerView.getWidth (); 
						rvH = mAttachedRecyclerView.getHeight (); 
					} 
					int srcWidth = pdfiumCore.getPageWidth (pdfDocument, pageIndex); 
					int srcHeight = pdfiumCore.getPageHeight (pdfDocument, pageIndex); 
					int targetWidth = pageView.getWidth (); 
					int targetHeight = targetWidth * srcHeight / srcWidth; 
					int loadWidth = targetWidth == 0 ? srcWidth : Math.min (srcWidth, targetWidth); 
					int naturalHeight = targetHeight == 0 ? srcHeight : Math.min (srcHeight, targetHeight); 
					int otherHeight = loadWidth * rvH / rvW; 
					int loadHeight = Math.max (naturalHeight, Math.min (otherHeight, naturalHeight * 2)); // The min () is there to prevent a memory-intensive thing  
					// in the case that it's a PDF with lots of pages whose height << their width. So this serves as a memory cap, sort of, 
					// limiting usage to twice the size of the screen. 
					pageView.mBitmapNaturalWidth = loadWidth; 
					pageView.mBitmapNaturalHeight = naturalHeight; 
					pageView.mBitmapLoadHeight = loadHeight; 
					if (putWidth == 0) putWidth = loadWidth; 
					if (putHeight == 0) putHeight = loadHeight; 
					int needSeeX = putX, needSeeY = putY, needSeeW = putWidth, needSeeH = putHeight; 
					if (wideScaleParameter > 1) { 
						if (putWidth / wideScaleParameter >= loadWidth) { 
//						putX = Math.min (0, putX - putWidth * (wideScaleParameter - 1) / 2); 
							putX = downscalePutX (putX, targetWidth, wideScaleParameter); 
//						putX /= wideScaleParameter; 
							putWidth /= wideScaleParameter; 
						} else { 
							putX = 0; 
							putWidth = loadWidth; 
						} 
						if (putHeight / wideScaleParameter >= loadHeight) { 
//						putY = Math.min (0, putY - putHeight * (wideScaleParameter - 1) / 2); 
							putY = downscalePutY (putY, targetHeight, wideScaleParameter); 
//						putY /= wideScaleParameter; 
							putHeight /= wideScaleParameter; 
						} else { 
							putY = 0; 
							putHeight = naturalHeight; 
						} 
					} else if (wideScaleParameter == 0) { 
						// We put this condition here just so we have an easy way of changing back to regular render mode (just set parameter = 0) ... 
						putX = putY = 0; 
						putWidth = loadWidth; 
						putHeight = naturalHeight; 
					} 
					if (skipRenderIfSamePutParams && pageView.mBackgroundBitmap != null) { 
						if (putWidth == pageView.lastRenderW && 
									putHeight == pageView.lastRenderH) { 
							if (putX == pageView.lastRenderX && 
										putY == pageView.lastRenderY) 
								return; 
							if (Math.abs (putX - pageView.lastRenderX) < targetWidth * (wideScaleParameter - 1) / (2 * wideScaleParameter) && 
										Math.abs (putY - pageView.lastRenderY) < targetHeight * (wideScaleParameter - 1) / (2 * wideScaleParameter)) 
								return; 
						} 
					} 
					putX = Math.min (0, putX); 
					putY = Math.min (0, putY); 
					if (targetWidth != 0) { 
						Log.i (TAG, "Rendering area {" + putX + ", " + putY + ", " + putWidth + ", " + putHeight + "}"); 
						pageView.lastRenderX = putX; 
						pageView.lastRenderY = putY; 
						pageView.lastRenderW = putWidth; 
						pageView.lastRenderH = putHeight; 
						Bitmap bmp = null; 
						synchronized (pageView.mBackgroundBmpMutex) { 
							if (pageView.mBackgroundBitmap != null) { 
								if (pageView.mBackgroundBitmap.getWidth () == loadWidth && pageView.mBackgroundBitmap.getHeight () == loadHeight) { 
									bmp = pageView.mBackgroundBitmap; 
									pageView.mBackgroundBitmap = null; // So that it's not drawn while we modify it. 
									new Canvas (bmp).drawColor (Color.TRANSPARENT); 
								} else { 
									pageView.mBackgroundBitmap.recycle (); 
									pageView.mBackgroundBitmap = null; 
								} 
							} 
						} 
						if (bmp == null) { 
							try { 
								bmp = Bitmap.createBitmap (loadWidth, loadHeight, Bitmap.Config.RGB_565); 
							} catch (OutOfMemoryError err) { 
								if (mErrorCallback != null) 
									mErrorCallback.onBitmapOutOfMemory (); 
							} 
						} 
						if (bmp != null) { 
							pdfiumCore.renderPageBitmap (pdfDocument, bmp, pageIndex, putX, putY, putWidth, putHeight); 
							pageView.mBackgroundBitmap = bmp; 
							pageView.mBitmapNaturalWidth = loadWidth; 
							pageView.mBitmapNaturalHeight = naturalHeight; 
							pageView.mBitmapLoadHeight = loadHeight; 
						} 
					} else { 
						pageView.lastRenderX = 0; 
						pageView.lastRenderY = 0; 
						pageView.lastRenderW = 0; 
						pageView.lastRenderH = 0; 
					} 
				} 
			} else { 
				if (pageView.mBackgroundBitmap != null) { 
					pageView.mBackgroundBitmap.recycle (); 
					pageView.mBackgroundBitmap = null; 
				} 
			} 
			((Activity) mContext).runOnUiThread (new Runnable () { 
				@Override public void run () { 
					if (pageView.getWidth () * pageView.lastRenderH != pageView.lastRenderW * pageView.getHeight ()) 
						pageView.requestLayout (); // Redo layout if the view's aspect ratio is different from the render's. 
					pageView.invalidate (); 
				} 
			}); 
		} 
	} 
	
	void cleanUp () { 
//		for (PageView pageView : mAllPageViews) 
//			pageView.setImageBitmap (null); // This will recycle the previous bitmap. 
		if (pdfDocument != null) { 
			pdfiumCore.closeDocument (pdfDocument); 
			pdfDocument = null; 
		} 
		for (Holder h : mHolders) { 
			h.pageView.cleanUp (); 
		} 
	} 
	
	void refreshViews () { 
		for (Holder h : mHolders) 
			h.pageView.computePageDrawPosition (); 
	} 
	void forceRedrawAll () { 
		for (Holder h : mHolders) 
			h.pageView.forceRedraw (); 
	} 
	
	Vector<Holder> mHolders = new Vector<> (); 
	
	@Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) { 
		// If it's of a header view type, just return a plain holder with the header view: 
		if (viewType >= 100) 
			return new Plain (mHeaderItemViews[viewType - 100]); 
		// Otherwise, it's just a list item type; return a regular list holder of ours: 
		View itemView = LayoutInflater.from (mContext).inflate (R.layout.big_page, parent, false); 
		Holder h = new Holder (itemView); 
		mHolders.add (h); 
		return h; 
	}
	
	@Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof Holder) 
			((Holder) holder).bind (getItemFile (position), position); 
	} 
	
	@Override public int getItemViewType (int position) { 
		int headerCount = mHeaderItemViews != null ? mHeaderItemViews.length : 0; 
		return position < headerCount ? 
					   (position + 100) // Just use 100 and on for header view types. 
					   : 1; 
	} 
	
	@Override
	public long getItemId (int position) { 
		File itemFile = getItemFile (position); 
		if (itemFile != null) { 
			String path; 
			if (mIsPDF) { 
				int pageNumber = position - countHeaderViews () + 1; 
				path = itemFile.getAbsolutePath () + ":" + pageNumber; 
			} else path = itemFile.getAbsolutePath (); 
			if (mStableIds.containsKey (path)) 
				return mStableIds.get (path); 
		} 
		return super.getItemId (position);
	}
	
	@Override
	public int getItemCount () { 
		return (mIsPDF ? mPdfPageCount : (mList != null ? mList.length : 0)) + 
					   (mHeaderItemViews != null ? mHeaderItemViews.length : 0); 
	} 
	
	public int countHeaderViews () { 
		return mHeaderItemViews != null ? mHeaderItemViews.length : 0; 
	} 
	public int countImages () { 
		return mList != null ? mList.length : 0; 
	} 
	
} 
