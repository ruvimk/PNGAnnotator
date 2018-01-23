package com.gradgoose.pngannotator;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import static com.gradgoose.pngannotator.SubfoldersAdapter.isOwnedByMe;

public class NoteActivity extends Activity { 
	
	int mOverviewColumnCount = 3; 
	
	File mDCIM = null; // Keep track of the root camera images folder. 
	File mPictures = null; 
	
	File mSdDCIM = null; 
	File mSdPictures = null; 
	
	boolean mPaused = false; 
	
	Vector<File> mBrowsingFolders = null; 
	private void setBrowsingPaths (@Nullable String browsingPaths []) { 
		if (browsingPaths == null) return; 
		Vector<File> files = new Vector<> (); 
		for (String path : browsingPaths) 
			files.add (new File (path)); 
		mBrowsingFolders = files; 
	} 
	
	Vector<File> mParentFolder = null; 
	private void setParentPaths (@Nullable String paths []) { 
		if (paths == null) return; 
		Vector<File> files = new Vector<> (); 
		for (String path : paths) 
			files.add (new File (path)); 
		mParentFolder = files; 
	} 
	
	static final String PREFS_NAME = "com.gradgoose.pngannotator.NoteActivity.prefs"; 
	static final String LEFTOFF_NAME = "com.gradgoose.pngannotator.NoteActivity.leftOff"; 
	static final String RECENTS_NAME = "com.gradgoose.pngannotator.NoteActivity.recents"; 
	static final String OWNED_FOLDERS_NAME = "com.gradgoose.pngannotator.OWNED_FOLDERS"; 
	static final String HIDDEN_FOLDERS_NAME = "com.gradgoose.pngannotator.HIDDEN_FOLDERS"; 
	static final String PRIVATE_CLIPBOARD_NAME = "com.gradgoose.pngannotator.PRIVATE_CLIPBOARD"; 
	static final String MD5_CACHE_NAME = "com.gradgoose.pngannotator.MD5_cache"; 
	SharedPreferences prefs = null; 
	SharedPreferences leftOff = null; 
	SharedPreferences recents = null; 
	
	Vector<String> recentFolders = null; 
	
	static final int TOOL_NONE = 0; 
	static final int TOOL_PEN = 1; 
	static final int TOOL_ERASER = 2; 
	static final int TOOL_WHITEOUT = 3; 
	
	int currentTool = 0; // None. 
	int currentColor = Color.TRANSPARENT; 
	
	static final String STATE_BROWSING_PATH = "com.gradgoose.pngannotator.browse_path"; 
	static final String STATE_PARENT_BROWSE = "com.gradgoose.pngannotator.parent_browse"; 
	static final String STATE_SCROLL_ITEM = "com.gradgoose.pngannotator.scroll_item"; 
	static final String STATE_SCROLL_SPACE = "com.gradgoose.pngannotator.scroll_space"; 
	static final String STATE_SCROLL_FRACTION = "com.gradgoose.pngannotator.scroll_fraction"; 
	
	int initialScrollItemPosition = 0; 
	float initialScrollFraction = 0; 
	
	@Override protected void onCreate (Bundle savedInstanceState) { 
		super.onCreate (savedInstanceState); 
		setContentView (R.layout.activity_main); 
		// Read the key-value quick options from last time: 
		prefs = getSharedPreferences (PREFS_NAME, MODE_PRIVATE); 
		leftOff = getSharedPreferences (LEFTOFF_NAME, MODE_PRIVATE); 
		recents = getSharedPreferences (RECENTS_NAME, MODE_PRIVATE); 
		SubfoldersAdapter.OWNED_FOLDERS = getSharedPreferences (OWNED_FOLDERS_NAME, MODE_PRIVATE); 
		SubfoldersAdapter.HIDDEN_FOLDERS = getSharedPreferences (HIDDEN_FOLDERS_NAME, MODE_PRIVATE); 
		SubfoldersAdapter.PRIVATE_CLIPBOARD = getSharedPreferences (PRIVATE_CLIPBOARD_NAME, MODE_PRIVATE); 
		PageView.mMd5Cache = getSharedPreferences (MD5_CACHE_NAME, MODE_PRIVATE); 
		String recentText; 
		recentFolders = new Vector<> (10); // Can change 10 to something else later. From settings, eg. 
		if (recents.contains ("recent-folders") && 
					(recentText = recents.getString ("recent-folders", null)) != null) { 
			String lines [] = recentText.split ("\\\\n"); 
			for (String line : lines) 
				recentFolders.add (line); 
		} 
		// Grab the editing state: 
		currentTool = prefs.getInt ("tool", currentTool); 
		currentColor = prefs.getInt ("color", currentColor); 
		// Get the folder where digital camera images are stored: 
		mDCIM = Environment.getExternalStoragePublicDirectory ( 
				Environment.DIRECTORY_DCIM 
		); 
		mPictures = Environment.getExternalStoragePublicDirectory (
				Environment.DIRECTORY_PICTURES 
		); 
		// Get some folders: 
		String inCard = mDCIM.getParent (); 
		String sdCard = System.getenv("SECONDARY_STORAGE"); 
		mSdDCIM = new File (sdCard + mDCIM.getAbsolutePath ().substring (inCard.length ())); 
		mSdPictures = new File (sdCard + mPictures.getAbsolutePath ().substring (inCard.length ())); 
		// See if we had a folder already open last time that we can reopen now: 
		if (savedInstanceState != null) { 
			if (savedInstanceState.containsKey (STATE_BROWSING_PATH)) 
				setBrowsingPaths (savedInstanceState.getStringArray (STATE_BROWSING_PATH)); 
			if (savedInstanceState.containsKey (STATE_PARENT_BROWSE)) 
				setParentPaths (savedInstanceState.getStringArray (STATE_PARENT_BROWSE)); 
			if (savedInstanceState.containsKey (STATE_SCROLL_ITEM)) 
				initialScrollItemPosition = savedInstanceState.getInt (STATE_SCROLL_ITEM); 
			if (savedInstanceState.containsKey (STATE_SCROLL_FRACTION)) 
				initialScrollFraction = savedInstanceState.getFloat (STATE_SCROLL_FRACTION); 
		} 
		// See if whoever started this activity wanted us to open any particular folder: 
		Intent sourceIntent = getIntent (); 
		Bundle extras = sourceIntent.getExtras (); 
		if (extras != null) { 
			if (mBrowsingFolders == null) { /* If the above did not give us a folder ... */
				if (extras.containsKey (STATE_BROWSING_PATH)) 
					setBrowsingPaths (extras.getStringArray (STATE_BROWSING_PATH)); 
			} 
			if (mParentFolder == null) { 
				if (extras.containsKey (STATE_PARENT_BROWSE)) 
					setParentPaths (extras.getStringArray (STATE_PARENT_BROWSE)); 
			} 
			if (initialScrollItemPosition == 0 && extras.containsKey (STATE_SCROLL_ITEM)) 
				initialScrollItemPosition = extras.getInt (STATE_SCROLL_ITEM); 
			if (initialScrollFraction == 0 && extras.containsKey (STATE_SCROLL_FRACTION)) 
				initialScrollFraction = extras.getFloat (STATE_SCROLL_FRACTION); 
		} 
		if (mBrowsingFolders == null) // Else use the default of the DCIM folder. 
		{ 
			mBrowsingFolders = new Vector<> (); 
			mBrowsingFolders.add (mPictures); 
			mBrowsingFolders.add (mDCIM); 
			if (mSdDCIM.exists ()) 
				mBrowsingFolders.add (mSdDCIM); 
			if (mSdPictures.exists ()) 
				mBrowsingFolders.add (mSdPictures); 
		} 
		if (mParentFolder == null) { // Use the parents of mBrowsingFolders ... 
			mParentFolder = new Vector<> (mBrowsingFolders.capacity ()); 
			if (!isBrowsingRootFolder ()) { 
				// Only do this if this is not the root folder. 
				for (File file : mBrowsingFolders) { 
					File parent = file.getParentFile (); 
					if (!mParentFolder.contains (parent)) // Check this, just in case. 
						mParentFolder.add (parent); 
				} 
			} 
		} 
		// Check to see if we have a record of what scroll position we were at last time: 
		if (initialScrollItemPosition == 0) // (only if we don't have one loaded from onRestore...) 
			initialScrollItemPosition = leftOff.getInt ("Scroll:" + mBrowsingFolders.get (0).getPath (), 0); 
		if (initialScrollFraction == 0) 
			initialScrollFraction = leftOff.getFloat ("ScrollFraction:" + mBrowsingFolders.get (0).getPath (), 0); 
		// Initialize views and the window title and icon: 
		initUserInterface (); // Views. 
		initActionBar (); // Title, Icon. 
		// Add this thing to the recent folders list: 
		if (!isBrowsingRootFolder () && mNotesAdapter.hasImages ()) { 
			String nowBrowsing = ""; 
			for (File f : mBrowsingFolders) { 
				if (!nowBrowsing.isEmpty ()) nowBrowsing += "\t"; 
				nowBrowsing += f.getAbsolutePath (); 
			} 
			String prefix = nowBrowsing + ";parent:"; 
			String parentText = ""; 
			for (File f : mParentFolder) { 
				if (!parentText.isEmpty ()) parentText += "\t"; 
				parentText += f.getAbsolutePath (); 
			} 
			String fullEntry = prefix + parentText; 
			int foundIndex = -1; 
			for (int i = 0; i < recentFolders.size (); i++) { 
				if (!recentFolders.elementAt (i).startsWith (prefix)) continue; 
				foundIndex = i; 
			} 
			if (foundIndex == -1) { 
				if (recentFolders.size () == recentFolders.capacity ()) 
					recentFolders.remove (recentFolders.size () - 1); 
				recentFolders.add (0, fullEntry); 
			} else { 
				recentFolders.remove (foundIndex); 
				recentFolders.add (0, fullEntry); 
			} 
			// Save the recent folders list: 
			StringBuilder sb = new StringBuilder (recentFolders.elementAt (0).length () * 
				recentFolders.size () * 2); 
			for (int i = 0; i < recentFolders.size (); i++) { 
				if (i > 0) sb.append ("\\n"); 
				sb.append (recentFolders.elementAt (i)); 
			} 
			recents.edit ().putString ("recent-folders", sb.toString ()).apply (); 
		} 
	} 
	// Save which folder we're working on, and what scroll position: 
	@Override protected void onSaveInstanceState (Bundle outState) { 
		// Put things into the saved instance state: 
		String paths [] = new String [mBrowsingFolders.size ()]; 
		for (int i = 0; i < mBrowsingFolders.size (); i++) 
			paths[i] = mBrowsingFolders.elementAt (i).getAbsolutePath (); 
		outState.putStringArray (STATE_BROWSING_PATH, paths);  
		// Calculate scroll position: 
		int scrollPosition = getScrollPosition (); 
		float scrollFraction = getScrollFraction (); 
		outState.putInt (STATE_SCROLL_ITEM, scrollPosition); 
		outState.putFloat (STATE_SCROLL_FRACTION, scrollFraction); 
	} 
	
	boolean mReloadOnNextResume = false; 
	@Override public void onPause () { 
		// Calculate scroll position: 
		int scrollPosition = getScrollPosition (); 
		float scrollFraction = getScrollFraction (); 
		// Update the "last page, left off" value: 
		leftOff.edit () 
				.putInt ("Scroll:" + mBrowsingFolders.elementAt (0).getPath (), scrollPosition) 
				.putFloat ("ScrollFraction:" + mBrowsingFolders.elementAt (0).getPath (), 
						scrollFraction) 
				.apply (); 
		mReloadOnNextResume = true; 
		mPaused = true; 
		if (mNotesAdapter != null) 
			mNotesAdapter.recycleBitmaps (); 
		super.onPause (); 
	} 
	@Override public void onResume () { 
		super.onResume (); 
		mPaused = false; 
		// Only reload if this is following an onPause (): 
		if (mReloadOnNextResume) { 
			mSubfoldersAdapter.reloadList (); 
			mNotesAdapter.reloadList (); 
			// This is because we don't want to reload following an onCreate (), 
			// in which case we would have just been done creating the adapters, 
			// so the lists are already up to date. 
		} 
	} 
	
	MenuItem mMenuGoToPage = null; 
	MenuItem mMenuShowPageNav = null; 
	MenuItem mMenuPenMode = null; 
	MenuItem mMenuToggleOverview = null; 
	MenuItem mMenuPaste = null; 
//	MenuItem mMenuRecents = null; 
	@Override public boolean onCreateOptionsMenu (Menu menu) { 
		getMenuInflater ().inflate (R.menu.main_menu, menu); 
		return true; 
	} 
	protected boolean hasImages () { 
		return mNotesAdapter != null ? 
									mNotesAdapter.hasImages () : 
									PngNotesAdapter.hasImages (mBrowsingFolders); 
	} 
	protected boolean canShowAsGrid () { 
		return hasImages (); 
	} 
	void updateMenuItems () { 
		if (mMenuGoToPage == null) return; // Return if these have not yet been initialized. 
		boolean hasImages = hasImages (); 
		mMenuGoToPage.setVisible (hasImages); 
		mMenuToggleOverview.setVisible (canShowAsGrid ()); 
		mMenuShowPageNav.setVisible (hasImages ()); 
		mMenuPaste.setVisible (SubfoldersAdapter.hasClipboardItems ()); 
//		mMenuRecents.setVisible (recentFolders.size () > 1 && hasImages); 
		mMenuToggleOverview.setChecked (prefs.getBoolean ("notes-overview", false)); 
		mMenuShowPageNav.setChecked (isPageNavShowing ()); 
		mMenuPenMode.setChecked (isPenModeEnabled ()); 
	} 
	@Override public boolean onPrepareOptionsMenu (Menu menu) { 
		super.onPrepareOptionsMenu (menu); 
		mMenuGoToPage = menu.findItem (R.id.menu_action_goto_page); 
		mMenuToggleOverview = menu.findItem (R.id.menu_action_toggle_overview); 
//		mMenuRecents = menu.findItem (R.id.menu_action_recents); 
		mMenuShowPageNav = menu.findItem (R.id.menu_action_show_pg_nav); 
		mMenuPenMode = menu.findItem (R.id.menu_action_pen_mode); 
		mMenuPaste = menu.findItem (R.id.menu_action_paste); 
		updateMenuItems (); 
//		menu.findItem (R.id.menu_action_annotate).setVisible (hasImages); 
		return true; 
	} 
	PaperGenerator mPaperGenerator = new PaperGenerator (); 
	@Override public boolean onOptionsItemSelected (MenuItem item) { 
		switch (item.getItemId ()) { 
//			case R.id.menu_action_annotate: 
//				userSelectAnnotateOptions (); 
//				break; 
			case R.id.menu_action_goto_page: 
				userSelectPage (); 
				break; 
//			case R.id.menu_action_recents: 
//				if (recentFolders.size () > 1){ 
//					String parts [] = recentFolders.elementAt (1).split (";parent:"); 
//					String folderPaths [] = parts[0].split ("\t"); 
//					String parentPaths [] = parts[1] != null ? parts[1].split ("\t") 
//							: new String [0]; 
//					Intent goRecent = new Intent (this, NoteActivity.class); 
//					goRecent.putExtra (STATE_BROWSING_PATH, folderPaths); 
//					goRecent.putExtra (STATE_PARENT_BROWSE, parentPaths); 
//					startActivity (goRecent); // Start new activity. 
//					finish (); // Finish this activity. 
//				} 
//				break; 
			case R.id.menu_action_show_pg_nav: 
				item.setChecked (!item.isChecked ()); 
				showPageNav (item.isChecked ()); 
				break; 
			case R.id.menu_action_pen_mode: 
				item.setChecked (!item.isChecked ()); 
				enablePenMode (item.isChecked ()); 
				break; 
			case R.id.menu_action_toggle_overview: 
				prefs.edit ().putBoolean ("notes-overview", !prefs.getBoolean ("notes-overview", false)).apply (); 
				item.setChecked (prefs.getBoolean ("notes-overview", false)); 
				setNotesLayoutManager (); // Swap layout. 
				break; 
			case R.id.menu_action_paste: 
				if (mSubfoldersAdapter != null && mSubfoldersAdapter.mBrowsingFolder != null && 
							mSubfoldersAdapter.mBrowsingFolder.size () > 0) 
								SubfoldersAdapter.pasteFiles (mSubfoldersAdapter.mBrowsingFolder.elementAt (0), this); 
				break; 
			case R.id.menu_action_export_pages: 
				exportPages (); 
				break; 
			case R.id.menu_action_new_folder: 
				userRenameFile (this, null, ""); 
				break; 
			case R.id.menu_action_new_page: 
				// Insert a new graph paper at the end of the list: 
//				final boolean wasEmpty = !PngNotesAdapter.hasImages (mBrowsingFolders); 
				int wasImageCount = mNotesAdapter.countImages (); 
				mPaperGenerator.copyGraphPaper (this, mBrowsingFolders.elementAt (0), null); 
//				mPaperGenerator.makeGraphPaper (mBrowsingFolders.elementAt (0), null, 
//						new Runnable () { 
//							@Override public void run () { 
//								runOnUiThread (new Runnable () { 
//									@Override public void run () { 
										updateUserInterface (); 
										mNotesAdapter.reloadList (); 
				updateMenuItems (); 
//									} 
//								}); 
//							} 
//						}); 
				scrollToItem (wasImageCount + 
					mNotesAdapter.countHeaderViews ()); // Scroll to new page. 
				break; 
			case R.id.menu_action_settings: 
				openSettings (); 
				break; 
			case android.R.id.home: 
				goBack (); 
				break; 
			default: 
		} 
		return super.onOptionsItemSelected (item); 
	} 
	
	private void goBack () { 
		if (canGoBack ()) 
			finish (); 
	} 
	
	int getScrollPosition () { 
		if (mRvBigPages == null) return -1; 
		RecyclerView.LayoutManager notesLayoutManager = mRvBigPages.getLayoutManager (); 
		int scrollPosition; 
		View firstView; 
		int viewTop, viewTop2; 
		if (notesLayoutManager == mNoteOverviewLayoutManager) { 
			scrollPosition = mNoteOverviewLayoutManager.findFirstVisibleItemPosition (); 
			firstView = mNoteOverviewLayoutManager.findViewByPosition (scrollPosition); 
			viewTop = firstView != null ? firstView.getTop () : 0; 
			if (viewTop < 0) { 
				firstView = mNoteOverviewLayoutManager.findViewByPosition (scrollPosition + mOverviewColumnCount); 
				if (firstView != null) { 
					viewTop2 = firstView.getTop (); 
					if (Math.abs (viewTop2) < Math.abs (viewTop)) 
						scrollPosition += mOverviewColumnCount; 
				} 
			} 
		} else { 
			scrollPosition = mNotesLayoutManager.findFirstVisibleItemPosition (); 
			firstView = mNotesLayoutManager.findViewByPosition (scrollPosition); 
			viewTop = firstView != null ? firstView.getTop () : 0; 
			if (viewTop < 0) { 
				firstView = mNotesLayoutManager.findViewByPosition (scrollPosition + 1); 
				if (firstView != null) { 
					viewTop2 = firstView.getTop (); 
					if (Math.abs (viewTop2) < Math.abs (viewTop)) 
						scrollPosition += 1; 
				} 
			} 
		} 
		return scrollPosition; 
	} 
	float getScrollFraction () { 
		if (mRvBigPages == null) return -1; 
		RecyclerView.LayoutManager notesLayoutManager = mRvBigPages.getLayoutManager (); 
		int scrollPosition = getScrollPosition (); 
		View firstView; 
		float scrollFraction; 
		if (notesLayoutManager == mNoteOverviewLayoutManager) { 
			firstView = mNoteOverviewLayoutManager.findViewByPosition (scrollPosition); 
			scrollFraction = (float) (firstView != null ? firstView.getTop () : 0) / 
									 mRvBigPages.getWidth (); 
		} else { 
			firstView = mNotesLayoutManager.findViewByPosition (scrollPosition); 
			scrollFraction = (float) (firstView != null ? firstView.getTop () : 0) / 
									 mRvBigPages.getWidth (); 
		} 
		return scrollFraction; 
	} 
	
	int getPageIndex () { 
		return Math.max (mNotesLayoutManager.findFirstVisibleItemPosition () 
				- mNotesAdapter.countHeaderViews (), 0); 
	} 
	void setPageIndex (int index) { 
		if (index == 0) 
			mRvBigPages.scrollToPosition (0); 
		else mRvBigPages.scrollToPosition (index + mNotesAdapter.countHeaderViews ()); 
	} 
	void scrollToItem (int itemIndex) { 
		mDoNotResetInitialScrollYet = true; 
		initialScrollItemPosition = itemIndex; 
		initialScrollFraction = 0; 
		mRvBigPages.getViewTreeObserver ().addOnGlobalLayoutListener (mOnGlobalLayout); 
		mNotesAdapter.notifyDataSetChanged (); 
	} 
	int getScrollSpace () { 
		int pageIndex = getPageIndex (); 
		int position = pageIndex > 0 ? pageIndex + mNotesAdapter.countHeaderViews () : 0; 
		View currentView = mNotesLayoutManager.findViewByPosition (position); 
		return currentView != null ? currentView.getTop () : 0; 
	} 
	void addScrollSpace (int pixelsY) { 
		int nowTop = getScrollSpace (); 
		mRvBigPages.scrollBy (0, nowTop - pixelsY); 
	} 
	
//	void userSelectAnnotateOptions () { 
//		
//	} 
	static void userRenameFile (final NoteActivity activity, final @Nullable Vector<File> oldName, String userMessage) { 
		final EditText editText = (EditText) activity.getLayoutInflater ().inflate (R.layout.edit_file_name, 
				(ViewGroup) activity.findViewById (R.id.vMainRoot), false); 
		String currentName; 
		if (oldName == null) { 
			String baseTitle = activity.getString (R.string.label_new_folder); 
			int folderNumber = 1; 
			currentName = baseTitle; 
			while ((new File (currentName)).exists ()) { 
				folderNumber++; 
				currentName = baseTitle + " (" + folderNumber + ")"; 
			} 
		} else currentName = oldName.elementAt (0).getName (); 
		editText.setText (currentName); 
		editText.setSelection (0, currentName.length ()); 
		AlertDialog dialog = new AlertDialog.Builder (activity) 
									 .setTitle ( 
									 		oldName == null ? R.string.title_new_folder : 
													(oldName.elementAt (0).isDirectory () ? 
															 R.string.title_ren_folder : 
															 R.string.title_ren_file 
													)
									 ) 
									 .setMessage (userMessage) 
									 .setView (editText) 
									 .setPositiveButton (R.string.label_ok, new DialogInterface.OnClickListener () {
										 @Override public void onClick (DialogInterface dialogInterface, int i) {
											 String nowName = editText.getText ().toString (); 
											 boolean wasEmpty = activity.mSubfoldersAdapter.mList.length > 0; 
											 boolean success = true; 
											 if (oldName == null) { 
												 File nowFile = new File (activity.mBrowsingFolders.elementAt (0), nowName); 
												 if (nowFile.mkdirs ()) {
													 SubfoldersAdapter.OWNED_FOLDERS.edit ().putBoolean (nowFile.getPath (), true).apply (); 
												 	if (SubfoldersAdapter.HIDDEN_FOLDERS.contains (nowFile.getPath ())) { 
												 		// In case there is a folder like that on the hidden list, 
														// the user has manually deleted the folder using a file manager, 
														// and then is trying to create a folder with the same path here. 
												 		SubfoldersAdapter.HIDDEN_FOLDERS.edit ().remove (nowFile.getPath ()).apply (); 
													} 
													 // Success. Add the subfolders view if it wasn't there yet 
													 // (we don't add it in the initialization procedure if it 
													 // is empty, so check if it was empty to begin with): 
													 if (wasEmpty)
														 activity.mNotesAdapter.setHeaderItemViews ( 
														 		new View [] {activity.mRvSubfolderBrowser}); 
													 // Now open the new folder. 
													 Intent intent = new Intent (activity, 
																						NoteActivity.class); 
													 String [] toOpen = new String [] {nowFile.getPath ()}; 
													 String [] was = new String [activity.mBrowsingFolders.size ()]; 
													 for (int j = 0; j < activity.mBrowsingFolders.size (); j++) 
													 	was[j] = activity.mBrowsingFolders.elementAt (j).getAbsolutePath (); 
													 intent.putExtra (STATE_BROWSING_PATH, toOpen); 
													 intent.putExtra (STATE_PARENT_BROWSE, was);
													 activity.startActivity (intent); 
												 } else { 
													 // Try create again? 
													 userRenameFile (activity, null,
															 activity.getString (R.string.msg_could_not_new_folder)); 
												 } 
											 } else { 
											 	 SharedPreferences.Editor editor = SubfoldersAdapter.OWNED_FOLDERS.edit (); 
												 for (File oldFile : oldName) { 
													 File nowFile = new File (oldFile.getParentFile (), 
																					 nowName); 
													 if (oldFile.renameTo (nowFile)) { 
														 success &= true; 
														 boolean isOwnedByMe = SubfoldersAdapter.OWNED_FOLDERS.contains (oldFile.getPath ()); 
														 if (isOwnedByMe) { 
															 editor.remove (oldFile.getPath ()); 
															 editor.putBoolean (nowFile.getPath (), true); 
														 } 
													 } else { 
														 success = false; 
													 } 
												 } 
												 editor.apply (); 
												 if (success) { 
													 // Renamed. 
													 activity.mSubfoldersAdapter.reloadList ();
													 activity.mNotesAdapter.reloadList (); 
												 } else { 
													 // Try rename again? 
													 userRenameFile (activity, oldName,
															 activity.getString (oldName.elementAt (0).isDirectory () ? 
																				R.string.msg_could_not_ren_folder : 
																				R.string.msg_could_not_ren_file 
															 ) 
													 ); 
												 } 
											 } 
										 }
									 }) 
									 .setNegativeButton (R.string.label_cancel, null) 
									 .create (); 
		dialog.show (); 
	} 
	boolean mDeleteInProgress = false; 
	boolean userDeleteFiles (final Vector<Vector<File>> folders) { 
		if (mDeleteInProgress) { 
			AlertDialog dialog = new AlertDialog.Builder (this) 
					.setTitle (R.string.title_already_progress) 
					.setMessage (R.string.msg_previous_delete_progress) 
					.setPositiveButton (R.string.label_ok, null) 
					.create (); 
			dialog.show (); 
			return false; 
		} 
		mDeleteInProgress = true; 
		int ownedCount = 0; 
		int otherCount = 0; 
		// First count how many of these we own, and how many we don't own. 
		for (Vector<File> files : folders) { 
			for (File file : files) { 
				if (isOwnedByMe (file)) 
					ownedCount++; 
				else otherCount++; 
			} 
		} 
		final int sOwned = ownedCount; 
		final int sOther = otherCount; 
		// Next, display an appropriate confirmation prompt to the user. 
		String msg; 
		if (folders.elementAt (0).elementAt (0).isDirectory ()) { 
			msg = ownedCount != 0 && otherCount == 0 ? 
						  (ownedCount == 1 ? getString (R.string.msg_confirm_delete_owned_folders_s) : 
								   getString (R.string.msg_confirm_delete_owned_folders)) : 
						  (ownedCount != 0 /* && otherCount != 0 */ ? 
								   getString (R.string.msg_confirm_delete_mixed_folders) 
								   : 
								   (otherCount == 1 ? getString (R.string.msg_confirm_delete_other_folders_s) : 
											getString (R.string.msg_confirm_delete_other_folders)) 
						  ); 
		} else { 
			msg = (ownedCount + otherCount) == 1 ? 
						  getString (R.string.msg_confirm_delete_files_s) 
						  : getString (R.string.msg_confirm_delete_files); 
		} 
		msg = msg.replace ("[owned]", String.valueOf (ownedCount)); 
		msg = msg.replace ("[other]", String.valueOf (otherCount)); 
		msg = msg.replace ("[number]", String.valueOf (ownedCount + otherCount)); 
		AlertDialog dialog = new AlertDialog.Builder (this) 
				.setTitle (R.string.title_delete) 
				.setMessage (msg) 
				.setPositiveButton (R.string.label_delete, new DialogInterface.OnClickListener () { 
					@Override public void onClick (DialogInterface dialogInterface, int i) { 
						(new Thread () { 
							int successCount = 0; 
							int totalCount = 0; 
							SharedPreferences.Editor editor = null; 
							@Override public void run () { 
								// Task: delete all the folders, except hide the ones that we don't own. 
								if (sOther != 0) editor = SubfoldersAdapter.HIDDEN_FOLDERS.edit (); 
								for (Vector<File> files : folders) 
									for (File f : files) 
										delete (f, true); 
								if (editor != null) { 
									editor.apply (); 
									editor = null; 
								} 
								mDeleteInProgress = false; 
								runOnUiThread (new Runnable () { 
									@Override public void run () { 
										// Reload file lists: 
										mSubfoldersAdapter.reloadList (); 
										mNotesAdapter.reloadList (); 
										// Display status message: 
										Toast.makeText (NoteActivity.this, 
												getString (R.string.msg_files_deleted) 
														.replace ("[number]", String.valueOf (successCount)) 
														.replace ("[total]", String.valueOf (totalCount)), 
												Toast.LENGTH_SHORT) 
												.show (); 
									} 
								}); 
							} 
							@SuppressLint ("ApplySharedPref")
							boolean delete (File file, boolean checkOwnership) { 
								boolean success = true; 
								totalCount++; 
								if (file.isDirectory ()) { 
									if (checkOwnership && !isOwnedByMe (file)) { 
										if (editor == null) { 
											// This is a separate thread, so commit () should do just fine, as opposed to apply (). 
											SubfoldersAdapter.HIDDEN_FOLDERS.edit ().putBoolean (file.getPath (), true).commit (); 
										} else editor.putBoolean (file.getPath (), true); 
									} else { 
										File list[] = file.listFiles (); 
										for (File f : list) { 
											success &= delete (f, false); 
										} 
										success &= file.delete (); 
									} 
								} else success = file.delete (); 
								if (success) successCount++; 
								return success; 
							} 
						}).start (); 
					} 
				}) 
				.setNegativeButton (R.string.label_cancel, new DialogInterface.OnClickListener () { 
					@Override public void onClick (DialogInterface dialogInterface, int i) { 
						mDeleteInProgress = false; 
					} 
				}) 
				.setCancelable (true) 
				.setOnCancelListener (new DialogInterface.OnCancelListener () { 
					@Override public void onCancel (DialogInterface dialogInterface) { 
						mDeleteInProgress = false; 
					} 
				}) 
				.create (); 
		dialog.show (); 
		return true; 
	} 
	void userSelectPage () { 
		final EditText editText = (EditText) getLayoutInflater ().inflate (R.layout.edit_number, 
				(ViewGroup) findViewById (R.id.vMainRoot), false); 
		String currentPage = String.valueOf (getPageIndex () + 1); 
		editText.setText (currentPage); 
		editText.setSelection (0, currentPage.length ()); 
		AlertDialog dialog = new AlertDialog.Builder (this) 
									 .setTitle (R.string.title_goto_page) 
									 .setMessage (getString (R.string.msg_goto_page) 
											 .replace ("[current]", currentPage) 
											 .replace ("[total]", 
													 String.valueOf (mNotesAdapter.countImages ())) 
									 ) 
									 .setView (editText) 
									 .setPositiveButton (R.string.label_ok, new DialogInterface.OnClickListener () {
										 @Override public void onClick (DialogInterface dialogInterface, int i) {
											 int number; 
											 try { 
												 number = Integer.valueOf (editText.getText ().toString ()); 
											 } catch (NumberFormatException err) { 
												 return; 
											 } 
											 scrollToItem (number - 1 + 
											 mNotesAdapter.countHeaderViews ()); 
										 }
									 }) 
									 .setNegativeButton (R.string.label_cancel, null) 
									 .create (); 
		dialog.show (); 
	} 
	void userChangeBrushWidth () { 
		final EditText editText = (EditText) getLayoutInflater ().inflate (R.layout.edit_float, 
				(ViewGroup) findViewById (R.id.vMainRoot), false); 
		String currentWidth = String.valueOf (mNotesAdapter.mBrush); 
		editText.setText (currentWidth); 
		editText.setSelection (0, currentWidth.length ()); 
		AlertDialog dialog = new AlertDialog.Builder (this) 
									 .setTitle (R.string.title_brush_width)
									 .setMessage (R.string.msg_brush_width) 
									 .setView (editText) 
									 .setPositiveButton (R.string.label_ok, new DialogInterface.OnClickListener () {
										 @Override public void onClick (DialogInterface dialogInterface, int i) {
											 float number; 
											 try { 
												 number = Float.valueOf (editText.getText ().toString ()); 
											 } catch (NumberFormatException err) { 
												 return; 
											 } 
											 // Send this to the notes adapter: 
											 mNotesAdapter.mBrush = number; 
											 mNotesAdapter.notifyDataSetChanged (); 
											 // Update the view: 
											 updateBrushWidthTextShowing (); 
											 // Save this in the quick-preferences: 
											 prefs.edit ().putFloat ( 
											 		currentTool == TOOL_ERASER || currentTool == TOOL_WHITEOUT ? 
															"erase-width" : "write-width" 
											 		, number).apply (); 
										 }
									 }) 
									 .setNegativeButton (R.string.label_cancel, null) 
									 .create (); 
		dialog.show (); 
	} 
	void exportPages () { 
//		mNotesAdapter.reloadList (); // Just in case. 
//		(new AsyncTask<File [], Integer, File> () { 
//			boolean success = true; 
//			int mTotal = 1; 
//			@Override protected File doInBackground (File [] ... params) { 
//				try { 
//					success = true; 
//					return ZipRenderer.render (NoteActivity.this, params[0], 
//							mBrowsingFolders.elementAt (0).getName (), 
//							new ZipRenderer.OnRenderProgress () { 
//								@Override public void onRenderProgress (int current, int total) { 
//									mTotal = total; 
//									publishProgress (current); 
//								} 
//							}); 
//				} catch (IOException err) { 
//					err.printStackTrace (); 
//					success = false; 
//					return null; 
//				} 
//			} 
//			@Override protected void onPreExecute () { 
//				pbMainProgress.setProgress (0); 
//				pbMainProgress.setVisibility (View.VISIBLE); 
//			} 
//			@Override protected void onPostExecute (File result) { 
//				pbMainProgress.setVisibility (View.GONE); 
//				if (success) { 
//					Uri toFile = Uri.fromFile (result); 
//					Intent shareIntent = new Intent (); 
//					shareIntent.setAction (Intent.ACTION_SEND); 
//					shareIntent.putExtra (Intent.EXTRA_STREAM, toFile); 
//					shareIntent.setType ("application/zip"); 
//					startActivity (Intent.createChooser (shareIntent, 
//							getString (R.string.title_send_zip_to))); 
//				} else { 
//					Toast.makeText (NoteActivity.this, R.string.msg_could_not_export_io, 
//							Toast.LENGTH_SHORT).show (); 
//				} 
//			} 
//			@Override protected void onProgressUpdate (Integer ... values) { 
//				for (int current : values) { 
//					if (Build.VERSION.SDK_INT >= 24) { 
//						pbMainProgress.setProgress (current * 100 / mTotal, true); 
//					} else pbMainProgress.setProgress (current * 100 / mTotal); 
//				} 
//			} 
//		}).execute (mNotesAdapter.mList); 
		PdfMaker maker = new PdfMaker (this); 
		try { 
			File pdf = new File (mPictures, mBrowsingFolders.elementAt (0).getName () + ".pdf"); 
			maker.render (mNotesAdapter.mList, pdf); 
			Intent share = new Intent (Intent.ACTION_SEND, Uri.fromFile (pdf)); 
			share.setType ("application/pdf"); 
			if (pdf.exists ()) 
				startActivity (Intent.createChooser (share, "Share PDF")); 
			else Log.e ("NoteActivity", "PDF does not exist"); 
		} catch (IOException err) {
			Log.e ("NoteActivity", "IOException"); 
		} 
	} 
	void openSettings () { 
		
	} 
	
	RecyclerView mRvSubfolderBrowser = null; 
	SubfoldersAdapter mSubfoldersAdapter = null; 
	RecyclerView.LayoutManager mSubfoldersLayoutManager = null; 
	
	LinearLayoutManager mSubfoldersLinearLayoutManager = null; 
	GridLayoutManager mSubfoldersGridLayoutManager = null; 
	
	ScaleDetectorContainer mScalePageContainer = null; 
	SwipeableRecyclerView mRvBigPages = null; 
	PngNotesAdapter mNotesAdapter = null; 
	LinearLayoutManager mNotesLayoutManager = null; 
	GridLayoutManager mNoteOverviewLayoutManager = null; 
	
	RecyclerView mRvPenOptions = null; 
	PensAdapter mPensAdapter = null; 
	LinearLayoutManager mPensLayoutManager = null;
	
	View eraser = null; 
	View whiteout = null; 
	View hand = null; 
	View eraser_miniHand = null; 
	View whiteout_miniHand = null; 
	View brushWidthButton = null; 
	TextView brushWidthText = null; 
	
	View ivPageUp = null; 
	View ivPageDown = null; 
	
	ProgressBar pbMainProgress = null; 
	
	boolean animatingZoomLeave = false; 
	float notesZoomPivotX = 0; 
	float notesZoomPivotY = 0; 
	float targetZoomPivotX = 0; 
	float targetZoomPivotY = 0; 
	float targetZoomScale = 0.3f; 
	Handler mHandler = new Handler (); 
	Runnable mAnimateZoomLeaveStep = new Runnable () { 
		@Override public void run () { 
			if (!animatingZoomLeave) return; 
			float prevScale = mScalePageContainer.currentScale; 
			float now = prevScale * 0.95f + targetZoomScale * 0.05f; 
			notesZoomPivotX = notesZoomPivotX * 0.9f + targetZoomPivotX * 0.1f; 
			notesZoomPivotY = notesZoomPivotY * 0.9f + targetZoomPivotY * 0.1f; 
			mScalePageContainer.setScale (now, now, notesZoomPivotX, notesZoomPivotY); 
			if (Math.abs (notesZoomPivotX - targetZoomPivotX) < 50 && 
					Math.abs (notesZoomPivotY - targetZoomPivotY) < 50) { 
				// This checks if the little tile flew over to where it should go ... 
				setNotesLayoutManager (false); 
				return; // Done animating. 
			} 
			mHandler.postDelayed (this, 20); 
		} 
	}; 
	void animateZoomLeave (float startPivotX, float startPivotY) { 
		notesZoomPivotX = startPivotX; 
		notesZoomPivotY = startPivotY; 
		targetZoomPivotX = (float) (getScrollPosition () % mOverviewColumnCount) * mScalePageContainer.getWidth () / mOverviewColumnCount + 
								   (float) mScalePageContainer.getWidth () / (2 * mOverviewColumnCount); 
		targetZoomPivotY = startPivotY; 
		initialScrollFraction = targetZoomPivotY / (float) mScalePageContainer.getWidth (); 
		if (animatingZoomLeave) return; // Already animating. 
		animatingZoomLeave = true; 
		mAnimateZoomLeaveStep.run (); 
	} 
	
	boolean isBrowsingRootFolder () { 
		for (File folder : mBrowsingFolders) 
			if (folder.equals (mDCIM)) 
				return true; 
		return false; 
	} 
	boolean wantDisplaySubfoldersAsBig () { 
		return !mNotesAdapter.hasImages () || 
					   isBrowsingRootFolder (); 
	} 
	boolean mAlreadyHandling_OutOfMem = false; 
	void updateUserInterface () { 
		updateUserInterface (false); 
	} 
	void updateUserInterface (boolean firstTimeLoading) { 
		if (mNotesAdapter == null || mSubfoldersAdapter == null || hand == null) return; 
		// Subfolders: 
		if (!wantDisplaySubfoldersAsBig ()) { 
			// If there ARE images to display, then list the subfolders up above the images: 
			mSubfoldersLayoutManager = mSubfoldersLinearLayoutManager; 
		} else { 
			// If no images to browse, then it would look weird with a bunch of empty 
			// space below the subfolder browser; in this case, make the subfolder 
			// browser take up all the space it needs by assigning it a grid layout: 
			mSubfoldersLayoutManager = mSubfoldersGridLayoutManager; 
			// Clear the background color: 
			mRvSubfolderBrowser.setBackgroundColor (Color.TRANSPARENT); 
		} 
		mRvSubfolderBrowser.setLayoutManager (mSubfoldersLayoutManager); 
		// Notes: 
		setNotesLayoutManager (!firstTimeLoading); 
		// Update the views for the tool initially selected: 
		hand.findViewById (R.id.flEraser).setBackgroundResource (currentTool == TOOL_NONE ? 
																		 R.drawable.black_border : 0); 
		eraser.findViewById (R.id.flEraser).setBackgroundResource (currentTool == TOOL_ERASER ? 
																		   R.drawable.black_border : 0); 
		whiteout.findViewById (R.id.flWhiteout).setBackgroundResource (currentTool == TOOL_WHITEOUT ? 
																			   R.drawable.black_border : 0); 
		if (currentTool == TOOL_PEN) 
			mPensAdapter.setBorderedItemPosition (mPensAdapter.findColorPosition (currentColor)); 
		if (currentTool == TOOL_ERASER || currentTool == TOOL_WHITEOUT) 
			currentColor = PageView.ERASE_COLOR; 
		mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
		mNotesAdapter.mBrush = currentTool == TOOL_ERASER || currentTool == TOOL_WHITEOUT ? 
									   prefs.getFloat ("erase-width", 10.0f) 
									   : prefs.getFloat ("write-width", 1.0f); 
		mNotesAdapter.mTool = currentTool; 
		mNotesAdapter.mColor = currentColor; 
		mNotesAdapter.notifyDataSetChanged (); 
		// Set the brush width text: 
		updateBrushWidthTextShowing (); 
		// Initialize the pen mode things: 
		updateViewsForPenMode (); 
		// Show the pen options only if there are images available for editing: 
		mRvPenOptions.setVisibility (canEdit () ? View.VISIBLE : View.GONE); 
	} 
	void initUserInterface () { 
		ViewGroup vgRoot = (ViewGroup) findViewById (R.id.vMainRoot); 
		mScalePageContainer = findViewById (R.id.flScaleDetectorContainer); 
		// Subfolder browser RecyclerView: 
		mRvSubfolderBrowser = (RecyclerView) getLayoutInflater () 
				.inflate (R.layout.subfolder_browser, 
						vgRoot, 
						false); 
		mSubfoldersAdapter = new SubfoldersAdapter (this, mBrowsingFolders); 
		mNotesAdapter = new PngNotesAdapter (this, mBrowsingFolders, 
													new FileListCache.OnFilesChangedListener () { 
														@Override 
														public void onFilesChanged (File[][] list) { 
															mDoNotResetInitialScrollYet = false; 
															if (mRvBigPages != null) 
																mRvBigPages 
																	.getViewTreeObserver () 
																	.addOnGlobalLayoutListener (mOnGlobalLayout); 
															updateUserInterface (); 
															updateMenuItems (); 
														} 
														@Override 
														public void onFilesNoChange (File[][] list) { 
															mDoNotResetInitialScrollYet = false; 
															if (!mStillWaitingToScroll) 
																resetInitialScroll (); 
														} 
													}); 
		mNotesAdapter.setOnCacheUpdatedListener (new PngNotesAdapter.CacheUpdatedListener () { 
			@Override public void onThumbnailCacheUpdated () { 
				
			} 
			
			@Override public void onTileCacheUpdated () { 
				mNotesAdapter.notifyDataSetChanged (); 
			} 
		}); 
		mNotesAdapter.setNoteInteractListener (new PngNotesAdapter.OnNoteInteractListener () { 
			@Override public void onNotePageClicked (File itemFile, int listPosition) { 
				if (prefs.getBoolean ("notes-overview", false)) { 
					// A note tile was clicked from overview mode; zoom in on that note: 
					prefs.edit ().putBoolean ("notes-overview", false).apply (); // Go into full-page mode. 
					initialScrollItemPosition = listPosition; // Scroll to this. 
					initialScrollFraction = 0; 
					setNotesLayoutManager (false); // Don't recalculate; just use initialScrollItemPosition. 
				} 
			} 
			@Override public boolean onNotePageLongClicked (File itemFile, int listPosition) { 
				return false; 
			} 
		}); 
		mNotesAdapter.mErrorCallback = new PageView.ErrorCallback () { 
			@Override public void onBitmapOutOfMemory () { 
				if (mAlreadyHandling_OutOfMem) return; 
				mAlreadyHandling_OutOfMem = true; 
				Bundle extras = getIntent ().getExtras (); 
				boolean alreadyRestartedByError = extras != null && extras.getBoolean ("memory-error", false); 
				if (alreadyRestartedByError) { 
					new AlertDialog.Builder (NoteActivity.this) 
							.setTitle (R.string.title_out_of_mem) 
							.setMessage (R.string.msg_out_of_mem) 
							.create ().show (); 
				} else { 
					String current [] = new String [mBrowsingFolders.size ()]; 
					String parent [] = new String [mParentFolder.size ()]; 
					for (int i = 0; i < current.length; i++) 
						current[i] = mBrowsingFolders.elementAt (i).getAbsolutePath (); 
					for (int i = 0; i < parent.length; i++) 
						parent[i] = mParentFolder.elementAt (i).getAbsolutePath (); 
					Intent intent = new Intent (NoteActivity.this, NoteActivity.class); 
					intent.putExtra (STATE_BROWSING_PATH, current); 
					intent.putExtra (STATE_PARENT_BROWSE, parent); 
					intent.putExtra ("memory-error", true); 
					startActivity (intent); 
					finish (); 
				} 
			} 
		}; 
		mScalePageContainer.setOnScaleDoneListener (new ScaleDetectorContainer.OnScaleDone () { 
			@Override public void onZoomLeave (float pivotX, float pivotY) { 
				initialScrollItemPosition = getScrollPosition (); 
				prefs.edit ().putBoolean ("notes-overview", true).apply (); 
				animateZoomLeave (pivotX, pivotY); 
			} 
		}); 
		mSubfoldersLinearLayoutManager = new LinearLayoutManager (this, LinearLayoutManager.HORIZONTAL, false); 
		mSubfoldersGridLayoutManager = new GridLayoutManager (this, 3, LinearLayoutManager.VERTICAL, false); 
		mRvSubfolderBrowser.setAdapter (mSubfoldersAdapter); 
		
		// Image annotator layout managers: 
		mNotesLayoutManager = new LinearLayoutManager (this, LinearLayoutManager.VERTICAL, false); 
		mNoteOverviewLayoutManager = new GridLayoutManager (this, mOverviewColumnCount, LinearLayoutManager.VERTICAL, false); 
		
		// Image annotation RecyclerView: 
		mRvBigPages = findViewById (R.id.rvBigPages);
		mRvBigPages.setParentFolder (mParentFolder, mBrowsingFolders.elementAt (0).getName ()); 
		mRvBigPages.setAdapter (mNotesAdapter); 
		mRvBigPages.setSwipeCallback (new SwipeableRecyclerView.SwipeCallback () { 
			@Override public void swipeComplete (int direction) { 
				if (direction < 0) goBack (); 
			} 
			@Override public boolean canSwipe (int direction) { 
				return direction <= 0 && canGoBack () && mScalePageContainer.currentScale == 1; 
			} 
		}); 
		// Put it into the list only if it's not empty (to avoid a scroll bar problem): 
		if (mSubfoldersAdapter.mList.length > 0) 
			mNotesAdapter.setHeaderItemViews (new View [] {mRvSubfolderBrowser}); 
		// Pen options: 
		mRvPenOptions = findViewById (R.id.rvPenOptions); 
		mRvPenOptions.setLayoutManager (mPensLayoutManager = 
						new LinearLayoutManager (this, LinearLayoutManager.HORIZONTAL, false)); 
		mRvPenOptions.setAdapter (mPensAdapter = new PensAdapter (this)); 
		eraser = getLayoutInflater ().inflate (R.layout.icon_eraser, 
				vgRoot, false); 
		whiteout = getLayoutInflater ().inflate (R.layout.icon_whiteout, 
				vgRoot, false); 
		hand = getLayoutInflater ().inflate (R.layout.icon_eraser, 
				vgRoot, false); 
		brushWidthButton = getLayoutInflater ().inflate (R.layout.icon_brush_width, 
				vgRoot, false); 
		((ImageView) hand.findViewById (R.id.ivEraser)).setImageResource (R.mipmap.ic_hand); 
		hand.findViewById (R.id.ivMiniHand).setVisibility (View.GONE); 
		eraser_miniHand = eraser.findViewById (R.id.ivMiniHand); 
		whiteout_miniHand = whiteout.findViewById (R.id.ivMiniHand); 
		hand.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				// Select the hand tool ("none"). 
				hand.findViewById (R.id.flEraser).setBackgroundResource (R.drawable.black_border); 
				whiteout.findViewById (R.id.flWhiteout).setBackgroundResource (0); 
				eraser.findViewById (R.id.flEraser).setBackgroundResource (0); 
				mPensAdapter.setBorderedItemPosition (-1); 
				currentTool = TOOL_NONE; 
				prefs.edit ().putInt ("tool", currentTool).apply (); 
				// Update the touch event handler for PageView: 
				mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
				mNotesAdapter.mTool = currentTool; 
				mNotesAdapter.mBrush = prefs.getFloat ("write-width", 1.0f); 
				mNotesAdapter.notifyDataSetChanged (); 
				// Update the brush width text that's displayed to the user at the top: 
				updateBrushWidthTextShowing (); 
			} 
		}); 
		eraser.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				// Select the eraser as the tool. 
				hand.findViewById (R.id.flEraser).setBackgroundResource (0); 
				whiteout.findViewById (R.id.flWhiteout).setBackgroundResource (0); 
				eraser.findViewById (R.id.flEraser).setBackgroundResource (R.drawable.black_border); 
				mPensAdapter.setBorderedItemPosition (-1); 
				currentTool = TOOL_ERASER; 
				currentColor = PageView.ERASE_COLOR; 
				prefs.edit ().putInt ("tool", currentTool).apply (); 
				// Update the touch event handler for PageView: 
				mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
				mNotesAdapter.mColor = currentColor; 
				mNotesAdapter.mTool = currentTool; 
				mNotesAdapter.mBrush = prefs.getFloat ("erase-width", 10.0f); 
				mNotesAdapter.notifyDataSetChanged (); 
				// Update the brush width text that's displayed to the user at the top: 
				updateBrushWidthTextShowing (); 
			} 
		}); 
		whiteout.setOnClickListener (new View.OnClickListener () {
			@Override public void onClick (View view) { 
				// Select the whiteout as the tool. 
				hand.findViewById (R.id.flEraser).setBackgroundResource (0); 
				eraser.findViewById (R.id.flEraser).setBackgroundResource (0); 
				whiteout.findViewById (R.id.flWhiteout).setBackgroundResource (R.drawable.black_border); 
				mPensAdapter.setBorderedItemPosition (-1); 
				currentTool = TOOL_WHITEOUT; 
				currentColor = PageView.ERASE_COLOR; 
				prefs.edit ().putInt ("tool", currentTool).apply (); 
				// Update the touch event handler for PageView: 
				mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
				mNotesAdapter.mColor = currentColor; 
				mNotesAdapter.mTool = currentTool; 
				mNotesAdapter.mBrush = prefs.getFloat ("erase-width", 10.0f); 
				mNotesAdapter.notifyDataSetChanged (); 
				// Update the brush width text that's displayed to the user at the top: 
				updateBrushWidthTextShowing (); 
			}
		}); 
		mPensAdapter.setOnPenColorSelectedListener (new PensAdapter.OnPenColorSelectedListener () { 
			@Override public void onPenColorSelected (int penColor) { 
				hand.findViewById (R.id.flEraser).setBackgroundResource (0); 
				eraser.findViewById (R.id.flEraser).setBackgroundResource (0); 
				whiteout.findViewById (R.id.flWhiteout).setBackgroundResource (0); 
				mPensAdapter.setBorderedItemPosition (mPensAdapter.findColorPosition (penColor)); 
				currentTool = TOOL_PEN; 
				currentColor = penColor; 
				prefs.edit ().putInt ("tool", currentTool) 
						.putInt ("color", currentColor) 
						.apply (); 
				// Update the touch event handler for PageView: 
				mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
				mNotesAdapter.mTool = currentTool; 
				mNotesAdapter.mColor = currentColor; 
				mNotesAdapter.mBrush = prefs.getFloat ("write-width", 1.0f); 
				mNotesAdapter.notifyDataSetChanged (); 
				// Update the brush width text that's displayed to the user at the top: 
				updateBrushWidthTextShowing (); 
			} 
		}); 
		brushWidthText = brushWidthButton.findViewById (R.id.tvBrushWidth); 
		brushWidthButton.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				userChangeBrushWidth (); 
			} 
		}); 
		mPensAdapter.setHeaderItemViews (new View [] {hand, brushWidthButton, eraser, whiteout}); 
		
		// Page navigation buttons: 
		ivPageUp = findViewById (R.id.ivGoPageUp); 
		ivPageDown = findViewById (R.id.ivGoPageDown); 
		ivPageUp.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (mRvBigPages != null) 
					mRvBigPages.pageUp (); 
			} 
		}); 
		ivPageDown.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (mRvBigPages != null) 
					mRvBigPages.pageDown (); 
			} 
		}); 
		updateViewsForPageNav (); 
		
		// Progress bar: 
		pbMainProgress = findViewById (R.id.pbMainProgress); 
		pbMainProgress.setVisibility (View.GONE); 
		// Set things: 
		updateUserInterface (true); 
	} 
	private void setNotesLayoutManager () { 
		setNotesLayoutManager (true); 
	} 
	private void setNotesLayoutManager (boolean recalculateScrollValues) { 
		// Calculate scroll stuff for notes: 
		RecyclerView.LayoutManager oldManager = mRvBigPages.getLayoutManager (); 
		int scrollPosition = initialScrollItemPosition; 
		float scrollFraction = initialScrollFraction; 
		if (recalculateScrollValues) { 
			scrollPosition = getScrollPosition (); 
			scrollFraction = getScrollFraction (); 
		} 
		// Change the layout manager: 
		boolean useGrid = canShowAsGrid () && prefs.getBoolean ("notes-overview", false); 
		mScalePageContainer.allowZoomOut = !useGrid; // Allow zoom-out leave gesture if we're in full-page view. 
		mNotesAdapter.usePictureFrameBackground (useGrid); // Use picture frame tiles if they're in a grid. 
		mNotesAdapter.setSampleMode (useGrid ? PageView.SAMPLE_SPARSE : PageView.SAMPLE_NORMAL); 
		mNotesAdapter.setLoadMode (useGrid ? PageView.LOAD_TILE : PageView.LOAD_ORIGINAL); 
		mNotesAdapter.setPreviewMode (useGrid ? PageView.PREVIEW_THUMBNAIL_IF_EXISTS : PageView.PREVIEW_THUMBNAIL); 
		mRvBigPages.setLayoutManager (useGrid ? mNoteOverviewLayoutManager : mNotesLayoutManager); 
		// Wait for the RecyclerView to finish loading, and then scroll to the right place: 
		initialScrollItemPosition = scrollPosition; 
		initialScrollFraction = scrollFraction; 
		mDoNotResetInitialScrollYet = true; 
		mStillWaitingToScroll = true; 
		mRvBigPages.getViewTreeObserver ().addOnGlobalLayoutListener (mOnGlobalLayout); 
	} 
	void updateBrushWidthTextShowing () { 
		brushWidthText.setText (getString (R.string.label_brush_width) 
										.replace ("[width]", String.valueOf (mNotesAdapter.mBrush))); 
	} 
	boolean mStillWaitingToScroll = false; 
	ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayout = new ViewTreeObserver.OnGlobalLayoutListener () { 
		@Override  public void onGlobalLayout () { 
			// Check if we still need to do this or not (maybe it's the first time, and no need to scroll): 
			if (initialScrollItemPosition != 0 || 
						initialScrollFraction != 0) { 
				// Scroll to the initial scroll position, and forget the scroll position (so we 
				// don't mess up and reuse it when the user doesn't want us to): 
				int initialScrollItemSpace = (int) (mRvBigPages.getWidth () * initialScrollFraction); 
				mNotesLayoutManager.scrollToPositionWithOffset (initialScrollItemPosition, 
						initialScrollItemSpace); 
				mNoteOverviewLayoutManager.scrollToPositionWithOffset (initialScrollItemPosition, 
						initialScrollItemSpace); 
				animatingZoomLeave = false; // Cancel any zoom animations. 
				mScalePageContainer.setScale (1, 1, 0, 0); // Reset scale. 
				if (!mDoNotResetInitialScrollYet) 
					resetInitialScroll (); 
			} 
			mStillWaitingToScroll = false; 
			// Remove the extra layout overhead by removing this listener: 
			if (Build.VERSION.SDK_INT >= 16) 
				mRvBigPages.getViewTreeObserver ().removeOnGlobalLayoutListener (this); 
		} 
	}; 
	private void resetInitialScroll () { 
		initialScrollItemPosition = 0; 
		initialScrollFraction = 0; 
	} 
	boolean mDoNotResetInitialScrollYet = false; 
	
	void initActionBar () { 
		if (isBrowsingRootFolder ()) 
			setTitle (R.string.title_root_dir); 
		else 
			setTitle (getString (R.string.title_format).replace ("[folder]", 
					mBrowsingFolders.elementAt (0).getName ())); 
		ActionBar actionBar = getActionBar (); 
		if (actionBar != null) { 
			actionBar.setDisplayHomeAsUpEnabled (canGoBack ()); 
			actionBar.setDisplayShowHomeEnabled (true); 
			actionBar.setHomeButtonEnabled (canGoBack ()); 
		} 
	} 
	
	void enablePenMode (boolean penMode) { 
		prefs.edit ().putBoolean ("pen-mode", penMode).apply (); 
		updateViewsForPenMode (); 
	} 
	boolean isPenModeEnabled () { 
		return prefs.getBoolean ("pen-mode", false); 
	} 
	void updateViewsForPenMode () { 
		eraser_miniHand.setVisibility (isPenModeEnabled () ? View.VISIBLE : View.GONE); 
		whiteout_miniHand.setVisibility (isPenModeEnabled () ? View.VISIBLE : View.GONE); 
		mPensAdapter.mPenModeMiniHands = isPenModeEnabled (); 
		mPensAdapter.notifyDataSetChanged (); 
		mNotesAdapter.mPenMode = isPenModeEnabled (); 
		mNotesAdapter.notifyDataSetChanged (); 
	} 
	
	void showPageNav (boolean show) { 
		prefs.edit ().putBoolean ("page-nav", show).apply (); 
		updateViewsForPageNav (); 
	} 
	boolean isPageNavShowing () { 
		return prefs.getBoolean ("page-nav", false); 
	} 
	void updateViewsForPageNav () { 
		boolean pageNavVisible = isPageNavShowing () && hasImages (); 
		ivPageUp.setVisibility (pageNavVisible ? View.VISIBLE : View.GONE); 
		ivPageDown.setVisibility (pageNavVisible ? View.VISIBLE : View.GONE); 
	} 
	
	boolean canGoBack () { 
		return !isBrowsingRootFolder (); 
	} 
	boolean canEdit () { 
		return hasImages (); 
	} 
	
	@Override public void finish () { 
		if (mNotesAdapter != null) 
			mNotesAdapter.recycleBitmaps (); 
		super.finish (); 
	} 
	
	
} 
