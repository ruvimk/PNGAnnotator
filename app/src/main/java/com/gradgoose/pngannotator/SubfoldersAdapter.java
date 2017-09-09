package com.gradgoose.pngannotator;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class SubfoldersAdapter extends RecyclerView.Adapter { 
	final Context mContext; 
	final File mBrowsingFolder; 
	final HashMap<String, Long> mStableIds; 
	
	File mList [] = null; 
	
	private void prepareFileList () { 
		File list [] = mBrowsingFolder.listFiles (mFilterJustFolders); 
		Arrays.sort (list); 
		mList = list; 
	} 
	
	public void reloadList () { 
		prepareFileList (); 
		notifyDataSetChanged (); 
	} 
	
	private File getItemFile (int position) { 
		return position < mList.length ? mList[position] : null; 
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
	
	private FileFilter mFilterJustFolders = new FileFilter () { 
		@Override public boolean accept (File file) { 
			return file.isDirectory (); 
		} 
	}; 
	
	public SubfoldersAdapter (Context context, File browsingDir) { 
		super (); 
		mContext = context; 
		mBrowsingFolder = browsingDir; 
		prepareFileList (); 
		mStableIds = new HashMap<> (mList.length); 
		loadIds (mList); 
		reloadList (); // Load the list for the first time. 
		setHasStableIds (true); 
	} 
	
	private void openSubfolder (File itemFile) { 
		Intent intent = new Intent (mContext, NoteActivity.class); 
		intent.putExtra (NoteActivity.STATE_BROWSING_PATH, itemFile.getAbsolutePath ()); 
		mContext.startActivity (intent); 
	} 
	private View.OnClickListener mOnClick = new View.OnClickListener () { 
		@Override public void onClick (View view) { 
			Object tag = view.getTag (R.id.item_file); 
			if (tag != null && tag instanceof File) { 
				File itemFile = (File) tag;
				openSubfolder (itemFile); 
			} 
		} 
	}; 
	
	public class Holder extends RecyclerView.ViewHolder { 
		final ImageView iconView; 
		final TextView nameView; 
		public Holder (View root) { 
			super (root); 
			iconView = root.findViewById (R.id.ivItemIcon); 
			nameView = root.findViewById (R.id.tvItemName); 
			itemView.setOnClickListener (mOnClick); 
		} 
		public void bind (File itemFile) { 
			nameView.setText (itemFile.getName ()); 
			itemView.setTag (R.id.item_file, itemFile); 
		} 
	} 
	
	@Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) { 
		View itemView = LayoutInflater.from (mContext).inflate (R.layout.small_icon, parent, false); 
		return new Holder (itemView); 
	} 
	
	@Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) { 
		if (holder instanceof Holder) 
			((Holder) holder).bind (getItemFile (position)); 
	} 
	
	@Override public int getItemViewType (int position) {
		return super.getItemViewType (position);
	}
	
	@Override public long getItemId (int position) { 
		File itemFile = getItemFile (position); 
		if (itemFile != null) { 
			String path = itemFile.getAbsolutePath (); 
			if (mStableIds.containsKey (path)) 
				return mStableIds.get (path); 
		} 
		return super.getItemId (position); 
	} 
	
	@Override public int getItemCount () { 
		return mList.length; 
	} 
	
} 
