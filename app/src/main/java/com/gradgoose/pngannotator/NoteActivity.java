package com.gradgoose.pngannotator;

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
import android.support.annotation.Nullable;
import android.support.v4.util.ArraySet;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.Vector;

public class NoteActivity extends Activity { 
	
	File mDCIM = null; // Keep track of the root camera images folder. 
	File mPictures = null; 
	
	File mSdDCIM = null; 
	File mSdPictures = null; 
	
	Vector<File> mBrowsingFolders = null; 
	private void setBrowsingPaths (@Nullable String browsingPaths []) { 
		if (browsingPaths == null) return; 
		Vector<File> files = new Vector<> (); 
		for (String path : browsingPaths) 
			files.add (new File (path)); 
		mBrowsingFolders = files; 
	} 
	
	static final String PREFS_NAME = "com.gradgoose.pngannotator.NoteActivity.prefs"; 
	static final String LEFTOFF_NAME = "com.gradgoose.pngannotator.NoteActivity.leftOff"; 
	static final String RECENTS_NAME = "com.gradgoose.pngannotator.NoteActivity.recents";  
	SharedPreferences prefs = null; 
	SharedPreferences leftOff = null; 
	SharedPreferences recents = null; 
	
	Vector<String> recentFolders = null; 
	
	static final int TOOL_NONE = 0; 
	static final int TOOL_PEN = 1; 
	static final int TOOL_ERASER = 2; 
	
	int currentTool = 0; // None. 
	int currentColor = Color.TRANSPARENT; 
	
	static final String STATE_BROWSING_PATH = "com.gradgoose.pngannotator.browse_path"; 
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
		Set<String> recentSet; 
		recentFolders = new Vector<> (10); // Can change 10 to something else later. From settings, eg. 
		if (recents.contains ("recent-folders") && 
					(recentSet = recents.getStringSet ("recent-folders", null)) != null) { 
			int i = 0; 
			for (String s : recentSet) { 
				if (i >= recentFolders.capacity ()) 
					break; // Let's only keep the LENGTH most recent items. 
				recentFolders.add (s); 
				i++; 
			} 
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
			if (savedInstanceState.containsKey (STATE_SCROLL_ITEM)) 
				initialScrollItemPosition = savedInstanceState.getInt (STATE_SCROLL_ITEM); 
			if (savedInstanceState.containsKey (STATE_SCROLL_FRACTION)) 
				initialScrollFraction = savedInstanceState.getInt (STATE_SCROLL_FRACTION); 
		} 
		// See if whoever started this activity wanted us to open any particular folder: 
		Intent sourceIntent = getIntent (); 
		Bundle extras = sourceIntent.getExtras (); 
		if (extras != null) { 
			if (mBrowsingFolders == null) { /* If the above did not give us a folder ... */
				if (extras.containsKey (STATE_BROWSING_PATH)) 
					setBrowsingPaths (extras.getStringArray (STATE_BROWSING_PATH)); 
			} 
			if (initialScrollItemPosition == 0 && extras.containsKey (STATE_SCROLL_ITEM)) 
				initialScrollItemPosition = extras.getInt (STATE_SCROLL_ITEM); 
			if (initialScrollFraction == 0 && extras.containsKey (STATE_SCROLL_FRACTION)) 
				initialScrollFraction = extras.getInt (STATE_SCROLL_FRACTION); 
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
			int foundIndex = -1; 
			for (int i = 0; i < recentFolders.size (); i++) { 
				if (!recentFolders.elementAt (i).equals (nowBrowsing)) continue; 
				foundIndex = i; 
			} 
			if (foundIndex == -1) { 
				if (recentFolders.size () == recentFolders.capacity ()) 
					recentFolders.remove (recentFolders.size () - 1); 
				recentFolders.add (0, nowBrowsing); 
			} else { 
				recentFolders.remove (foundIndex); 
				recentFolders.add (0, nowBrowsing); 
			} 
			// Save the recent folders list: 
			recentSet = new ArraySet<> (recentFolders.capacity ()); 
			recentSet.addAll (recentFolders); 
			recents.edit ().putStringSet ("recent-folders", recentSet).apply (); 
		} 
	} 
	// Save which folder we're working on, and what scroll position: 
	@Override protected void onSaveInstanceState (Bundle outState) { 
		// Put things into the saved instance state: 
		String paths [] = new String [mBrowsingFolders.size ()]; 
		for (int i = 0; i < mBrowsingFolders.size (); i++) 
			paths[i] = mBrowsingFolders.elementAt (i).getAbsolutePath (); 
		outState.putStringArray (STATE_BROWSING_PATH, paths);  
		int position = mNotesLayoutManager.findFirstVisibleItemPosition (); 
		outState.putInt (STATE_SCROLL_ITEM, position); 
		View firstView = mNotesLayoutManager.findViewByPosition (position); 
		float scrollFraction = (float) (firstView != null ? firstView.getTop () : 0) / 
									   mRvBigPages.getWidth (); 
		outState.putFloat (STATE_SCROLL_FRACTION, scrollFraction); 
	} 
	
	boolean mReloadOnNextResume = false; 
	@Override public void onPause () {
		// Update the "last page, left off" value: 
		int position = mNotesLayoutManager.findFirstVisibleItemPosition (); 
		View firstView = mNotesLayoutManager.findViewByPosition (position); 
		float scrollFraction = (float) (firstView != null ? firstView.getTop () : 0) / 
									   mRvBigPages.getWidth (); 
		leftOff.edit () 
				.putInt ("Scroll:" + mBrowsingFolders.elementAt (0).getPath (), position) 
				.putFloat ("ScrollFraction:" + mBrowsingFolders.elementAt (0).getPath (), 
						scrollFraction) 
				.apply (); 
		mReloadOnNextResume = true; 
		super.onPause (); 
	} 
	@Override public void onResume () { 
		super.onResume (); 
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
	@Override public boolean onCreateOptionsMenu (Menu menu) { 
		getMenuInflater ().inflate (R.menu.main_menu, menu); 
		return true; 
	} 
	@Override public boolean onPrepareOptionsMenu (Menu menu) { 
		super.onPrepareOptionsMenu (menu); 
		boolean hasImages = mNotesAdapter != null ? 
									mNotesAdapter.hasImages () : 
									PngNotesAdapter.hasImages (mBrowsingFolders); 
		mMenuGoToPage = menu.findItem (R.id.menu_action_goto_page); 
		mMenuGoToPage.setVisible (hasImages); 
		menu.findItem (R.id.menu_action_recents).setVisible (recentFolders.size () > 1 && 
			hasImages); 
		menu.findItem (R.id.menu_action_pen_mode).setChecked (isPenModeEnabled ()); 
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
			case R.id.menu_action_recents: 
				if (recentFolders.size () > 1){ 
					String folderPaths [] = recentFolders.elementAt (1).split ("\t"); 
					Intent goRecent = new Intent (this, NoteActivity.class); 
					goRecent.putExtra (STATE_BROWSING_PATH, folderPaths); 
					startActivity (goRecent); // Start new activity. 
					finish (); // Finish this activity. 
				} 
				break; 
			case R.id.menu_action_pen_mode: 
				item.setChecked (!item.isChecked ()); 
				enablePenMode (item.isChecked ()); 
				break; 
			case R.id.menu_action_export_pages: 
				exportPages (); 
				break; 
			case R.id.menu_action_new_folder: 
				userRenameFile (null, ""); 
				break; 
			case R.id.menu_action_new_page: 
				// Insert a new graph paper at the end of the list: 
				final boolean wasEmpty = !PngNotesAdapter.hasImages (mBrowsingFolders); 
				mPaperGenerator.copyGraphPaper (this, mBrowsingFolders.elementAt (0), null); 
//				mPaperGenerator.makeGraphPaper (mBrowsingFolders.elementAt (0), null, 
//						new Runnable () { 
//							@Override public void run () { 
//								runOnUiThread (new Runnable () { 
//									@Override public void run () { 
										if (wasEmpty) initUserInterface (); 
										else mNotesAdapter.reloadList (); 
				if (mMenuGoToPage != null) // Make sure the 'go to page' menu item is visible. 
					mMenuGoToPage.setVisible (true); // May not have been if this is the first page. 
//									} 
//								}); 
//							} 
//						}); 
				scrollToItem (mNotesAdapter.countImages () - 1 + 
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
		mNotesLayoutManager.scrollToPositionWithOffset (itemIndex, 0); 
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
	void userRenameFile (final @Nullable Vector<File> oldName, String userMessage) { 
		final EditText editText = (EditText) getLayoutInflater ().inflate (R.layout.edit_file_name, 
				(ViewGroup) findViewById (R.id.vMainRoot), false); 
		String currentName; 
		if (oldName == null) { 
			String baseTitle = getString (R.string.label_new_folder); 
			int folderNumber = 1; 
			currentName = baseTitle; 
			while ((new File (currentName)).exists ()) { 
				folderNumber++; 
				currentName = baseTitle + " (" + folderNumber + ")"; 
			} 
		} else currentName = oldName.elementAt (0).getName (); 
		editText.setText (currentName); 
		editText.setSelection (0, currentName.length ()); 
		AlertDialog dialog = new AlertDialog.Builder (this) 
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
											 boolean wasEmpty = mSubfoldersAdapter.mList.length > 0; 
											 boolean success = true; 
											 if (oldName == null) { 
												 File nowFile = new File (mBrowsingFolders.elementAt (0), nowName); 
												 if (nowFile.mkdirs ()) { 
													 // Success. Add the subfolders view if it wasn't there yet 
													 // (we don't add it in the initialization procedure if it 
													 // is empty, so check if it was empty to begin with): 
													 if (wasEmpty) 
														 mNotesAdapter.setHeaderItemViews ( 
														 		new View [] {mRvSubfolderBrowser}); 
													 // Now open the new folder. 
													 Intent intent = new Intent (NoteActivity.this, 
																						NoteActivity.class); 
													 String [] toOpen = new String [] {nowFile.getPath ()}; 
													 intent.putExtra (STATE_BROWSING_PATH, toOpen); 
													 startActivity (intent); 
												 } else { 
													 // Try create again? 
													 userRenameFile (null, 
															 getString (R.string.msg_could_not_new_folder)); 
												 } 
											 } else { 
												 for (File oldFile : oldName) { 
													 File nowFile = new File (oldFile.getParentFile (), 
																					 nowName); 
													 if (oldFile.renameTo (nowFile)) { 
														 success &= true; 
													 } else { 
														 success = false; 
													 } 
												 } 
												 if (success) { 
													 // Renamed. 
													 mSubfoldersAdapter.reloadList (); 
													 mNotesAdapter.reloadList (); 
												 } else { 
													 // Try rename again? 
													 userRenameFile (oldName, 
															 getString (oldName.elementAt (0).isDirectory () ? 
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
	void exportPages () { 
		mNotesAdapter.reloadList (); // Just in case. 
		(new AsyncTask<File [], Integer, File> () { 
			boolean success = true; 
			int mTotal = 1; 
			@Override protected File doInBackground (File [] ... params) { 
				try { 
					success = true; 
					return ZipRenderer.render (NoteActivity.this, params[0], 
							mBrowsingFolders.elementAt (0).getName (), 
							new ZipRenderer.OnRenderProgress () { 
								@Override public void onRenderProgress (int current, int total) { 
									mTotal = total; 
									publishProgress (current); 
								} 
							}); 
				} catch (IOException err) { 
					err.printStackTrace (); 
					success = false; 
					return null; 
				} 
			} 
			@Override protected void onPreExecute () { 
				pbMainProgress.setProgress (0); 
				pbMainProgress.setVisibility (View.VISIBLE); 
			} 
			@Override protected void onPostExecute (File result) { 
				pbMainProgress.setVisibility (View.GONE); 
				if (success) { 
					Uri toFile = Uri.fromFile (result); 
					Intent shareIntent = new Intent (); 
					shareIntent.setAction (Intent.ACTION_SEND); 
					shareIntent.putExtra (Intent.EXTRA_STREAM, toFile); 
					shareIntent.setType ("application/zip"); 
					startActivity (Intent.createChooser (shareIntent, 
							getString (R.string.title_send_zip_to))); 
				} else { 
					Toast.makeText (NoteActivity.this, R.string.msg_could_not_export_io, 
							Toast.LENGTH_SHORT).show (); 
				} 
			} 
			@Override protected void onProgressUpdate (Integer ... values) { 
				for (int current : values) { 
					if (Build.VERSION.SDK_INT >= 24) { 
						pbMainProgress.setProgress (current * 100 / mTotal, true); 
					} else pbMainProgress.setProgress (current * 100 / mTotal); 
				} 
			} 
		}).execute (mNotesAdapter.mList); 
	} 
	void openSettings () { 
		
	} 
	
	RecyclerView mRvSubfolderBrowser = null; 
	SubfoldersAdapter mSubfoldersAdapter = null; 
	RecyclerView.LayoutManager mSubfoldersLayoutManager = null; 
	
	RecyclerView mRvBigPages = null; 
	PngNotesAdapter mNotesAdapter = null; 
	LinearLayoutManager mNotesLayoutManager = null; 
	
	RecyclerView mRvPenOptions = null; 
	PensAdapter mPensAdapter = null; 
	LinearLayoutManager mPensLayoutManager = null;
	
	View eraser = null; 
	View hand = null; 
	View eraser_miniHand = null; 
	
	ProgressBar pbMainProgress = null; 
	
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
	void initUserInterface () { 
		// Subfolder browser RecyclerView: 
		mRvSubfolderBrowser = (RecyclerView) getLayoutInflater () 
				.inflate (R.layout.subfolder_browser, 
						(ViewGroup) findViewById (R.id.vMainRoot), 
						false); 
		mSubfoldersAdapter = new SubfoldersAdapter (this, mBrowsingFolders); 
		mNotesAdapter = new PngNotesAdapter (this, mBrowsingFolders); 
		if (!wantDisplaySubfoldersAsBig ()) { 
			// If there ARE images to display, then list the subfolders up above the images: 
			mSubfoldersLayoutManager = 
					new LinearLayoutManager (this, LinearLayoutManager.HORIZONTAL, false); 
		} else { 
			// If no images to browse, then it would look weird with a bunch of empty 
			// space below the subfolder browser; in this case, make the subfolder 
			// browser take up all the space it needs by assigning it a grid layout: 
			mSubfoldersLayoutManager = 
					new GridLayoutManager (this, 3, LinearLayoutManager.VERTICAL, false); 
			// Clear the background color: 
			mRvSubfolderBrowser.setBackgroundColor (Color.TRANSPARENT); 
		} 
		mRvSubfolderBrowser.setLayoutManager (mSubfoldersLayoutManager); 
		mRvSubfolderBrowser.setAdapter (mSubfoldersAdapter); 
		
		// Image annotation RecyclerView: 
		mRvBigPages = findViewById (R.id.rvBigPages); 
		mRvBigPages.setLayoutManager (mNotesLayoutManager = 
						new LinearLayoutManager (this, LinearLayoutManager.VERTICAL, false)); 
		mRvBigPages.setAdapter (mNotesAdapter); 
		// Put it into the list only if it's not empty (to avoid a scroll bar problem): 
		if (mSubfoldersAdapter.mList.length > 0) 
			mNotesAdapter.setHeaderItemViews (new View [] {mRvSubfolderBrowser}); 
		// Pen options: 
		mRvPenOptions = findViewById (R.id.rvPenOptions); 
		mRvPenOptions.setLayoutManager (mPensLayoutManager = 
						new LinearLayoutManager (this, LinearLayoutManager.HORIZONTAL, false)); 
		mRvPenOptions.setAdapter (mPensAdapter = new PensAdapter (this)); 
		eraser = getLayoutInflater ().inflate (R.layout.icon_eraser, 
				(ViewGroup) findViewById (R.id.vMainRoot), false); 
		hand = getLayoutInflater ().inflate (R.layout.icon_eraser, 
				(ViewGroup) findViewById (R.id.vMainRoot), false); 
		((ImageView) hand.findViewById (R.id.ivEraser)).setImageResource (R.mipmap.ic_hand); 
		hand.findViewById (R.id.ivMiniHand).setVisibility (View.GONE); 
		eraser_miniHand = eraser.findViewById (R.id.ivMiniHand); 
		hand.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				// Select the hand tool ("none"). 
				hand.findViewById (R.id.flEraser).setBackgroundResource (R.drawable.black_border); 
				eraser.findViewById (R.id.flEraser).setBackgroundResource (0); 
				mPensAdapter.setBorderedItemPosition (-1); 
				currentTool = TOOL_NONE; 
				prefs.edit ().putInt ("tool", currentTool).apply (); 
				// Update the touch event handler for PageView: 
				mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
				mNotesAdapter.mTool = currentTool; 
				mNotesAdapter.notifyDataSetChanged (); 
			} 
		}); 
		eraser.setOnClickListener (new View.OnClickListener () { 
			@Override public void onClick (View view) { 
				// Select the eraser as the tool. 
				hand.findViewById (R.id.flEraser).setBackgroundResource (0); 
				eraser.findViewById (R.id.flEraser).setBackgroundResource (R.drawable.black_border); 
				mPensAdapter.setBorderedItemPosition (-1); 
				currentTool = TOOL_ERASER; 
				currentColor = PageView.ERASE_COLOR; 
				prefs.edit ().putInt ("tool", currentTool).apply (); 
				// Update the touch event handler for PageView: 
				mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
				mNotesAdapter.mColor = currentColor; 
				mNotesAdapter.mTool = currentTool; 
				mNotesAdapter.mBrush = prefs.getFloat ("erase-width", 30.0f); 
				mNotesAdapter.notifyDataSetChanged (); 
			} 
		}); 
		mPensAdapter.setOnPenColorSelectedListener (new PensAdapter.OnPenColorSelectedListener () { 
			@Override public void onPenColorSelected (int penColor) { 
				hand.findViewById (R.id.flEraser).setBackgroundResource (0); 
				eraser.findViewById (R.id.flEraser).setBackgroundResource (0); 
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
				mNotesAdapter.mBrush = prefs.getFloat ("write-width", 3.0f); 
				mNotesAdapter.notifyDataSetChanged (); 
			} 
		}); 
		mPensAdapter.setHeaderItemViews (new View [] {hand, eraser}); 
		// Progress bar: 
		pbMainProgress = findViewById (R.id.pbMainProgress); 
		pbMainProgress.setVisibility (View.GONE); 
		// Update the views for the tool initially selected: 
		hand.findViewById (R.id.flEraser).setBackgroundResource (currentTool == TOOL_NONE ? 
				R.drawable.black_border : 0); 
		eraser.findViewById (R.id.flEraser).setBackgroundResource (currentTool == TOOL_ERASER ? 
				R.drawable.black_border : 0); 
		if (currentTool == TOOL_PEN) 
			mPensAdapter.setBorderedItemPosition (mPensAdapter.findColorPosition (currentColor)); 
		if (currentTool == TOOL_ERASER) 
			currentColor = PageView.ERASE_COLOR; 
		mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
		mNotesAdapter.mBrush = currentTool == TOOL_ERASER ? 
									   prefs.getFloat ("erase-width", 30.0f) 
									   : prefs.getFloat ("write-width", 3.0f); 
		mNotesAdapter.mTool = currentTool; 
		mNotesAdapter.mColor = currentColor; 
		mNotesAdapter.notifyDataSetChanged (); 
		// Initialize the pen mode things: 
		updateViewsForPenMode (); 
		// Show the pen options only if there are images available for editing: 
		mRvPenOptions.setVisibility (canEdit () ? View.VISIBLE : View.GONE); 
		// Wait for the RecyclerView to finish loading, and then scroll to the right place: 
		mRvBigPages.getViewTreeObserver ().addOnGlobalLayoutListener (new ViewTreeObserver.OnGlobalLayoutListener () { 
			@Override  public void onGlobalLayout () { 
				// Check if we still need to do this or not (maybe it's the first time, and no need to scroll): 
				if (initialScrollItemPosition == 0 && 
						initialScrollFraction == 0) return; 
				// Scroll to the initial scroll position, and forget the scroll position (so we 
				// don't mess up and reuse it when the user doesn't want us to): 
				int initialScrollItemSpace = (int) (mRvBigPages.getWidth () * initialScrollFraction); 
				mNotesLayoutManager.scrollToPositionWithOffset (initialScrollItemPosition, 
						initialScrollItemSpace); 
				initialScrollItemPosition = 0; 
				initialScrollFraction = 0; 
				// Remove the extra layout overhead by removing this listener: 
				if (Build.VERSION.SDK_INT >= 16) 
					mRvBigPages.getViewTreeObserver ().removeOnGlobalLayoutListener (this); 
			} 
		}); 
	} 
	
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
		mPensAdapter.mPenModeMiniHands = isPenModeEnabled (); 
		mPensAdapter.notifyDataSetChanged (); 
		mNotesAdapter.mPenMode = isPenModeEnabled (); 
		mNotesAdapter.notifyDataSetChanged (); 
	} 
	
	boolean canGoBack () { 
		return !isBrowsingRootFolder (); 
	} 
	boolean canEdit () { 
		return mNotesAdapter.hasImages (); 
	} 
	
	
} 
