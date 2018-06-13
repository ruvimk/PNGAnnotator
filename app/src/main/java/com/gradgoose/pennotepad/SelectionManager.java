package com.gradgoose.pennotepad;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Vector;

public class SelectionManager { 
	final Context mContext; 
	File [] [] mSubfolderList = new File [0] []; 
	SelectionManager (Context context) { 
		mContext = context; 
	} 
	static SharedPreferences OWNED_FOLDERS = null; 
	static SharedPreferences HIDDEN_FOLDERS = null; 
	static SharedPreferences PRIVATE_CLIPBOARD = null; 
	private static final Object CLIPBOARD_MUTEX = new Object (); 
	
	static class FileEntry { 
		// Set either 'files' or the 'singleFile'+'pageIndex'; not both. 
		Vector<File> files = null; 
		File singleFile = null; 
		int pageIndex = 0; 
		boolean isMultipage = false; 
		boolean isDirectory () { 
			if (files != null && !files.isEmpty ()) 
				return files.elementAt (0).isDirectory (); 
			else return singleFile.isDirectory (); 
		} 
	} 
	
	Vector<FileEntry> getSelectedFiles () { 
		Vector<FileEntry> result = new Vector<> (mSelection.size ()); 
		for (String targetName : mSelection) { 
			boolean found = false; 
			for (File [] files : mSubfolderList) { 
				if (!files[0].getName ().equals (targetName)) 
					continue; 
				Vector<File> item = new Vector<> (files.length); 
				for (File f : files) 
					item.add (f); 
				FileEntry entry = new FileEntry (); 
				entry.files = item; 
				result.add (entry); 
				found = true; 
				break; 
			} 
			if (!found) { 
				int lastColon = targetName.lastIndexOf (':'); 
				int pageIndex = 0; 
				boolean isMultipage = false; 
				String namePart = targetName; 
				if (lastColon > 0) { 
					namePart = targetName.substring (0, lastColon); 
					try {
						pageIndex = Integer.parseInt (targetName.substring (lastColon + 1)); 
						isMultipage = true; 
					} catch (NumberFormatException err) { 
						// Can't grab page number part. Maybe just the filename has a colon in it? 
						namePart = targetName; 
						pageIndex = 0; 
					} 
				} 
				File file = new File (namePart); 
				if (!file.exists ()) 
					continue; 
				FileEntry entry = new FileEntry (); 
				entry.singleFile = file; 
				entry.pageIndex = pageIndex; 
				entry.isMultipage = isMultipage; 
				result.add (entry); 
			} 
		} 
		return result; 
	} 
	
	ActionMode mActionMode = null; 
	boolean mActionModeActive = false; 
	final Vector<String> mSelection = new Vector<> (); 
	NotesActionModeCallback mActionModeCallback = new NotesActionModeCallback (); 
	class NotesActionModeCallback implements ActionMode.Callback { 
		MenuItem mMenuRename = null; 
		MenuItem mMenuCut = null; 
		MenuItem mMenuDelete = null; 
		public void updateMenuVisibility () { 
			if (mMenuRename != null) mMenuRename.setVisible (mSelection.size () == 1); 
			if (mMenuCut != null) mMenuCut.setVisible (!hasNonOwnedFolders () && !hasPdfPages ()); 
			if (mMenuDelete != null) mMenuDelete.setVisible (!hasPdfPages ()); 
		} 
		Vector<FileEntry> lastCalculatedSelected = null; 
		public boolean hasNonOwnedFolders () { 
			Vector<FileEntry> selected = getSelectedFiles (); 
			if (selected.size () < 1) return false; 
			boolean hasNonOwnedFolders = false; 
			for (FileEntry entry : selected) { 
				Vector<File> files = entry.files; 
				if (files != null) for (File file : files) { 
					if (!file.isDirectory ()) continue; 
					if (SelectionManager.isOwnedByMe (file)) continue; 
					// Otherwise, not owned by me. 
					hasNonOwnedFolders = true; 
					break; 
				} 
				if (hasNonOwnedFolders) break; 
			} 
			lastCalculatedSelected = selected; 
			return hasNonOwnedFolders; 
		} 
		boolean hasPdfPages () { 
			Vector<FileEntry> selected = getSelectedFiles (); 
			if (selected.size () < 1) return false; 
			for (FileEntry entry : selected) { 
				if (entry.isMultipage) 
					return true; 
			} 
			return false; 
		} 
		@Override public boolean onCreateActionMode (ActionMode actionMode, Menu menu) { 
			MenuInflater inflater = actionMode.getMenuInflater (); 
			inflater.inflate (R.menu.folder_menu, menu); 
			mActionModeActive = true; 
			for (SelectionListener listener : selectionListeners) 
				listener.onSelectionBegin (); 
			return true; 
		} 
		@Override public boolean onPrepareActionMode (ActionMode actionMode, Menu menu) { 
			mMenuRename = menu.findItem (R.id.action_rename); 
			mMenuCut = menu.findItem (R.id.action_cut); 
			mMenuDelete = menu.findItem (R.id.action_delete); 
			updateMenuVisibility (); 
			return true; 
		} 
		@Override public boolean onActionItemClicked (ActionMode actionMode, MenuItem menuItem) { 
			boolean hasNonOwned = hasNonOwnedFolders (); 
			Vector<FileEntry> selected = lastCalculatedSelected != null ? lastCalculatedSelected : getSelectedFiles (); 
			if (selected.size () < 1) return false; 
			switch (menuItem.getItemId ()) { 
				case R.id.action_copy: 
					SelectionManager.copyFiles (selected); 
					for (SelectionListener listener : selectionListeners) 
						listener.onSelectionEnd (); 
					mActionMode.finish (); 
					return true; 
				case R.id.action_cut: 
					if (hasNonOwned) return false; 
					SelectionManager.cutFiles (selected); 
					for (SelectionListener listener : selectionListeners) 
						listener.onSelectionEnd (); 
					mActionMode.finish (); 
					return true; 
				case R.id.action_delete: 
					if (mContext instanceof NoteActivity) 
						if (((NoteActivity) mContext).userDeleteFiles (selected)) 
							mActionMode.finish (); 
					return true; 
				case R.id.action_rename: 
					FileEntry oldName = selected.elementAt (0); 
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
			for (SelectionListener listener : selectionListeners) 
				listener.onSelectionEnd (); 
		} 
	}
	
	public interface SelectionListener { 
		void onSelectionBegin (); 
		void onSelectionChange (); 
		void onSelectionEnd (); 
		void onSelectionFilesChanged (); 
	} 
	
	Vector<SelectionListener> selectionListeners = new Vector<> (); 
	
	void finishSelection () { 
		ActionMode mode = mActionMode; 
		if (mode != null) { 
			mActionMode = null; 
			mode.finish (); 
		} 
	} 
	void updateMenu () { 
		mActionModeCallback.updateMenuVisibility (); 
	} 
	void startActionMode () { 
		if (mActionModeActive) return; 
		mActionMode = ((Activity) mContext).startActionMode (mActionModeCallback); 
	} 
	
	void selectFile (String file) { 
		mSelection.add (file); 
		for (SelectionListener listener : selectionListeners) 
			listener.onSelectionChange (); 
		updateMenu (); 
	} 
	void deselectFile (String file) { 
		mSelection.remove (file); 
		for (SelectionListener listener : selectionListeners) 
			listener.onSelectionChange (); 
		updateMenu (); 
	} 
	boolean isFileSelected (String file) { 
		for (String f : mSelection) 
			if (f.equals (file)) 
				return true; 
		return false; 
	} 
	
	Runnable mRunnableDoneIO = new Runnable () { 
		@Override public void run () { 
			for (SelectionListener listener : selectionListeners) 
				listener.onSelectionFilesChanged (); 
		} 
	}; 
	void paste (File destinationFolder) { 
		pasteFiles (destinationFolder, mContext, mRunnableDoneIO); 
	} 
	
	
	static boolean isCut (String file) { 
		return PRIVATE_CLIPBOARD.contains (file) && PRIVATE_CLIPBOARD.getString (file, "").equals ("cut"); 
	} 
	static boolean isCopy (String file) { 
		return PRIVATE_CLIPBOARD.contains (file) && PRIVATE_CLIPBOARD.getString (file, "").equals ("copy"); 
	} 
	static String getClipboardItemType (String file) { 
		return PRIVATE_CLIPBOARD.contains (file) ? PRIVATE_CLIPBOARD.getString (file, "") : ""; 
	} 
	
	static FileFilter mFilterJustFolders = new FileFilter () {
		@Override public boolean accept (File file) { 
			if (HIDDEN_FOLDERS != null && HIDDEN_FOLDERS.contains (file.getPath ())) 
				return false; // Do not show folders that are on the "hidden" list. 
			return !new File (file, NoteActivity.HOME_TAG).exists () && // <-- Do not show our home folder in the documents, etc. 
						   (file.isDirectory () || file.getName ().toLowerCase ().endsWith (".pdf")); 
		}
	}; 
	
	static boolean isOwnedByMe (File file) { 
		return file != null && (OWNED_FOLDERS.contains (file.getPath ()) || isOwnedByMe (file.getParentFile ())); 
	} 
	
	static void cutFiles (Vector<FileEntry> files) { 
		synchronized (CLIPBOARD_MUTEX) { 
			SharedPreferences.Editor editor = PRIVATE_CLIPBOARD.edit ().clear (); 
			for (FileEntry entry : files) { 
				if (entry.files != null) 
					for (File f : entry.files) 
						editor.putString (f.getPath (), "cut"); 
				if (entry.singleFile != null && !entry.isMultipage) 
					editor.putString (entry.singleFile.getPath (), "cut"); 
			} 
			editor.apply (); 
		} 
	} 
	static void copyFiles (Vector<FileEntry> files) { 
		// TODO: Build a list of all the files to copy. 
		synchronized (CLIPBOARD_MUTEX) { 
			SharedPreferences.Editor editor = PRIVATE_CLIPBOARD.edit ().clear (); 
			for (FileEntry entry : files) { 
				if (entry.files != null) 
					for (File f : entry.files) 
						editor.putString (f.getPath (), "copy"); 
				if (entry.singleFile != null) { 
					if (entry.isMultipage) { 
						// TODO: Export the page to its own .apg file, and copy that file here. 
					} else editor.putString (entry.singleFile.getPath (), "copy"); 
				} 
			} 
			editor.apply (); 
		} 
		// TODO: Copy to the Android clipboard too. 
	} 
	static boolean hasClipboardItems () {
		return PRIVATE_CLIPBOARD.getAll ().size () > 0;
	} 
	static void pasteFiles (final File destinationFolder, @Nullable final Context opt_context, 
							@Nullable final Runnable opt_finish_callback) { 
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
//					if (opt_context instanceof NoteActivity) { 
//						final NoteActivity noteActivity = (NoteActivity) opt_context; 
//						noteActivity.runOnUiThread (new Runnable () {
//							@Override public void run () { 
//								noteActivity.mSubfoldersAdapter.reloadList (); 
//								noteActivity.mNotesAdapter.reloadList (); 
//								noteActivity.updateUserInterface (); 
//								noteActivity.updateMenuItems (); 
//							}
//						}); 
//					} 
					// Display a success message. 
					final int sTot = total; 
					final int sNow = success; 
					((Activity) opt_context).runOnUiThread (new Runnable () {
						@Override public void run () { 
							if (opt_finish_callback != null) { 
								opt_finish_callback.run (); 
							} 
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
} 
