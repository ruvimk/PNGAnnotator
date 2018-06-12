package com.gradgoose.pennotepad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class SubfoldersAdapter extends RecyclerView.Adapter { 
	static final String TAG = "SubfoldersAdapter"; 
	
	final Context mContext; 
	final Vector<File> mBrowsingFolder; 
	final HashMap<String, Long> mStableIds; 
	
	final String picturesFolderName; 
	
	static SharedPreferences OWNED_FOLDERS = null; 
	static SharedPreferences HIDDEN_FOLDERS = null; 
	static SharedPreferences PRIVATE_CLIPBOARD = null; 
	static final Object CLIPBOARD_MUTEX = new Object (); 
	
	File [] additionalDirsToShow = new File [0]; 
	
	File mList [] [] = null; 
	
	boolean matchParentWidth = false; 
	
	static boolean isOwnedByMe (File file) { 
		return file != null && (SubfoldersAdapter.OWNED_FOLDERS.contains (file.getPath ()) || isOwnedByMe (file.getParentFile ())); 
	} 
	
	static Comparator<File []> mFileComparator = new Comparator<File []> () { 
		@Override public int compare (File a [], File b []) { 
			return a[0].getName ().compareTo (b[0].getName ()); 
		} 
	}; 
	private void prepareFileList () { 
		HashMap<String,Vector<File>> children = new HashMap<> (); 
		for (File folder : mBrowsingFolder) { 
			File list [] = folder.listFiles (mFilterJustFolders); 
			if (list != null) for (File file : list) { 
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
		loadIds (); 
		notifyDataSetChanged (); 
	} 
	
	private File getItemFile (int position) { 
		return position < mList.length ? mList[position][0] : null; 
	} 
	
	private void loadIds (File list [] []) { 
		long maximum = 1025; 
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
			if (HIDDEN_FOLDERS != null && HIDDEN_FOLDERS.contains (file.getPath ())) 
				return false; // Do not show folders that are on the "hidden" list. 
			return !new File (file, NoteActivity.HOME_TAG).exists () && // <-- Do not show our home folder in the documents, etc. 
						   (file.isDirectory () || file.getName ().toLowerCase ().endsWith (".pdf")); 
		} 
	}; 
	
	public SubfoldersAdapter (Context context, Vector<File> browsingDir, @Nullable File [] additionalFoldersToShow) { 
		super (); 
		mContext = context; 
		mBrowsingFolder = browsingDir; 
		if (additionalFoldersToShow != null) 
			additionalDirsToShow = additionalFoldersToShow; 
		picturesFolderName = context.getString (R.string.title_pictures); 
		prepareFileList (); 
		mStableIds = new HashMap<> (mList.length); 
		loadIds (mList); 
		notifyDataSetChanged (); 
		setHasStableIds (true); 
	} 
	
	@Nullable File getAdditionalDirToShow (File matchingFile) { 
		for (File f : additionalDirsToShow) { 
			if (f != matchingFile) continue; 
			return f; 
		} 
		return null; 
	} 
	private void openSubfolder (File itemFile) { 
		Intent intent = new Intent (mContext, NoteActivity.class); 
		File [] target = new File [] { getAdditionalDirToShow (itemFile) }; 
		if (target[0] == null) 
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
				if (mFolderClickListener == null || mFolderClickListener.onFolderClick (itemFile)) 
					openSubfolder (itemFile); 
			} 
		} 
	}; 
	private View.OnTouchListener mOnTouchListener = new View.OnTouchListener () { 
		@Override public boolean onTouch (View view, MotionEvent motionEvent) { 
			view.getParent ().requestDisallowInterceptTouchEvent (true); 
			return false; 
		} 
	}; 
	
	public interface OnFolderClickListener { 
		boolean onFolderClick (File folderClicked); 
	} 
	OnFolderClickListener mFolderClickListener = null; 
	
	Vector<Vector<File>> getSelectedFiles () { 
		Vector<Vector<File>> result = new Vector<> (mSelection.size ()); 
		for (String targetName : mSelection) { 
			for (File[] files : mList) { 
				if (!files[0].getName ().equals (targetName)) 
					continue; 
				Vector<File> item = new Vector<> (files.length); 
				for (File f : files) 
					item.add (f); 
				result.add (item); 
				break; 
			} 
		} 
		return result; 
	} 
	
	ActionMode mActionMode = null; 
	boolean mActionModeActive = false; 
	final Vector<String> mSelection = new Vector<> (); 
	MyActionModeCallback mActionModeCallback = new MyActionModeCallback (); 
	class MyActionModeCallback implements ActionMode.Callback { 
		MenuItem mMenuRename = null; 
		MenuItem mMenuCut = null; 
		public void updateMenuVisibility () { 
			if (mMenuRename != null) mMenuRename.setVisible (mSelection.size () == 1); 
			if (mMenuCut != null) mMenuCut.setVisible (!hasNonOwnedFolders ()); 
		} 
		Vector<Vector<File>> lastCalculatedSelected = null; 
		public boolean hasNonOwnedFolders () { 
			Vector<Vector<File>> selected = getSelectedFiles (); 
			if (selected.size () < 1) return false; 
			boolean hasNonOwnedFolders = false; 
			for (Vector<File> files : selected) { 
				for (File file : files) { 
					if (!file.isDirectory ()) continue; 
					if (isOwnedByMe (file)) continue; 
					// Otherwise, not owned by me. 
					hasNonOwnedFolders = true; 
					break; 
				} 
				if (hasNonOwnedFolders) break; 
			} 
			lastCalculatedSelected = selected; 
			return hasNonOwnedFolders; 
		} 
		@Override public boolean onCreateActionMode (ActionMode actionMode, Menu menu) {
			MenuInflater inflater = actionMode.getMenuInflater (); 
			inflater.inflate (R.menu.folder_menu, menu); 
			mActionModeActive = true; 
			return true; 
		} 
		@Override public boolean onPrepareActionMode (ActionMode actionMode, Menu menu) { 
			mMenuRename = menu.findItem (R.id.action_rename); 
			mMenuCut = menu.findItem (R.id.action_cut); 
			updateMenuVisibility (); 
			return true; 
		} 
		@Override public boolean onActionItemClicked (ActionMode actionMode, MenuItem menuItem) { 
			boolean hasNonOwned = hasNonOwnedFolders (); 
			Vector<Vector<File>> selected = lastCalculatedSelected != null ? lastCalculatedSelected : getSelectedFiles (); 
			if (selected.size () < 1) return false; 
			switch (menuItem.getItemId ()) { 
				case R.id.action_cut: 
					if (hasNonOwned) return false; 
					cutFiles (selected); 
					if (mContext instanceof NoteActivity) 
						((NoteActivity) mContext).updateMenuItems (); 
					mActionMode.finish (); 
					return true; 
				case R.id.action_delete: 
					if (mContext instanceof NoteActivity) 
						if (((NoteActivity) mContext).userDeleteFiles (selected)) 
							mActionMode.finish (); 
					return true; 
				case R.id.action_rename: 
					Vector<File> oldName = selected.elementAt (0); 
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
	static void cutFiles (Vector<Vector<File>> files) { 
		synchronized (CLIPBOARD_MUTEX) { 
			SharedPreferences.Editor editor = PRIVATE_CLIPBOARD.edit ().clear (); 
			for (Vector<File> fs : files) 
				for (File f : fs) 
					editor.putString (f.getPath (), "cut"); 
			editor.apply (); 
		} 
	} 
	static boolean hasClipboardItems () { 
		return PRIVATE_CLIPBOARD.getAll ().size () > 0; 
	} 
	static void pasteFiles (final File destinationFolder, @Nullable final Context opt_context) { 
		(new Thread () { 
			@Override public void run () { 
				int total = 0; 
				int success = 0; 
				synchronized (CLIPBOARD_MUTEX) { 
					Map<String, ?> all = PRIVATE_CLIPBOARD.getAll ();
					SharedPreferences.Editor editor = PRIVATE_CLIPBOARD.edit ();
					SharedPreferences.Editor owned = OWNED_FOLDERS.edit (); 
					for (Map.Entry<String, ?> entry : all.entrySet ()) { 
						total++; 
						File source = new File (entry.getKey ()); 
						long lastModified = source.lastModified (); // Just in case renameTo () touches this, we'll save a copy. 
						Object type = entry.getValue (); 
						if (type instanceof String) { 
							if (type.equals ("cut")) { 
								File needName = new File (destinationFolder, source.getName ()); 
								String path = needName.getPath (); 
								String ext = ""; 
								// Skip if we're trying to copy a folder inside itself: 
								File parentCheck = needName; 
								boolean skip = false; 
								while ((parentCheck = parentCheck.getParentFile ()) != null) { 
									if (parentCheck.equals (source)) skip = true; 
								} 
								if (skip) continue; 
								// Do filename checks to make sure we don't overwrite anything: 
								if (!source.isDirectory ()) { 
									int lastDotIndex = path.lastIndexOf ('.'); 
									ext = path.substring (lastDotIndex); 
									path = path.substring (0, lastDotIndex); // Get rid of the filename extension. 
								} 
								File tmp = needName; 
								int number = 1; 
								while (tmp.exists ()) { 
									number++; 
									tmp = new File (path + " (" + String.valueOf (number) + ")" + ext); 
								} 
								needName = tmp; 
								// Now try to move the file: 
								if (source.renameTo (needName)) { 
									needName.setLastModified (lastModified); // And restore it here. 
									owned.remove (source.getPath ()); // Take out old path from our owned list, if applicable. 
									editor.remove (source.getPath ()); // Done with this file. 
									owned.putBoolean (needName.getPath (), true); // Take note of it, so we know we own it. 
									success++; 
								} 
							} 
						} 
					} 
					owned.apply (); 
					editor.apply (); 
				} 
				if (opt_context != null && opt_context instanceof Activity) { 
					if (opt_context instanceof NoteActivity) { 
						final NoteActivity noteActivity = (NoteActivity) opt_context; 
						noteActivity.runOnUiThread (new Runnable () { 
							@Override public void run () { 
								noteActivity.mSubfoldersAdapter.reloadList (); 
								noteActivity.mNotesAdapter.reloadList (); 
								noteActivity.updateUserInterface (); 
								noteActivity.updateMenuItems (); 
							} 
						}); 
					} 
					// Display a success message. 
					final int sTot = total; 
					final int sNow = success; 
					((Activity) opt_context).runOnUiThread (new Runnable () { 
						@Override public void run () {
							Toast.makeText (opt_context, opt_context.getString (R.string.msg_files_pasted) 
									.replace ("[number]", String.valueOf (sNow)) 
									.replace ("[total]", String.valueOf (sTot)) 
									, Toast.LENGTH_SHORT) 
									.show (); 
						} 
					}); 
				} 
			} 
		}).start (); 
	} 
	public class Holder extends RecyclerView.ViewHolder { 
		final ImageView iconView; 
		final ImageView cutIcon; 
		final TextView nameView; 
		final CheckBox checkboxView; 
		View.OnClickListener mToggleSelectedItemOnclick = new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (!mActionModeActive) return; // Do nothing if not in select mode. 
				Object itemObject = itemView.getTag (R.id.item_file); 
				File itemFile = itemObject instanceof File ? (File) itemObject : null; 
				if (itemFile == null) return; 
				if (getAdditionalDirToShow (itemFile) != null) return; // Cannot select one of these. 
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
				if (getAdditionalDirToShow (itemFile) != null) return false; // Cannot select one of these. 
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
			cutIcon = root.findViewById (R.id.ivCutIcon); 
			itemView.setOnLongClickListener (mOnLongClick); 
		} 
		public void bind (File itemFile) { 
			File additionalFile = getAdditionalDirToShow (itemFile); 
			int folderIconResource = itemFile.isDirectory () ? R.drawable.ic_folder_peach_120dp : R.drawable.ic_book_orange_120dp; 
			String itemPath = itemFile.getPath (); 
			if (itemPath.equals ("Pictures") || itemPath.equals (picturesFolderName)) 
				folderIconResource = R.drawable.ic_picture_120dp; 
			iconView.setImageResource (folderIconResource); 
			itemView.setTag (R.id.item_file, itemFile); 
			ViewGroup.LayoutParams lp = itemView.getLayoutParams (); 
			lp.width = matchParentWidth ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT; 
			boolean showNameView = additionalFile == null && mActionModeActive; 
			nameView.setVisibility (showNameView ? View.GONE : View.VISIBLE); 
			checkboxView.setVisibility (showNameView ? View.VISIBLE : View.GONE); 
			cutIcon.setVisibility (additionalFile == null && PRIVATE_CLIPBOARD.contains (itemPath) ? View.VISIBLE : View.GONE); 
			nameView.setText (itemFile.getName ()); 
			checkboxView.setText (itemFile.getName ()); 
			checkboxView.setChecked (isFileSelected (itemFile.getName ())); 
			itemView.setOnClickListener (mActionModeActive ? mToggleSelectedItemOnclick : mOpenSubfolderOnclick); 
			itemView.setOnTouchListener (mOnTouchListener); 
		} 
	} 
	
	@Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) { 
		View itemView = LayoutInflater.from (mContext).inflate (R.layout.small_icon, parent, false); 
		return new Holder (itemView); 
	} 
	
	@Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) { 
		if (holder instanceof Holder) 
			((Holder) holder).bind (position < additionalDirsToShow.length ? 
											additionalDirsToShow[position] : 
											getItemFile (position - additionalDirsToShow.length)); 
	} 
	
	@Override public int getItemViewType (int position) {
		return super.getItemViewType (position);
	}
	
	@Override public long getItemId (int position) { 
		if (position < additionalDirsToShow.length) 
			return 1 + position; 
		File itemFile = getItemFile (position - additionalDirsToShow.length); 
		if (itemFile != null) { 
			String path = itemFile.getAbsolutePath (); 
			if (mStableIds.containsKey (path)) 
				return mStableIds.get (path); 
		} 
		return super.getItemId (position); 
	} 
	
	@Override public int getItemCount () { 
//		Log.i (TAG, "getItemCount (): returning " + (mList.length + additionalDirsToShow.length)); 
		return mList.length + additionalDirsToShow.length; 
	} 
	
} 
