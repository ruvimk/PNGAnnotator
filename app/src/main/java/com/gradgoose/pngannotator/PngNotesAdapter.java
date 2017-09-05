package com.gradgoose.pngannotator;

import android.content.Context;
import android.net.Uri;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PngNotesAdapter extends RecyclerView.Adapter { 
	final Context mContext; 
	final File mBrowsingFolder; 
	final HashMap<String, Long> mStableIds; 
	
	boolean mPenMode = false; 
	boolean mToolMode = false; 
	
	long headerPersistantIdStart = 0; 
	View mHeaderItemViews [] = null; 
	
	File mList [] = null; 
	
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
	
	private void prepareFileList () { 
		File list [] = mBrowsingFolder.listFiles (mFilterJustImages); 
		Arrays.sort (list); 
		mList = list; 
	} 
	
	public void reloadList () { 
		prepareFileList (); 
	} 
	
	private File getItemFile (int position) { 
		return position < mHeaderItemViews.length ? 
					   null : 
				(position - mHeaderItemViews.length < mList.length ? 
						mList[position - mHeaderItemViews.length] : null); 
	} 
	
	private void loadIds (File list []) { 
		long maximum = 1; 
		for (Long l : mStableIds.values ()) 
			if (l > maximum) 
				maximum = l; 
		for (File file : list) 
			if (!mStableIds.containsKey (file.getAbsolutePath ())) 
				mStableIds.put (file.getAbsolutePath (), ++maximum); 
	} 
	private void loadIds () { 
		loadIds (mBrowsingFolder.listFiles (mFilterJustImages)); 
	} 
	
	static boolean hasImages (File folder) { 
		File list [] = folder.listFiles (); 
		for (File file : list) 
			if (mFilterJustImages.accept (file)) 
				return true; 
		return false; 
	} 
	private static FileFilter mFilterJustImages = new FileFilter () { 
		@Override public boolean accept (File file) { 
			return file.isFile () && file.getName ().toLowerCase ().endsWith (".png"); 
		} 
	}; 
	
	public PngNotesAdapter (Context context, File browsingDir) { 
		super (); 
		mContext = context; 
		mBrowsingFolder = browsingDir; 
		File [] list = browsingDir.listFiles (mFilterJustImages); 
		mStableIds = new HashMap<> (list.length); 
		loadIds (list); 
		reloadList (); // Load the list for the first time. 
		setHasStableIds (true); 
	} 
	
	public class Plain extends RecyclerView.ViewHolder { 
		public Plain (View root) { 
			super (root); 
		} 
	} 
	public class Holder extends RecyclerView.ViewHolder { 
		final PageView pageView; 
		final TextView titleView; 
		public Holder (View root) { 
			super (root); 
			pageView = root.findViewById (R.id.pvBigPage); 
			titleView = root.findViewById (R.id.tvPageTitle); 
		} 
		public void bind (File itemFile) { 
			titleView.setText (itemFile.getName ()); 
			pageView.setItemFile (itemFile); 
			pageView.setPenMode (mPenMode); 
			pageView.setToolMode (mToolMode); 
		} 
	} 
	
	@Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) { 
		// If it's of a header view type, just return a plain holder with the header view: 
		if (viewType >= 100) 
			return new Plain (mHeaderItemViews[viewType - 100]); 
		// Otherwise, it's just a list item type; return a regular list holder of ours: 
		View itemView = LayoutInflater.from (mContext).inflate (R.layout.big_page, parent, false); 
		return new Holder (itemView); 
	}
	
	@Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof Holder) 
			((Holder) holder).bind (getItemFile (position)); 
	} 
	
	@Override public int getItemViewType (int position) { 
		return position < mHeaderItemViews.length ? 
					   (position + 100) // Just use 100 and on for header view types. 
					   : 1; 
	} 
	
	@Override
	public long getItemId (int position) { 
		File itemFile = getItemFile (position); 
		if (itemFile != null) { 
			String path = itemFile.getAbsolutePath (); 
			if (mStableIds.containsKey (path)) 
				return mStableIds.get (path); 
		} 
		return super.getItemId (position);
	}
	
	@Override
	public int getItemCount () { 
		return (mList != null ? mList.length : 0) + 
					   (mHeaderItemViews != null ? mHeaderItemViews.length : 0); 
	} 
	
	public int countHeaderViews () { 
		return mHeaderItemViews != null ? mHeaderItemViews.length : 0; 
	} 
	public int countImages () { 
		return mList != null ? mList.length : 0; 
	} 
	
} 
