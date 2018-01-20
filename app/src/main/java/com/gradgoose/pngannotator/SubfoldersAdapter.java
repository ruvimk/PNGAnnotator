package com.gradgoose.pngannotator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
	private View.OnClickListener mOpenSubfolderOnclick = new View.OnClickListener () { 
		@Override public void onClick (View view) { 
			Object tag = view.getTag (R.id.item_file); 
			if (tag != null && tag instanceof File) { 
				File itemFile = (File) tag;
				openSubfolder (itemFile); 
			} 
		} 
	}; 
	
	ActionMode mActionMode = null; 
	boolean mActionModeActive = false; 
	final Vector<String> mSelection = new Vector<> (); 
	MyActionModeCallback mActionModeCallback = new MyActionModeCallback (); 
	class MyActionModeCallback implements ActionMode.Callback { 
		MenuItem mMenuRename = null; 
		public void updateMenuVisibility () { 
			if (mMenuRename != null) mMenuRename.setVisible (mSelection.size () == 1); 
		} 
		@Override public boolean onCreateActionMode (ActionMode actionMode, Menu menu) {
			MenuInflater inflater = actionMode.getMenuInflater (); 
			inflater.inflate (R.menu.folder_menu, menu); 
			mActionModeActive = true; 
			return true; 
		} 
		@Override public boolean onPrepareActionMode (ActionMode actionMode, Menu menu) { 
			mMenuRename = menu.findItem (R.id.action_rename); 
			updateMenuVisibility (); 
			return true; 
		} 
		@Override public boolean onActionItemClicked (ActionMode actionMode, MenuItem menuItem) { 
			switch (menuItem.getItemId ()) { 
				case R.id.action_cut: 
					
					return true; 
				case R.id.action_delete: 
					
					return true; 
				case R.id.action_rename: 
					Vector<File> oldName = null; 
					String targetName = mSelection.elementAt (0); 
					for (File [] files : mList){ 
						if (!files[0].getName ().equals (targetName)) 
							continue; 
						oldName = new Vector<> (files.length); 
						for (File f : files) 
							oldName.add (f); 
						break; 
					} 
					if (oldName == null) return false; // Not found? This should not happen, but just do nothing if it does. 
					if (mContext instanceof NoteActivity) 
						NoteActivity.userRenameFile ((NoteActivity) mContext, 
								oldName, ""); 
					mActionMode.finish (); 
					return true; 
			} 
			return false; 
		} 
		@Override public void onDestroyActionMode (ActionMode actionMode) { 
			mActionModeActive = false; 
			mSelection.clear (); 
			notifyDataSetChanged (); 
		} 
	}; 
	void selectFile (String file) { 
		mSelection.add (file); 
	} 
	void deselectFile (String file) { 
		mSelection.remove (file); 
	} 
	boolean isFileSelected (String file) { 
		for (String f : mSelection) 
			if (f.equals (file)) 
				return true; 
		return false; 
	} 
	public class Holder extends RecyclerView.ViewHolder { 
		final ImageView iconView; 
		final TextView nameView; 
		final CheckBox checkboxView; 
		View.OnClickListener mToggleSelectedItemOnclick = new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (!mActionModeActive) return; // Do nothing if not in select mode. 
				Object itemObject = itemView.getTag (R.id.item_file); 
				File itemFile = itemObject instanceof File ? (File) itemObject : null; 
				if (itemFile == null) return; 
				if (isFileSelected (itemFile.getName ())) deselectFile (itemFile.getName ()); 
				else selectFile (itemFile.getName ()); 
				if (mSelection.isEmpty ()) { 
					mActionMode.finish (); 
					mActionMode = null; 
				} 
				mActionModeCallback.updateMenuVisibility (); 
				notifyDataSetChanged (); 
			} 
		}; 
		View.OnLongClickListener mOnLongClick = new View.OnLongClickListener () { 
			@Override public boolean onLongClick (View view) { 
				if (mActionModeActive) return false; 
				Object itemObject = itemView.getTag (R.id.item_file); 
				File itemFile = itemObject instanceof File ? (File) itemObject : null; 
				if (itemFile != null) selectFile (itemFile.getName ()); 
				mActionMode = ((Activity) mContext).startActionMode (mActionModeCallback); 
				notifyDataSetChanged (); 
				return true; 
			} 
		}; 
		public Holder (View root) { 
			super (root); 
			iconView = root.findViewById (R.id.ivItemIcon); 
			nameView = root.findViewById (R.id.tvItemName); 
			checkboxView = root.findViewById (R.id.cbItemName); 
			itemView.setOnLongClickListener (mOnLongClick); 
		} 
		public void bind (File itemFile) { 
			itemView.setTag (R.id.item_file, itemFile); 
			nameView.setVisibility (mActionModeActive ? View.GONE : View.VISIBLE); 
			checkboxView.setVisibility (mActionModeActive ? View.VISIBLE : View.GONE); 
			nameView.setText (itemFile.getName ()); 
			checkboxView.setText (itemFile.getName ()); 
			checkboxView.setChecked (isFileSelected (itemFile.getName ())); 
			itemView.setOnClickListener (mActionModeActive ? mToggleSelectedItemOnclick : mOpenSubfolderOnclick); 
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
