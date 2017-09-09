package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Color;
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
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PngNotesAdapter extends RecyclerView.Adapter { 
	final Context mContext; 
	final Vector<File> mBrowsingFolder; 
	final HashMap<String, Long> mStableIds; 
	
	boolean mPenMode = false; 
	boolean mToolMode = false; 
	
	int mTool = 0; 
	int mColor = Color.BLACK; 
	float mBrush = 3.0f; 
	
	long headerPersistantIdStart = 0; 
	View mHeaderItemViews [] = null; 
	
	File mList [] = null; 
	
	Comparator<File []> mFileComparator = new Comparator<File []> () { 
		@Override public int compare (File a [], File b []) { 
			return a[0].getName ().compareTo (b[0].getName ()); 
		} 
	}; 
	
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
		HashMap<String,Vector<File>> children = new HashMap<> (); 
		for (File folder : mBrowsingFolder) { 
			File list [] = folder.listFiles (mFilterJustImages); 
			for (File file : list) 
				if (!children.containsKey (file.getName ())) { 
					Vector<File> files = new Vector<> (); 
					files.add (file); 
					children.put (file.getName (), files); 
				} else children.get (file.getName ()).add (file); 
		} 
		File list [] [] = new File [children.size ()] []; 
		int index = 0; 
		int total = 0; 
		for (String name : children.keySet ()) { 
			Vector<File> possible = children.get (name); 
			list[index] = new File [possible.size ()]; 
			possible.toArray (list[index]); 
			total += list[index].length; 
			index++; 
		} 
		Arrays.sort (list, mFileComparator); 
		File list2 [] = new File [total]; 
		int c = 0; 
		for (File [] possible : list) { 
			for (File file : possible) { 
				list2[c] = file; 
				c++; 
			} 
		} 
		mList = list2; 
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
	private static FileFilter mFilterJustImages = new FileFilter () { 
		@Override public boolean accept (File file) { 
			String lowerName = file.getName ().toLowerCase (); 
			return file.isFile () && ( 
					lowerName.endsWith (".png") || 
							lowerName.endsWith (".jpg") || 
							lowerName.endsWith (".jpeg") || 
							lowerName.endsWith (".gif") 
			); 
		} 
	}; 
	
	public PngNotesAdapter (Context context, Vector<File> browsingDir) { 
		super (); 
		mContext = context; 
		mBrowsingFolder = browsingDir; 
		prepareFileList (); 
		mStableIds = new HashMap<> (mList.length); 
		loadIds (mList); 
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
			pageView.mTool = mTool; 
			pageView.mColor = mColor; 
			pageView.mBrush = mBrush; 
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
