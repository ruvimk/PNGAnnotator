package com.gradgoose.pngannotator;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
	
	boolean mUsePictureFrameBackground = true; 
	
	int mSampleMode = PageView.SAMPLE_NORMAL; 
	int mLoadMode = PageView.LOAD_ORIGINAL; 
	int mPreviewMode = PageView.PREVIEW_THUMBNAIL; 
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 3.0f; 
	
	PageView.ErrorCallback mErrorCallback = null; 
	
	OnNoteInteractListener mOnNoteInteractListener = null; 
	
	long headerPersistantIdStart = 0; 
	View mHeaderItemViews [] = null; 
	
	FileListCache mCache = null; 
	File mList [] = new File [0]; 
	
	boolean mIsPDF = false; 
	
	PdfiumCore pdfiumCore = null; 
	PdfDocument pdfDocument = null; 
	int mPdfPageCount = 0; 
	
	void setSampleMode (int sampleMode) { 
		mSampleMode = sampleMode; 
	} 
	void setLoadMode (int loadMode) { 
		mLoadMode = loadMode; 
	} 
	void setPreviewMode (int previewMode) { 
		mPreviewMode = previewMode; 
	} 
	
	void setNoteInteractListener (OnNoteInteractListener listener) { 
		mOnNoteInteractListener = listener; 
	} 
	public interface OnNoteInteractListener { 
		void onNotePageClicked (File itemFile, int listPosition); 
		boolean onNotePageLongClicked (File itemFile, int listPosition); 
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
	static class UpdateCache extends AsyncTask<File [], Void, Integer> { 
		Context mContext; 
		CacheUpdatedListener mOnUpdate = null; 
		int mWhichDir = 0; 
		public UpdateCache (Context context, int whichDir) { 
			mContext = context; 
			mWhichDir = whichDir; 
		} 
		void close () { 
			mContext = null; 
			mOnUpdate = null; 
		} 
		@Override protected Integer doInBackground (File [] ... files) {
			int windowWidth = 1024; 
			int windowHeight = 1024; 
			int changedCount = 0; 
			if (mContext instanceof Activity) { 
				DisplayMetrics displayMetrics = new DisplayMetrics (); 
				((Activity) mContext).getWindowManager () 
						.getDefaultDisplay () 
						.getMetrics (displayMetrics); 
				windowWidth = displayMetrics.widthPixels; 
				windowHeight = displayMetrics.heightPixels; 
			} 
			for (File [] list : files) {
				for (File picture : list) {
					String path = picture.getPath (); 
					if (path.endsWith (".apg")) continue; // Skip, since this is not a bitmap. 
					// Get the file where to save the thumbnail: 
					File thumbnail = mWhichDir == 1 ? getTileFile (mContext, picture) : getThumbnailFile (mContext, picture); 
					if (thumbnail == null) continue;
					// If the picture didn't change, skip updating the thumbnail: 
					if (thumbnail.exists () &&
								thumbnail.lastModified () > picture.lastModified ())
						continue;
					// Load just the image dimensions first: 
					final BitmapFactory.Options options = new BitmapFactory.Options ();
					options.inJustDecodeBounds = true;
					BitmapFactory.decodeFile (picture.getPath (), options);
					// Load a REALLY small version for time time being, while it's loading 
					// (this is to avoid white blanks and confusing the user by showing 
					// them some random picture that they have just seen from a 
					// recycled view): 
					options.inJustDecodeBounds = false;
					// For a tile, load a bigger bitmap first. 
					options.inSampleSize = PageView.calculateInSampleSize (options.outWidth, 
							options.outHeight, 1024, 1024); 
					// Load a small version of the picture into a bitmap: 
					Bitmap big = BitmapFactory.decodeFile (picture.getPath (), options); 
					if (big == null) continue; // Picture could have changed in the meantime ... 
					// We're loading tiles ... 
					int needSize = mWhichDir == 1 ? Math.min (windowWidth / 2, windowHeight / 2) 
										   : PageView.THUMBNAIL_SIZE; 
					Bitmap bmp = Bitmap.createScaledBitmap (big, needSize, needSize, true); 
					if (bmp != big) 
						big.recycle (); 
					// Save the small bitmap to a PNG thumbnail: 
					try {
						FileOutputStream fos = new FileOutputStream (thumbnail, false);
						bmp.compress (Bitmap.CompressFormat.PNG, 100, fos);
						fos.close (); 
						changedCount++; 
					} catch (IOException e) {
						e.printStackTrace (); 
					}
					bmp.recycle ();
				} 
			} 
			return changedCount; 
		} 
		@Override public void onCancelled () { 
			close (); 
		} 
		@Override public void onPostExecute (Integer result) { 
			if (result > 0) { 
				if (mOnUpdate != null) { 
					if (mWhichDir == 0) mOnUpdate.onThumbnailCacheUpdated (); 
					else if (mWhichDir == 1) mOnUpdate.onTileCacheUpdated (); 
				} 
			} 
			close (); 
		} 
	} 
	private void updateThumbnailCache () {
		UpdateCache updateCache = new UpdateCache (mContext, 0); 
		updateCache.mOnUpdate = mOnCacheUpdatedListener; 
		updateCache.execute (mList); 
	} 
	private void updateTileCache () { 
		UpdateCache updateCache = new UpdateCache (mContext, 1); 
		updateCache.mOnUpdate = mOnCacheUpdatedListener; 
		updateCache.execute (mList); 
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
	
	public interface CacheUpdatedListener { 
		void onThumbnailCacheUpdated (); 
		void onTileCacheUpdated (); 
	} 
	
	FileListCache.OnFilesChangedListener mOnFilesChangedListener = null; 
	CacheUpdatedListener mOnCacheUpdatedListener = null; 
	void setOnCacheUpdatedListener (CacheUpdatedListener listener) { 
		mOnCacheUpdatedListener = listener; 
	} 
	
	public File [] prepareFileList () { 
		File list [] [] = mCache.asyncListFiles (mFilterJustImages, 
				new FileListCache.OnFilesChangedListener () { 
					private void updateCache () { 
						// Update thumbnails: 
						updateThumbnailCache (); 
						// Update tiles for grid view mode: 
						updateTileCache (); 
					} 
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
			mCache = new FileListCache (browsingDir, context.getFilesDir ()); 
			prepareFileList (); 
		} 
		mStableIds = new HashMap<> (mList.length); 
		loadIds (mList); 
		notifyDataSetChanged (); 
		setHasStableIds (true); 
	} 
	
	Vector<PageView> mAllPageViews = new Vector<> (); 
	
	public class Plain extends RecyclerView.ViewHolder { 
		public Plain (View root) { 
			super (root); 
		} 
	} 
	public class Holder extends RecyclerView.ViewHolder { 
		final PageView pageView; 
		final TextView titleView; 
		final TextView topRightView; 
		final View tileContainer; 
		final View horizontalRule; 
		final View verticalRule; 
		final View.OnClickListener onClickListener = new View.OnClickListener () { 
			@Override public void onClick (View view) { 
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
		public Holder (View root) { 
			super (root); 
			pageView = root.findViewById (R.id.pvBigPage); 
			titleView = root.findViewById (R.id.tvPageTitle); 
			topRightView = root.findViewById (R.id.tvTopRightCornerText); 
			tileContainer = root.findViewById (R.id.flPageTile); 
			horizontalRule = root.findViewById (R.id.vHorizontalRule); 
			verticalRule = root.findViewById (R.id.vVerticalRule); 
			mAllPageViews.add (pageView); 
		} 
		public void bind (File itemFile, int positionInList) { 
			final int pageIndex = mIsPDF ? positionInList - countHeaderViews () : 1; 
			titleView.setText (itemFile.getName ()); 
			topRightView.setText (mIsPDF ? String.format (Locale.US, mContext.getString (R.string.label_pg_of), pageIndex + 1, mPdfPageCount) : ""); 
			mItemFile = itemFile; 
			mListPosition = positionInList; 
			pageView.setOnClickListener (onClickListener); 
			pageView.setOnLongClickListener (onLongClickListener); 
			tileContainer.setBackgroundResource (mUsePictureFrameBackground ? android.R.drawable.picture_frame : 0); 
			if (!mUsePictureFrameBackground) tileContainer.setPadding (0, 0, 0, 0); 
			horizontalRule.setVisibility (mUsePictureFrameBackground ? View.GONE : View.VISIBLE); 
			verticalRule.setVisibility (mUsePictureFrameBackground ? View.GONE : View.VISIBLE); 
			pageView.mErrorCallback = mErrorCallback; 
			pageView.sampleMode = mSampleMode; 
			pageView.loadMode = mLoadMode; 
			pageView.previewMode = mPreviewMode; 
			renderPage (pageIndex); 
			pageView.setItemFile (itemFile, pageIndex); 
			pageView.setPenMode (mPenMode); 
			pageView.setToolMode (mToolMode); 
			pageView.mTool = mTool; 
			pageView.mColor = mColor; 
			pageView.mBrush = mBrush; 
			if (mIsPDF) pageView.mSizeChangeCallback = new PageView.SizeChanged () { 
				@Override public void onSizeChanged () { 
					renderPage (pageIndex); 
				} 
			}; 
			else pageView.mSizeChangeCallback = null; 
		} 
		synchronized void renderPage (int pageIndex) { 
			if (mIsPDF && pdfDocument != null) { 
				pdfiumCore.openPage (pdfDocument, pageIndex); 
				int srcWidth = pdfiumCore.getPageWidth (pdfDocument, pageIndex); 
				int srcHeight = pdfiumCore.getPageHeight (pdfDocument, pageIndex); 
				int targetWidth = pageView.getWidth (); 
				int targetHeight = targetWidth * srcHeight / srcWidth; 
				int loadWidth = targetWidth == 0 ? srcWidth : Math.min (srcWidth, targetWidth); 
				int loadHeight = targetHeight == 0 ? srcHeight : Math.min (srcHeight, targetHeight); 
				pageView.mBitmapNaturalWidth = loadWidth; 
				pageView.mBitmapNaturalHeight = loadHeight; 
				if (targetWidth != 0) { 
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
						pdfiumCore.renderPageBitmap (pdfDocument, bmp, pageIndex, 0, 0, loadWidth, loadHeight); 
						pageView.mBackgroundBitmap = bmp; 
						pageView.mBitmapNaturalWidth = loadWidth; 
						pageView.mBitmapNaturalHeight = loadHeight; 
					} 
				} 
			} else { 
				if (pageView.mBackgroundBitmap != null) { 
					pageView.mBackgroundBitmap.recycle (); 
					pageView.mBackgroundBitmap = null; 
				} 
			} 
			if (pageView.getHeight () != 0 && pageView.mBitmapNaturalHeight != 0 && 
						pageView.getWidth () * 100 / pageView.getHeight () != pageView.mBitmapNaturalWidth * 100 / pageView.mBitmapNaturalHeight) 
				pageView.requestLayout (); // Redo layout if the view's aspect ratio is different from the bitmap's. 
			pageView.invalidate (); 
		} 
	} 
	
	public void usePictureFrameBackground (boolean whetherUsePictureFrame) { 
		mUsePictureFrameBackground = whetherUsePictureFrame; 
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
