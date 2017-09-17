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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class SubfoldersAdapter extends RecyclerView.Adapter { 
	final Context mContext; 
	final Vector<File> mBrowsingFolder; 
	final HashMap<String, Long> mStableIds; 
	
	File mList [] [] = null; 
	
	static Comparator<File []> mFileComparator = new Comparator<File []> () { 
		@Override public int compare (File a [], File b []) { 
			return a[0].getName ().compareTo (b[0].getName ()); 
		} 
	}; 
	private void prepareFileList () { 
		HashMap<String,Vector<File>> children = new HashMap<> (); 
		for (File folder : mBrowsingFolder) { 
			File list [] = folder.listFiles (mFilterJustFolders); 
			for (File file : list) { 
				// Skip certain folder names: 
				if (file.getName ().equals (".thumbnails")) continue; 
				// Add the folder to a list that has all folders of this exact name: 
				if (!children.containsKey (file.getName ())) { 
					Vector<File> files = new Vector<> (); 
					files.add (file); 
					children.put (file.getName (), files); 
				} else children.get (file.getName ()).add (file); 
			} 
		} 
		File list [] [] = new File [children.size ()] []; 
		int index = 0; 
		for (String name : children.keySet ()) { 
			Vector<File> possible = children.get (name); 
			list[index] = new File [possible.size ()]; 
			possible.toArray (list[index]); 
			index++; 
		} 
		Arrays.sort (list, mFileComparator); 
		mList = list; 
	} 
	
	public void reloadList () { 
		prepareFileList (); 
		notifyDataSetChanged (); 
	} 
	
	private File getItemFile (int position) { 
		return position < mList.length ? mList[position][0] : null; 
	} 
	
	private void loadIds (File list [] []) { 
		long maximum = 1; 
		for (Long l : mStableIds.values ()) 
			if (l > maximum) 
				maximum = l; 
		for (File files [] : list) 
			if (!mStableIds.containsKey (files[0].getAbsolutePath ())) 
				mStableIds.put (files[0].getAbsolutePath (), ++maximum); 
	} 
	private void loadIds () { 
		prepareFileList (); 
		loadIds (mList); 
	} 
	
	static FileFilter mFilterJustFolders = new FileFilter () { 
		@Override public boolean accept (File file) { 
			return file.isDirectory (); 
		} 
	}; 
	
	public SubfoldersAdapter (Context context, Vector<File> browsingDir) { 
		super (); 
		mContext = context; 
		mBrowsingFolder = browsingDir; 
		prepareFileList (); 
		mStableIds = new HashMap<> (mList.length); 
		loadIds (mList); 
		notifyDataSetChanged (); 
		setHasStableIds (true); 
	} 
	
	private void openSubfolder (File itemFile) { 
		Intent intent = new Intent (mContext, NoteActivity.class); 
		File [] target = null; 
		for (File files [] : mList) 
			if (files[0].equals (itemFile)) { 
				target = files; 
				break; 
			} 
		if (target == null) 
			return; 
		String paths [] = new String [target.length]; 
		for (int i = 0; i < target.length; i++) 
			paths[i] = target[i].getAbsolutePath (); 
		intent.putExtra (NoteActivity.STATE_BROWSING_PATH, paths); 
		String parent [] = new String [mBrowsingFolder.size ()]; 
		for (int i = 0; i < parent.length; i++) 
			parent[i] = mBrowsingFolder.elementAt (i).getAbsolutePath (); 
		intent.putExtra (NoteActivity.STATE_PARENT_BROWSE, parent); 
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
