package com.gradgoose.pennotepad;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Vector;

public class NoteActivity extends Activity { 
	static final String TAG = "NoteActivity"; 
	
	static final String HOME_TAG = ".pnhome"; 
	
	int mOverviewColumnCount = 3; 
	
	File mDCIM = null; // Keep track of the root camera images folder. 
	File mPictures = null; 
	
	File mSdDCIM = null; 
	File mSdPictures = null; 
	
	File mDownloads = null; 
	File mDocuments = null; 
	
	File mHomeFolder = null; 
	File mAllPictures = null; // Will be either "Pictures" or an equivalent in another language. 
	
	File [] mAdditionalDirsToShow = new File [0]; 
	
	private File findHomeFolder () { 
		File quickCheck1 = mDocuments != null ? new File (mDocuments, HOME_TAG) : null; 
		if (quickCheck1 != null && quickCheck1.exists ()) return quickCheck1; 
		File quickCheck2 = new File (mPictures, HOME_TAG); 
		if (quickCheck2.exists ()) return quickCheck2; 
		File result = findTaggedFolder (mDocuments, HOME_TAG); 
		if (result != null) return result; 
		result = findTaggedFolder (mPictures, HOME_TAG); 
		if (result != null) return result; 
		result = findTaggedFolder (mSdDCIM, HOME_TAG); 
		if (result != null) return result; 
		result = findTaggedFolder (mSdPictures, HOME_TAG); 
		if (result != null) return result; 
		result = findTaggedFolder (mDownloads, HOME_TAG); 
		return result; 
	} 
	FileFilter mJustFolders = new FileFilter () { 
		@Override public boolean accept (File file) { 
			return file.isDirectory (); 
		} 
	}; 
	private @Nullable File findTaggedFolder (@Nullable File searchIn, String tagFileName) { 
		if (searchIn == null) return null; 
		File test = new File (searchIn, tagFileName); 
		if (test.exists ()) return searchIn; 
		File list [] = searchIn.listFiles (mJustFolders); 
		if (list == null) 
			return null; 
		for (File child : list) { 
			File subResult = findTaggedFolder (child, tagFileName); 
			if (subResult != null) 
				return subResult; 
		} 
		return null; 
	} 
	private @Nullable File createHomeFolder () { 
		String homeName = getString (R.string.home_folder_name); 
		File homeFolder = mDocuments != null ? new File (mDocuments, homeName) : new File (mPictures, homeName); 
		if (!homeFolder.exists () && !homeFolder.mkdirs ()) return null; 
		File tagFile = new File (homeFolder, HOME_TAG); 
		if (!tagFile.exists ()) { 
			try {
				if (!tagFile.createNewFile ()) { 
					Log.i (TAG, "Home folder tag file already exists ... "); 
				} 
			} catch (IOException err) { 
				Log.e (TAG, "Error creating home folder tag file. I/O Exception: " + err.getMessage ()); 
			} 
		} 
		return homeFolder; 
	} 
	
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
	
	final SelectionManager selectionManager = new SelectionManager (this); 
	final CustomEditDialog customEditDialog = new CustomEditDialog (this); 
	
	RearrangeManager rearrangeManager = null; 
	
	Vector<String> recentFolders = null; 
	
	static final int TOOL_NONE = 0; 
	static final int TOOL_PEN = 1; 
	static final int TOOL_ERASER = 2; 
	static final int TOOL_WHITEOUT = 3; 
	
	int currentTool = 0; // None. 
	int currentColor = Color.TRANSPARENT; 
	
	// For time tracking: 
	long activityResumeTime = 0; 
	long activityPauseTime = 0; 
	
	static final String STATE_BROWSING_PATH = "com.gradgoose.pngannotator.browse_path"; 
	static final String STATE_PARENT_BROWSE = "com.gradgoose.pngannotator.parent_browse"; 
	static final String STATE_SCROLL_ITEM = "com.gradgoose.pngannotator.scroll_item"; 
	static final String STATE_SCROLL_SPACE = "com.gradgoose.pngannotator.scroll_space"; 
	static final String STATE_SCROLL_FRACTION = "com.gradgoose.pngannotator.scroll_fraction"; 
	
	int initialScrollItemPosition = 0; 
	float initialScrollFraction = 0; 
	
	boolean initReady = false; 
	
	@Override protected void onCreate (Bundle savedInstanceState) { 
		super.onCreate (savedInstanceState); 
		setContentView (R.layout.activity_main); 
		// Read the key-value quick options from last time: 
		prefs = getSharedPreferences (PREFS_NAME, MODE_PRIVATE); 
		leftOff = getSharedPreferences (LEFTOFF_NAME, MODE_PRIVATE); 
		recents = getSharedPreferences (RECENTS_NAME, MODE_PRIVATE); 
		SelectionManager.OWNED_FOLDERS = getSharedPreferences (OWNED_FOLDERS_NAME, MODE_PRIVATE);
		SelectionManager.HIDDEN_FOLDERS = getSharedPreferences (HIDDEN_FOLDERS_NAME, MODE_PRIVATE); 
		SelectionManager.PRIVATE_CLIPBOARD = getSharedPreferences (PRIVATE_CLIPBOARD_NAME, MODE_PRIVATE); 
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
		// Zoom state: 
		if (savedInstanceState != null) { 
			uiZoomedInFlag = savedInstanceState.containsKey ("zoomed-in-flag") && savedInstanceState.getBoolean ("zoomed-in-flag"); 
			uiZoomPivotX = savedInstanceState.containsKey ("zoom-pivot-x") ? savedInstanceState.getFloat ("zoom-pivot-x") : 0; 
			uiZoomPivotY = savedInstanceState.containsKey ("zoom-pivot-y") ? savedInstanceState.getFloat ("zoom-pivot-y") : 0; 
		} 
		// Selection listener: 
		selectionManager.selectionListeners.add (new SelectionManager.SelectionListener () { 
			@Override public void onSelectionBegin () { 
				
			} 
			@Override public void onSelectionChange () { 
				
			} 
			@Override public void onSelectionEnd () { 
				
			} 
			@Override public void onSelectionFilesChanged () { 
				updateUserInterface (); 
				updateMenuItems (); 
			} 
		}); 
		// Get the folder where digital camera images are stored: 
		mDCIM = Environment.getExternalStoragePublicDirectory ( 
				Environment.DIRECTORY_DCIM 
		); 
		mPictures = Environment.getExternalStoragePublicDirectory (
				Environment.DIRECTORY_PICTURES 
		); 
		// Get folders where PDF files may be stored: 
		mDownloads = Environment.getExternalStoragePublicDirectory (Environment.DIRECTORY_DOWNLOADS); 
		if (Build.VERSION.SDK_INT >= 19) 
			mDocuments = Environment.getExternalStoragePublicDirectory (Environment.DIRECTORY_DOCUMENTS); 
		// Get some external folders: 
		String inCard = mDCIM.getParent (); 
		String sdCard = System.getenv("SECONDARY_STORAGE"); 
		mSdDCIM = new File (sdCard + mDCIM.getAbsolutePath ().substring (inCard.length ())); 
		mSdPictures = new File (sdCard + mPictures.getAbsolutePath ().substring (inCard.length ())); 
		// Find our home folder: 
		mHomeFolder = findHomeFolder (); 
		// Take care of permissions: 
		boolean havePermission = false; 
		if (Build.VERSION.SDK_INT >= 23) { 
			if (checkSelfPermission (Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) { 
				havePermission = true; 
			} else { 
				inCreateState = savedInstanceState; 
				ActivityCompat.requestPermissions (this, 
						new String [] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 
						1); 
			} 
		} else havePermission = true; 
		if (havePermission) { 
			if (mHomeFolder == null) 
				mHomeFolder = createHomeFolder (); 
			if (mHomeFolder == null) 
				errorExit (R.string.msg_create_home_folder_error); 
			finishSettingUpOnCreate (savedInstanceState); 
		} 
	} 
	private Bundle inCreateState = null; 
	@Override public void onRequestPermissionsResult (int requestCode, @NonNull String [] permissions, 
													  @NonNull int [] grantResults) { 
		super.onRequestPermissionsResult (requestCode, permissions, grantResults); 
		if (requestCode == 1) { 
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) { 
				if (mHomeFolder == null) 
					mHomeFolder = createHomeFolder (); 
				if (mHomeFolder == null) 
					errorExit (R.string.msg_create_home_folder_error); 
				finishSettingUpOnCreate (inCreateState); 
				inCreateState = null; 
			} else { 
				errorExit (R.string.msg_create_home_folder_error); 
			} 
		} 
	} 
	private void errorExit (int msgID) { 
		Toast.makeText (this, msgID, Toast.LENGTH_SHORT) 
				.show (); 
		finish (); 
	} 
	private void finishSettingUpOnCreate (Bundle savedInstanceState) { 
		initReady = true; 
		// Make sure the pictures folder we show to the user is named correctly: 
		mAllPictures = new File (getString (R.string.title_pictures)); 
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
		if (mBrowsingFolders == null) // Else use the default, i.e. the app home folder. 
		{ 
			mBrowsingFolders = new Vector<> (); 
			mBrowsingFolders.add (mHomeFolder); 
		} 
		if (mParentFolder == null) { // Use the parents of mBrowsingFolders ... 
			mParentFolder = new Vector<> (mBrowsingFolders.capacity ()); 
			if (!isBrowsingRootFolder ()) { 
				// Only do this if this is not the root folder. 
				if (isBrowsingAllPicturesFolder ()) { 
					mParentFolder.add (mHomeFolder); 
				} else { 
					for (File file : mBrowsingFolders) { 
						File parent = file.getParentFile (); 
						if (!mParentFolder.contains (parent)) // Check this, just in case. 
							mParentFolder.add (parent); 
					} 
				} 
			} 
		} 
		// If we're in the root folder, then we should show links to the documents and downloads folders: 
		if (isBrowsingRootFolder ()) { 
			if (mDocuments == null) 
				mAdditionalDirsToShow = new File [] { mDownloads, mAllPictures }; 
			else mAdditionalDirsToShow = new File [] { mDocuments, mDownloads, mAllPictures }; 
		} else mAdditionalDirsToShow = new File [0]; 
		// Check to see if we have a record of what scroll position we were at last time: 
		if (initialScrollItemPosition == 0) // (only if we don't have one loaded from onRestore...) 
			initialScrollItemPosition = leftOff.getInt ("Scroll:" + mBrowsingFolders.get (0).getPath (), 0); 
		if (initialScrollFraction == 0) 
			initialScrollFraction = leftOff.getFloat ("ScrollFraction:" + mBrowsingFolders.get (0).getPath (), 0); 
		// Initialize views and the window title and icon: 
		initUserInterface (); // Views. 
		initActionBar (); // Title, Icon. 
		// Add this thing to the recent folders list: 
		if (!isBrowsingRootFolder () && (mNotesAdapter.hasImages () || isPDF ())) { 
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
		if (!initReady) { 
			super.onSaveInstanceState (outState); 
			return; 
		} 
		// Put things into the saved instance state: 
		String paths [] = new String [mBrowsingFolders.size ()]; 
		for (int i = 0; i < mBrowsingFolders.size (); i++) 
			paths[i] = mBrowsingFolders.elementAt (i).getAbsolutePath (); 
		outState.putStringArray (STATE_BROWSING_PATH, paths);  
		outState.putBoolean ("zoomed-in-flag", uiZoomedInFlag); 
		outState.putFloat ("zoom-pivot-x", uiZoomPivotX); 
		outState.putFloat ("zoom-pivot-y", uiZoomPivotY); 
		// Calculate scroll position: 
		int scrollPosition = getFocusedScrollPosition (); 
		float scrollFraction = getFocusedScrollFraction (); 
		outState.putInt (STATE_SCROLL_ITEM, scrollPosition); 
		outState.putFloat (STATE_SCROLL_FRACTION, scrollFraction); 
	} 
	
	boolean mReloadOnNextResume = false; 
	@Override public void onPause () { 
		if (!initReady) { 
			super.onPause (); 
			return; 
		} 
		// Calculate scroll position: 
		int scrollPosition = getFocusedScrollPosition (); 
		float scrollFraction = getFocusedScrollFraction (); 
		// Update the "last page, left off" value: 
		leftOff.edit () 
				.putInt ("Scroll:" + mBrowsingFolders.elementAt (0).getPath (), scrollPosition) 
				.putFloat ("ScrollFraction:" + mBrowsingFolders.elementAt (0).getPath (), 
						scrollFraction) 
				.apply (); 
		mReloadOnNextResume = true; 
		mPaused = true; 
//		if (mNotesAdapter != null) 
//			mNotesAdapter.cleanUp (); 
		saveTimeLog (); 
		super.onPause (); 
	} 
	@Override public void onResume () { 
		super.onResume (); 
		if (!initReady) return; 
		mPaused = false; 
		// Only reload if this is following an onPause (): 
		if (mReloadOnNextResume) { 
			mSubfoldersAdapter.reloadList (); 
			mNotesAdapter.reloadList (); 
			// This is because we don't want to reload following an onCreate (), 
			// in which case we would have just been done creating the adapters, 
			// so the lists are already up to date. 
		} 
		initTimeLog (); 
		updateUserInterface (initialScrollItemPosition != 0 || initialScrollFraction != 0); // In case some settings (such as 'grid view' toggle) changed. 
	} 
	
	void initTimeLog () { 
		activityResumeTime = System.currentTimeMillis (); 
	} 
	void saveTimeLog () { 
		if (!initReady) return; 
		activityPauseTime = System.currentTimeMillis (); 
		if (isTimeLogEnabled () && !isBrowsingRootFolder ()) try { 
			TimeLog.logTime (mBrowsingFolders.elementAt (0), activityResumeTime, activityPauseTime); 
		} catch (IOException err) { 
			Log.e (TAG, "Error writing to time-log file. "); 
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
		return initReady && (mNotesAdapter != null ? 
									mNotesAdapter.hasImages () : 
									PngNotesAdapter.hasImages (mBrowsingFolders)); 
	} 
	protected boolean isPDF () { 
		return initReady && (mNotesAdapter != null ? mNotesAdapter.mIsPDF : mBrowsingFolders.elementAt (0).getName ().toLowerCase ().endsWith (".pdf")); 
	} 
	protected boolean canShowAsGrid () { 
		return initReady && mNotesAdapter != null && (hasImages () || mNotesAdapter.mIsPDF); 
	} 
	protected boolean isUsingGrid () { 
		return canShowAsGrid () && prefs.getBoolean ("notes-overview", false); 
	} 
	void updateMenuItems () { 
		if (!initReady || mMenuGoToPage == null) return; // Return if these have not yet been initialized. 
		boolean hasImages = hasImages (); 
		boolean isPDF = isPDF (); 
		mMenuGoToPage.setVisible (hasImages || isPDF); 
		mMenuToggleOverview.setVisible (canShowAsGrid ()); 
		mMenuShowPageNav.setVisible (hasImages || isPDF); 
		mMenuPaste.setVisible (SelectionManager.hasClipboardItems ()); 
//		mMenuRecents.setVisible (recentFolders.size () > 1 && hasImages); 
		mMenuToggleOverview.setChecked (prefs.getBoolean ("notes-overview", false)); 
		mMenuShowPageNav.setChecked (isPageNavShowing ()); 
		mMenuPenMode.setChecked (isPenModeEnabled ()); 
	} 
	@Override public boolean onPrepareOptionsMenu (Menu menu) { 
		super.onPrepareOptionsMenu (menu); 
		menu.findItem (R.id.menu_action_enable_log).setChecked (isTimeLogEnabled ()); 
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
		if (!initReady) return super.onOptionsItemSelected (item); 
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
								selectionManager.paste (mSubfoldersAdapter.mBrowsingFolder.elementAt (0)); 
				break; 
			case R.id.menu_action_export_pages: 
				exportPages (); 
				break; 
			case R.id.menu_action_enable_log: 
				boolean nowTimeLogEnabled = !isTimeLogEnabled (); 
				if (nowTimeLogEnabled) { 
					// The user has just enabled time-log. 
					initTimeLog (); 
				} else { 
					// The user is disabling the time-log. Save what's logged so far, if applicable. 
					saveTimeLog (); 
				} 
				prefs.edit ().putBoolean ("time-log", nowTimeLogEnabled).apply (); 
				item.setChecked (nowTimeLogEnabled); 
				break; 
			case R.id.menu_action_view_time_log: 
				viewTimeLog (); 
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
		if (!initReady || mRvBigPages == null) return -1; 
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
		if (!initReady || mRvBigPages == null) return -1; 
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
	// Returns the Y position, within the RecyclerView, at the top of the visible rectangle: 
	float getVisibleCenterY () { 
		return getVisibleTopY () + 1 / (2 * mScalePageContainer.currentScale); 
	} 
	float getVisibleTopY () { 
		return (1 - 1 / mScalePageContainer.currentScale) * uiZoomPivotY; 
	} 
	float getVisibleBottomY () { 
		return getVisibleTopY () + 1 / mScalePageContainer.currentScale; 
	} 
	int getFocusedScrollPosition () { 
		if (!initReady || mRvBigPages == null || mRvBigPages.getChildCount () < 1) return -1; 
		return mNotesLayoutManager.findFirstVisibleItemPosition (); 
//		int childCount = mRvBigPages.getChildCount (); 
//		int increment = isUsingGrid () ? mOverviewColumnCount : 1; 
//		int position = 0; 
//		int bottomY = (int) (mRvBigPages.getHeight () * getVisibleBottomY ()); 
//		int prevCandidateTop = mRvBigPages.getChildAt (0).getTop (); 
//		RecyclerView.LayoutManager lm = mRvBigPages.getLayoutManager (); 
//		for (int i = 0; i < childCount; i++) { 
//			View child = mRvBigPages.getChildAt (i); 
//			int candidateTop = child.getTop (); 
//			if (candidateTop > bottomY || Math.abs (candidateTop) > Math.abs (prevCandidateTop)) 
//				break; 
//			position = lm.getPosition (child); 
//			prevCandidateTop = candidateTop; 
//		} 
//		return position - position % increment; 
	} 
	float getFocusedScrollFraction () { 
		int position = getFocusedScrollPosition (); 
		if (position < 0) return -1; 
		RecyclerView.LayoutManager lm = mRvBigPages.getLayoutManager (); 
		View viewByPosition = lm.findViewByPosition (position); 
		return (viewByPosition != null ? 
								(float) viewByPosition.getTop () / mRvBigPages.getWidth () 
								- getVisibleTopY () 
								: 0); 
	} 
	
	int getPageIndex () { 
		return initReady ? Math.max (mNotesLayoutManager.findFirstVisibleItemPosition () 
				- mNotesAdapter.countHeaderViews (), 0) : 0; 
	} 
	void setPageIndex (int index) { 
		if (!initReady) return; 
		if (index == 0) 
			mRvBigPages.scrollToPosition (0); 
		else mRvBigPages.scrollToPosition (index + mNotesAdapter.countHeaderViews ()); 
	} 
	void scrollToItem (int itemIndex) { 
		if (!initReady) return; 
		mDoNotResetInitialScrollYet = true; 
		initialScrollItemPosition = itemIndex; 
		initialScrollFraction = 0; 
		mRvBigPages.getViewTreeObserver ().addOnGlobalLayoutListener (mOnGlobalLayout); 
		mNotesAdapter.notifyDataSetChanged (); 
		mNotesAdapter.forceRedrawAll (); 
		rearrangeManager.update (); 
	} 
	int getScrollSpace () { 
		if (!initReady) return 0; 
		int pageIndex = getPageIndex (); 
		int position = pageIndex > 0 ? pageIndex + mNotesAdapter.countHeaderViews () : 0; 
		View currentView = mNotesLayoutManager.findViewByPosition (position); 
		return currentView != null ? currentView.getTop () : 0; 
	} 
	void addScrollSpace (int pixelsY) { 
		if (!initReady) return; 
		int nowTop = getScrollSpace (); 
		mRvBigPages.scrollBy (0, nowTop - pixelsY); 
	} 
	
//	void userSelectAnnotateOptions () { 
//		
//	} 
	static String [] splitFileName (String filename) { 
		int lastDot = filename.lastIndexOf ('.'); 
		if (lastDot > 0) { 
			return new String [] { 
					filename.substring (0, lastDot), 
					filename.substring (lastDot) 
			}; 
		} else return new String [] { filename, "" }; 
	} 
	static void userRenameFile (final NoteActivity activity, final @Nullable SelectionManager.FileEntry oldName, String userMessage) { 
		if (!activity.initReady) return; 
		final EditText editText = (EditText) activity.getLayoutInflater ().inflate (R.layout.edit_file_name, 
				(ViewGroup) activity.findViewById (R.id.vMainRoot), false); 
		String currentName = ""; 
		if (oldName != null) { 
			if (oldName.files != null && !oldName.files.isEmpty ()) 
				currentName = oldName.files.elementAt (0).getName (); 
			else if (oldName.singleFile != null) 
				currentName = oldName.singleFile.getName (); 
		} 
		if (currentName.isEmpty ()) { 
			String baseTitle = activity.getString (R.string.label_new_folder); 
			int folderNumber = 1; 
			currentName = baseTitle; 
			while ((new File (currentName)).exists ()) { 
				folderNumber++; 
				currentName = baseTitle + " (" + folderNumber + ")"; 
			} 
		} 
		String displayName = currentName; 
		final String fileExtension; 
		if (!oldName.isDirectory ()) { 
			String [] parts = splitFileName (currentName); 
			displayName = parts[0]; 
			fileExtension = parts[1]; 
		} else fileExtension = ""; 
		editText.setText (displayName); 
		editText.setSelection (0, displayName.length ()); 
		AlertDialog dialog = new AlertDialog.Builder (activity) 
									 .setTitle ( 
									 		oldName == null ? R.string.title_new_folder : 
													(oldName.isDirectory () ? 
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
													 SelectionManager.OWNED_FOLDERS.edit ().putBoolean (nowFile.getPath (), true).apply (); 
												 	if (SelectionManager.HIDDEN_FOLDERS.contains (nowFile.getPath ())) { 
												 		// In case there is a folder like that on the hidden list, 
														// the user has manually deleted the folder using a file manager, 
														// and then is trying to create a folder with the same path here. 
												 		SelectionManager.HIDDEN_FOLDERS.edit ().remove (nowFile.getPath ()).apply (); 
													} 
													 // Success. Add the subfolders view if it wasn't there yet 
													 // (we don't add it in the initialization procedure if it 
													 // is empty, so check if it was empty to begin with): 
													 if (wasEmpty)
														 activity.mNotesAdapter.setHeaderItemViews ( 
														 		new View [] {activity.mRvSubfolderBrowser}); 
												 	 // Notify everyone: 
													 for (SelectionManager.SelectionListener listener : activity.selectionManager.selectionListeners) 
														 listener.onSelectionFilesChanged (); 
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
											 	 SharedPreferences.Editor editor = SelectionManager.OWNED_FOLDERS.edit (); 
												 String suffix = nowName.endsWith (fileExtension) ? "" : fileExtension; 
												 if (oldName.files != null) for (File oldFile : oldName.files) { 
													 File nowFile = new File (oldFile.getParentFile (), 
																					 nowName + suffix); 
													 if (oldFile.renameTo (nowFile)) { 
														 success &= true; 
														 boolean isOwnedByMe = SelectionManager.OWNED_FOLDERS.contains (oldFile.getPath ()); 
														 if (isOwnedByMe) { 
															 editor.remove (oldFile.getPath ()); 
															 editor.putBoolean (nowFile.getPath (), true); 
														 } 
													 } else { 
														 success = false; 
													 } 
												 } 
												 if (oldName.singleFile != null) { 
												 	File nowFile = new File (oldName.singleFile.getParentFile (), 
															nowName + suffix); 
												 	if (oldName.singleFile.renameTo (nowFile)) { 
												 		success &= true; 
												 		boolean isOwnedByMe = SelectionManager.OWNED_FOLDERS.contains (oldName.singleFile.getPath ()); 
												 		if (isOwnedByMe) { 
												 			editor.remove (oldName.singleFile.getPath ()); 
												 			editor.putBoolean (nowFile.getPath (), true); 
														} 
													} else { 
												 		success = false; 
													} 
												 } 
												 editor.apply (); 
												 if (success) { 
													 // Renamed. 
													 // Notify everyone: 
													 for (SelectionManager.SelectionListener listener : activity.selectionManager.selectionListeners) 
														 listener.onSelectionFilesChanged (); 
												 } else { 
													 // Try rename again? 
													 userRenameFile (activity, oldName,
															 activity.getString (oldName.isDirectory () ? 
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
	boolean userDeleteFiles (final Vector<SelectionManager.FileEntry> folders) { 
		if (!initReady) return false; 
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
		for (SelectionManager.FileEntry entry : folders) { 
			if (entry.files != null) for (File file : entry.files) { 
				if (SelectionManager.isOwnedByMe (file)) 
					ownedCount++; 
				else otherCount++; 
			} 
			if (entry.singleFile != null && !entry.isMultipage) 
				ownedCount++; 
		} 
		final int sOwned = ownedCount; 
		final int sOther = otherCount; 
		// Next, display an appropriate confirmation prompt to the user. 
		String msg; 
		if (folders.elementAt (0).isDirectory ()) { 
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
								if (sOther != 0) editor = SelectionManager.HIDDEN_FOLDERS.edit (); 
								for (SelectionManager.FileEntry entry : folders) { 
									if (entry.files != null) 
										for (File f : entry.files) 
											delete (f, true); 
									if (entry.singleFile != null && !entry.isMultipage) 
										delete (entry.singleFile, false); 
								} 
								if (editor != null) { 
									editor.apply (); 
									editor = null; 
								} 
								mDeleteInProgress = false; 
								runOnUiThread (new Runnable () { 
									@Override public void run () { 
										// Notify everyone about the change: 
										for (SelectionManager.SelectionListener listener : selectionManager.selectionListeners) 
											listener.onSelectionFilesChanged (); 
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
									if (checkOwnership && !SelectionManager.isOwnedByMe (file)) { 
										if (editor == null) { 
											// This is a separate thread, so commit () should do just fine, as opposed to apply (). 
											SelectionManager.HIDDEN_FOLDERS.edit ().putBoolean (file.getPath (), true).commit (); 
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
		String currentPage = String.valueOf (getPageIndex () + 1); 
		customEditDialog.showDialog (getString (R.string.title_goto_page), 
				getString (R.string.msg_goto_page) 
						.replace ("[current]", currentPage) 
						.replace ("[total]", 
								String.valueOf (mNotesAdapter.countImages ())), 
				currentPage, 
				CustomEditDialog.INPUT_NUMBER, 
				getString (R.string.label_ok), 
				getString (R.string.label_cancel), 
				new Runnable () { 
					@Override public void run () { 
						int number; 
						try { 
							number = Integer.valueOf (customEditDialog.userResponse); 
						} catch (NumberFormatException err) { 
							return; 
						} 
						scrollToItem (number - 1 + 
											  mNotesAdapter.countHeaderViews ()); 
					} 
				}, 
				null, 
				null 
		); 
	} 
	void userChangeBrushWidth () { 
		if (!initReady) return; 
		String currentWidth = String.valueOf (mNotesAdapter.mBrush); 
		customEditDialog.showDialog (getString (R.string.title_brush_width), 
				getString (R.string.msg_brush_width), 
				currentWidth, 
				CustomEditDialog.INPUT_FLOAT, 
				getString (R.string.label_ok), 
				getString (R.string.label_cancel), 
				new Runnable () { 
					@Override public void run () { 
						float number; 
						try { 
							number = Float.valueOf (customEditDialog.userResponse); 
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
				}, 
				null, 
				null 
		); 
	} 
	
	void viewTimeLog () { 
		
	} 
	
	void exportPages () { 
		if (!initReady) return; 
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
//		}).execute (mNotesAdapter.mSubfolderList); 
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
		if (!initReady) return; 
		
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
	View ivPageHome = null; 
	View ivPageEnd = null; 
	
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
		if (!initReady) return; 
		notesZoomPivotX = startPivotX; 
		notesZoomPivotY = startPivotY; 
		targetZoomPivotX = (float) (getFocusedScrollPosition () % mOverviewColumnCount) * mScalePageContainer.getWidth () / mOverviewColumnCount + 
								   (float) mScalePageContainer.getWidth () / (2 * mOverviewColumnCount); 
		targetZoomPivotY = startPivotY; 
		initialScrollFraction = targetZoomPivotY / (float) mScalePageContainer.getWidth (); 
		if (animatingZoomLeave) return; // Already animating. 
		animatingZoomLeave = true; 
		mAnimateZoomLeaveStep.run (); 
	} 
	
	Vector<File> getPictureFolders () { 
		Vector<File> pictureFolders = new Vector<> (); 
		pictureFolders.add (mPictures); 
		pictureFolders.add (mDCIM); 
		if (mSdDCIM.exists ()) 
			pictureFolders.add (mSdDCIM); 
		if (mSdPictures.exists ()) 
			pictureFolders.add (mSdPictures); 
		return pictureFolders; 
	} 
	boolean isBrowsingAllPicturesFolder () { 
		return initReady && mBrowsingFolders.elementAt (0).equals (mPictures); 
	} 
	boolean isBrowsingRootFolder () { 
		return initReady && mBrowsingFolders.elementAt (0).equals (mHomeFolder); 
//		for (File folder : mBrowsingFolders) 
//			if (folder.equals (mDCIM)) 
//				return true; 
//		return false; 
	} 
	boolean wantDisplaySubfoldersAsBig () { 
//		return (!hasImages () && !isPDF ()) || 
//					   isBrowsingRootFolder (); 
		return true; 
	} 
	
	Runnable mReloadAll = new Runnable () { 
		@Override public void run () { 
			mAlreadyHandling_OutOfMem = false; 
			mNotesAdapter.cleanUp (); // Delete all bitmaps we made. 
			mNotesAdapter.notifyDataSetChanged (); // Just refresh, so the ones we need now are re-created. 
		} 
	}; 
	Runnable mRunUiThreadReloadAll = new Runnable () { 
		@Override public void run () { 
			runOnUiThread (mReloadAll); 
		} 
	}; 
	
	boolean mAlreadyHandling_OutOfMem = false; 
	void updateUserInterface () { 
		updateUserInterface (false); 
	} 
	void updateUserInterface (boolean firstTimeLoading) { 
		if (!initReady || mNotesAdapter == null || mSubfoldersAdapter == null || hand == null) return; 
		// Subfolders: 
		if (!wantDisplaySubfoldersAsBig ()) { 
			// If there ARE images to display, then list the subfolders up above the images: 
			mSubfoldersLayoutManager = mSubfoldersLinearLayoutManager; 
			mSubfoldersAdapter.matchParentWidth = false; // Wrap content for each folder/item. 
		} else { 
			// If no images to browse, then it would look weird with a bunch of empty 
			// space below the subfolder browser; in this case, make the subfolder 
			// browser take up all the space it needs by assigning it a grid layout: 
			mSubfoldersLayoutManager = mSubfoldersGridLayoutManager; 
			mSubfoldersAdapter.matchParentWidth = true; // Should match parent for grid items. 
			// Clear the background color: 
			mRvSubfolderBrowser.setBackgroundColor (Color.TRANSPARENT); 
		} 
		mRvSubfolderBrowser.setLayoutManager (mSubfoldersLayoutManager); 
		// Notes: 
		setNotesLayoutManager (!firstTimeLoading); 
		mScalePageContainer.setZoomedInScale (prefs.getFloat ("zoomed-in-scale", 2)); 
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
		mNotesAdapter.mColorMode = prefs.getInt ("color-mode", 0); 
		mNotesAdapter.notifyDataSetChanged (); 
		// Set the brush width text: 
		updateBrushWidthTextShowing (); 
		// Initialize the pen mode things: 
		updateViewsForPenMode (); 
		// Other updates: 
		updateViewsForPageNav (); 
		// Show the pen options only if there are images available for editing: 
		mRvPenOptions.setVisibility (canEdit () ? View.VISIBLE : View.GONE); 
		ViewGroup.LayoutParams lpNote = mScalePageContainer.getLayoutParams (); 
		FrameLayout.LayoutParams lpNoteFL = (FrameLayout.LayoutParams) lpNote; 
		lpNoteFL.topMargin = canEdit () ? getResources ().getDimensionPixelOffset (R.dimen.pen_options_height) : 0; 
		rearrangeManager.update (); 
	} 
	void updateZoomedInPivot () { 
		if (uiZoomedInFlag) { 
			float sizeFactorX = mRvBigPages.getWidth (); 
			float sizeFactorY = 0; //mRvBigPages.getHeight (); 
			mScalePageContainer.setScale (mScalePageContainer.zoomedInScale, 
					mScalePageContainer.zoomedInScale, 
					sizeFactorX * uiZoomPivotX, 
					sizeFactorY * (uiZoomPivotY != 0 ? uiZoomPivotY : mRvSubfolderBrowser.getHeight ())); 
		} 
	} 
	boolean uiZoomedInFlag = false; 
	float uiZoomPivotX = 0; 
	float uiZoomPivotY = 0; 
	void initUserInterface () { 
		ViewGroup vgRoot = (ViewGroup) findViewById (R.id.vMainRoot); 
		mScalePageContainer = findViewById (R.id.flScaleDetectorContainer); 
		// Subfolder browser RecyclerView: 
		mRvSubfolderBrowser = (RecyclerView) getLayoutInflater () 
				.inflate (R.layout.subfolder_browser, 
						vgRoot, 
						false); 
		mSubfoldersAdapter = new SubfoldersAdapter (this, mBrowsingFolders, mAdditionalDirsToShow, selectionManager); 
		mSubfoldersAdapter.mFolderClickListener = new SubfoldersAdapter.OnFolderClickListener () { 
			@Override public boolean onFolderClick (File folderClicked) { 
				if (folderClicked.equals (mAllPictures)) { 
					Intent viewPictures = new Intent (NoteActivity.this, NoteActivity.class); 
					Vector<File> pictureFolders = getPictureFolders (); 
					String [] paths = new String [pictureFolders.size ()]; 
					for (int i = 0; i < paths.length; i++) 
						paths[i] = pictureFolders.elementAt (i).getAbsolutePath (); 
					viewPictures.putExtra (STATE_BROWSING_PATH, paths); 
					startActivity (viewPictures); 
					return false; 
				} 
				return true; 
			} 
		}; 
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
													}, 
													selectionManager); 
		mSubfoldersAdapter.mAdditionalTouchInfo = mNotesAdapter; 
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
			@Override public boolean onPageLinkClicked (int pageIndex) { 
				if (!isUsingGrid ()) { 
					int headerViewCount = mNotesAdapter != null ? mNotesAdapter.countHeaderViews () : 0; 
					scrollToItem (pageIndex + headerViewCount); 
					return true; 
				} 
				return false; 
			} 
			@Override public boolean onUriLinkClicked (final String linkURI) { 
				Log.i (TAG, "Uri link: " + linkURI); 
				if (linkURI.startsWith ("mailto:")) { 
					// For mailto links, just open the mail app ... 
					// (no need to ask for confirmation from the user) 
					Intent emailIntent = new Intent (Intent.ACTION_SENDTO, Uri.parse (linkURI)); 
					startActivity (Intent.createChooser (emailIntent, getString (R.string.title_send_email))); 
					return true; 
				} 
				new AlertDialog.Builder (NoteActivity.this) 
						.setTitle (R.string.title_open_link) 
						.setMessage (getString (R.string.msg_open_link) 
								.replace ("[url]", linkURI) 
								.replace ("[br]", "\n") 
						) 
						.setPositiveButton (R.string.label_ok, new DialogInterface.OnClickListener () { 
							@Override public void onClick (DialogInterface dialogInterface, int i) { 
								Intent openIntent = new Intent (Intent.ACTION_VIEW, Uri.parse (linkURI)); 
								startActivity (openIntent); 
							} 
						}) 
						.setNegativeButton (R.string.label_cancel, new DialogInterface.OnClickListener () { 
							@Override public void onClick (DialogInterface dialogInterface, int i) { 
								
							} 
						}) 
						.show (); 
				return true; 
			} 
		}); 
		mNotesAdapter.mErrorCallback = new PageView.ErrorCallback () { 
			@Override public void onBitmapOutOfMemory () { 
				runOnUiThread (new Runnable () { 
					@Override public void run () { 
						Toast.makeText (NoteActivity.this, R.string.title_out_of_mem, Toast.LENGTH_SHORT).show (); 
					} 
				}); 
//				if (!mAlreadyHandling_OutOfMem) { 
//					mHandler.postDelayed (mRunUiThreadReloadAll, 500); 
//					mAlreadyHandling_OutOfMem = true; 
//				} 
			} 
			@Override public void onBitmapLoadError () {
				Toast.makeText (NoteActivity.this, R.string.msg_load_error, Toast.LENGTH_SHORT).show (); 
				Log.e (TAG, "Error loading bitmap. "); 
			} 
		}; 
		mScalePageContainer.setOnScaleDoneListener (new ScaleDetectorContainer.OnScaleDone () { 
			@Override public void onZoomLeave (float pivotX, float pivotY) { 
				initialScrollItemPosition = getFocusedScrollPosition (); 
				prefs.edit ().putBoolean ("notes-overview", true).apply (); 
				animateZoomLeave (pivotX, pivotY); 
			} 
			@Override public void onVerticalPanState (boolean isPanning) { 
				mRvBigPages.allowTouch = !isPanning; 
			} 
			@Override public void onZoomChanged (float scale) { 
				if (scale != 1f) { 
					float sizeFactorX = mRvBigPages.getWidth (); 
					float sizeFactorY = mRvBigPages.getHeight (); 
					if (sizeFactorX == 0 || sizeFactorY == 0) 
						return; // Just a safe-guard. 
					prefs.edit ().putFloat ("zoomed-in-scale", scale).apply (); 
					uiZoomedInFlag = true; 
					uiZoomPivotX = mScalePageContainer.nowPivotX / sizeFactorX; 
					uiZoomPivotY = mScalePageContainer.nowPivotY / sizeFactorY; 
					mScalePageContainer.setZoomedInScale (scale); 
				} else uiZoomedInFlag = false; 
			} 
			@Override public void onPivotChanged (float pivotX, float pivotY) { 
				float sizeFactorX = mRvBigPages.getWidth (); 
				float sizeFactorY = mRvBigPages.getHeight (); 
				if (sizeFactorX == 0 || sizeFactorY == 0) 
					return; // Just a safe-guard.  
				uiZoomPivotX = pivotX / sizeFactorX; 
				uiZoomPivotY = pivotY / sizeFactorY; 
			} 
		}); 
		mSubfoldersLinearLayoutManager = new MyLinearLayoutManager (this, LinearLayoutManager.HORIZONTAL, false); 
		mSubfoldersGridLayoutManager = new GridLayoutManager (this, 3, LinearLayoutManager.VERTICAL, false); 
		mRvSubfolderBrowser.setAdapter (mSubfoldersAdapter); 
		
		// Image annotator layout managers: 
		mNotesLayoutManager = new LinearLayoutManager (this, LinearLayoutManager.VERTICAL, false); 
		mNoteOverviewLayoutManager = new GridLayoutManager (this, mOverviewColumnCount, LinearLayoutManager.VERTICAL, false); 
		mNoteOverviewLayoutManager.setSpanSizeLookup (new GridLayoutManager.SpanSizeLookup () { 
			@Override public int getSpanSize (int position) { 
				return mNotesAdapter.getItemViewType (position) != 1 ? mOverviewColumnCount : 1; 
			} 
		}); 
		
		// Image annotation RecyclerView: 
		mRvBigPages = findViewById (R.id.rvBigPages);
		rearrangeManager = new RearrangeManager (this, mRvBigPages, findViewById (R.id.llRearrangeContainer)); 
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
		mRvBigPages.setScrollCallback (new SwipeableRecyclerView.ScrollCallback () { 
			@Override public void onScrollRecyclerView (int dx, int dy) { 
				rearrangeManager.update (); 
			} 
		}); 
		// Put it into the list only if it's not empty (to avoid a scroll bar problem): 
		if (mSubfoldersAdapter.getItemCount () > 0) 
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
		ivPageHome = findViewById (R.id.ivGoPageHome); 
		ivPageEnd = findViewById (R.id.ivGoPageEnd); 
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
		ivPageHome.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (mRvBigPages != null) 
					mRvBigPages.pageHome (); 
			} 
		}); 
		ivPageEnd.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				if (mRvBigPages != null) 
					mRvBigPages.pageEnd (); 
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
			scrollPosition = getFocusedScrollPosition (); 
			scrollFraction = getFocusedScrollFraction (); 
		} 
		// Change the layout manager: 
		boolean useGrid = isUsingGrid (); 
		mScalePageContainer.allowZoomOut = !useGrid; // Allow zoom-out leave gesture if we're in full-page view. 
		mNotesAdapter.setViewMode (useGrid ? PageView.VIEW_SMALL : PageView.VIEW_LARGE); 
		mRvBigPages.setLayoutManager (useGrid ? mNoteOverviewLayoutManager : mNotesLayoutManager); 
		// Wait for the RecyclerView to finish loading, and then scroll to the right place: 
		initialScrollItemPosition = scrollPosition; 
		initialScrollFraction = scrollFraction; 
		mDoNotResetInitialScrollYet = true; 
		mStillWaitingToScroll = true; 
		mRvBigPages.getViewTreeObserver ().addOnGlobalLayoutListener (mOnGlobalLayout); 
		// Set the flag whether to allow zooming or not: 
		boolean allowZoom = (hasImages () || isPDF ()) && !useGrid; 
		mScalePageContainer.allowZoomOut = allowZoom; 
		mScalePageContainer.allowZoomIn = allowZoom; 
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
				Log.i (TAG, "onGlobalLayout (); scrollToPositionWithOffset (" + initialScrollItemPosition + ", " + 
					initialScrollItemSpace + ")"); 
				mNotesLayoutManager.scrollToPositionWithOffset (initialScrollItemPosition, 
						initialScrollItemSpace); 
				mNoteOverviewLayoutManager.scrollToPositionWithOffset (initialScrollItemPosition, 
						initialScrollItemSpace); 
				animatingZoomLeave = false; // Cancel any zoom animations. 
				mScalePageContainer.setScale (); // Reset scale. 
				if (!mDoNotResetInitialScrollYet) 
					resetInitialScroll (); 
			} 
			mStillWaitingToScroll = false; 
			updateZoomedInPivot (); 
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
	
	boolean isTimeLogEnabled () { 
		return prefs.getBoolean ("time-log", false); 
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
		boolean pageNavVisible = isPageNavShowing () && (hasImages () || isPDF ()); 
		ivPageUp.setVisibility (pageNavVisible ? View.VISIBLE : View.GONE); 
		ivPageDown.setVisibility (pageNavVisible ? View.VISIBLE : View.GONE); 
		ivPageHome.setVisibility (pageNavVisible ? View.VISIBLE : View.GONE); 
		ivPageEnd.setVisibility (pageNavVisible ? View.VISIBLE : View.GONE); 
	} 
	
	boolean canGoBack () { 
		return !isBrowsingRootFolder (); 
	} 
	boolean canEdit () { 
		return hasImages () || isPDF (); // If there are images, or if it's a PDF document. 
	} 
	
	@Override public void finish () { 
//		if (mNotesAdapter != null) 
//			mNotesAdapter.cleanUp (); 
		super.finish (); 
	} 
	
	@Override public void onDestroy () { 
//		if (mNotesAdapter != null) 
//			mNotesAdapter.cleanUp (); 
		if (mNotesAdapter != null) 
			mNotesAdapter.mActivityRunning = false; 
		super.onDestroy (); 
	} 
	
	
} 
