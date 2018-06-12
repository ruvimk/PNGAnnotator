package com.gradgoose.pennotepad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class SubfoldersAdapter extends RecyclerView.Adapter { 
	static final String TAG = "SubfoldersAdapter"; 
	
	final int touchSlop; 
	
	final Context mContext; 
	final Vector<File> mBrowsingFolder; 
	final HashMap<String, Long> mStableIds; 
	
	final String picturesFolderName; 
	
	final SelectionManager selectionManager; 
	
	File [] additionalDirsToShow = new File [0]; 
	
	File mList [] [] = null; 
	
	boolean matchParentWidth = false; 
	
	static Comparator<File []> mFileComparator = new Comparator<File []> () { 
		@Override public int compare (File a [], File b []) { 
			return a[0].getName ().compareTo (b[0].getName ()); 
		} 
	}; 
	private void prepareFileList () { 
		HashMap<String,Vector<File>> children = new HashMap<> (); 
		for (File folder : mBrowsingFolder) { 
			File list [] = folder.listFiles (SelectionManager.mFilterJustFolders); 
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
		selectionManager.mSubfolderList = list; 
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
	
	public SubfoldersAdapter (Context context, Vector<File> browsingDir, @Nullable File [] additionalFoldersToShow, SelectionManager selMgr) { 
		super (); 
		mContext = context; 
		selectionManager = selMgr; 
		selectionManager.selectionListeners.add (new SelectionManager.SelectionListener () { 
			@Override public void onSelectionBegin () { 
				notifyDataSetChanged (); 
			} 
			@Override public void onSelectionChange () { 
				notifyDataSetChanged (); 
			} 
			@Override public void onSelectionEnd () { 
				notifyDataSetChanged (); 
			} 
			@Override public void onSelectionFilesChanged () { 
				reloadList (); 
			} 
		}); 
		touchSlop = ViewConfiguration.get (context).getScaledTouchSlop (); 
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
		float firstX = 0; 
		float firstY = 0; 
		boolean noDisallowIntercept = false; 
		@Override public boolean onTouch (View view, MotionEvent motionEvent) { 
			float x = motionEvent.getX (); 
			float y = motionEvent.getY (); 
			if (motionEvent.getAction () == MotionEvent.ACTION_DOWN) { 
				firstX = x; 
				firstY = y; 
				noDisallowIntercept = false; 
				view.getParent ().requestDisallowInterceptTouchEvent (true); 
			} 
			if (!noDisallowIntercept && Math.sqrt ((x - firstX) * (x - firstX) 
					+ (y - firstY) * (y - firstY) 
			) > touchSlop) { 
				noDisallowIntercept = true; 
				view.getParent ().requestDisallowInterceptTouchEvent (false); 
			} 
			return false; 
		} 
	}; 
	
	public interface OnFolderClickListener { 
		boolean onFolderClick (File folderClicked); 
	} 
	OnFolderClickListener mFolderClickListener = null; 
	
	public class Holder extends RecyclerView.ViewHolder { 
		final ImageView iconView; 
		final ImageView cutIcon; 
		final TextView nameView; 
		final CheckBox checkboxView; 
		View.OnClickListener mToggleSelectedItemOnclick = new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (!selectionManager.mActionModeActive) return; // Do nothing if not in select mode. 
				Object itemObject = itemView.getTag (R.id.item_file); 
				File itemFile = itemObject instanceof File ? (File) itemObject : null; 
				if (itemFile == null) return; 
				if (getAdditionalDirToShow (itemFile) != null) return; // Cannot select one of these. 
				if (selectionManager.isFileSelected (itemFile.getName ())) selectionManager.deselectFile (itemFile.getName ()); 
				else selectionManager.selectFile (itemFile.getName ()); 
				if (selectionManager.mSelection.isEmpty ()) {
					selectionManager.finishSelection (); 
				} 
			} 
		}; 
		View.OnLongClickListener mOnLongClick = new View.OnLongClickListener () { 
			@Override public boolean onLongClick (View view) { 
				if (selectionManager.mActionModeActive) return false; 
				Object itemObject = itemView.getTag (R.id.item_file); 
				File itemFile = itemObject instanceof File ? (File) itemObject : null; 
				if (getAdditionalDirToShow (itemFile) != null) return false; // Cannot select one of these. 
				if (itemFile != null) selectionManager.selectFile (itemFile.getName ()); 
				selectionManager.startActionMode (); 
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
			boolean showNameView = additionalFile == null && selectionManager.mActionModeActive; 
			nameView.setVisibility (showNameView ? View.GONE : View.VISIBLE); 
			checkboxView.setVisibility (showNameView ? View.VISIBLE : View.GONE); 
			cutIcon.setVisibility (additionalFile == null && SelectionManager.PRIVATE_CLIPBOARD.contains (itemPath) ? View.VISIBLE : View.GONE); 
			nameView.setText (itemFile.getName ()); 
			checkboxView.setText (itemFile.getName ()); 
			checkboxView.setChecked (selectionManager.isFileSelected (itemFile.getName ())); 
			itemView.setOnClickListener (selectionManager.mActionModeActive ? mToggleSelectedItemOnclick : mOpenSubfolderOnclick); 
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
//		Log.i (TAG, "getItemCount (): returning " + (mSubfolderList.length + additionalDirsToShow.length)); 
		return mList.length + additionalDirsToShow.length; 
	} 
	
} 
