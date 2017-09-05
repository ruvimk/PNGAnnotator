package com.gradgoose.pngannotator;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import java.io.File;

public class NoteActivity extends Activity { 
	
	File mDCIM = null; // Keep track of the root camera images folder. 
	
	File mBrowsingFolder = null; 
	private void setBrowsingPath (@Nullable String browsingPath) { 
		if (browsingPath == null) return; 
		mBrowsingFolder = new File (browsingPath); 
	} 
	
	static final String PREFS_NAME = "com.gradgoose.pngannotator.NoteActivity.prefs"; 
	SharedPreferences prefs = null; 
	
	static final int TOOL_NONE = 0; 
	static final int TOOL_PEN = 1; 
	static final int TOOL_ERASER = 2; 
	
	int currentTool = 0; // None. 
	int currentColor = Color.TRANSPARENT; 
	
	static final String STATE_BROWSING_PATH = "com.gradgoose.pngannotator.browse_path"; 
	static final String STATE_SCROLL_ITEM = "com.gradgoose.pngannotator.scroll_item"; 
	
	int initialScrollItemPosition = 0; 
	
	@Override protected void onCreate (Bundle savedInstanceState) { 
		super.onCreate (savedInstanceState); 
		setContentView (R.layout.activity_main); 
		// Read the key-value quick options from last time: 
		prefs = getSharedPreferences (PREFS_NAME, MODE_PRIVATE); 
		currentTool = prefs.getInt ("tool", currentTool); 
		currentColor = prefs.getInt ("color", currentColor); 
		// Get the folder where digital camera images are stored: 
		mDCIM = Environment.getExternalStoragePublicDirectory ( 
				Environment.DIRECTORY_DCIM 
		); 
		// See if we had a folder already open last time that we can reopen now: 
		if (savedInstanceState != null) { 
			if (savedInstanceState.containsKey (STATE_BROWSING_PATH)) 
				setBrowsingPath (savedInstanceState.getString (STATE_BROWSING_PATH)); 
			if (savedInstanceState.containsKey (STATE_SCROLL_ITEM)) 
				initialScrollItemPosition = savedInstanceState.getInt (STATE_SCROLL_ITEM); 
		} 
		// See if whoever started this activity wanted us to open any particular folder: 
		Intent sourceIntent = getIntent (); 
		Bundle extras = sourceIntent.getExtras (); 
		if (extras != null) { 
			if (mBrowsingFolder == null) { /* If the above did not give us a folder ... */
				if (extras.containsKey (STATE_BROWSING_PATH)) 
					setBrowsingPath (extras.getString (STATE_BROWSING_PATH)); 
			} 
			if (initialScrollItemPosition == 0) 
				initialScrollItemPosition = extras.getInt (STATE_SCROLL_ITEM); 
		} 
		if (mBrowsingFolder == null) // Else use the default of the DCIM folder. 
			mBrowsingFolder = mDCIM; 
		// Initialize views and the window title and icon: 
		initUserInterface (); // Views. 
		initActionBar (); // Title, Icon. 
	} 
	// Save which folder we're working on, and what scroll position: 
	@Override protected void onSaveInstanceState (Bundle outState) { 
		outState.putString (STATE_BROWSING_PATH, mBrowsingFolder.getAbsolutePath ()); 
		outState.putInt (STATE_SCROLL_ITEM, getPageIndex ()); 
	} 
	
	@Override public boolean onCreateOptionsMenu (Menu menu) { 
		getMenuInflater ().inflate (R.menu.main_menu, menu); 
		return true; 
	} 
	@Override public boolean onPrepareOptionsMenu (Menu menu) { 
		super.onPrepareOptionsMenu (menu); 
		boolean hasImages = PngNotesAdapter.hasImages (mBrowsingFolder); 
		menu.findItem (R.id.menu_action_goto_page).setVisible (hasImages); 
		menu.findItem (R.id.menu_action_pen_mode).setChecked (isPenModeEnabled ()); 
//		menu.findItem (R.id.menu_action_annotate).setVisible (hasImages); 
		return true; 
	} 
	@Override public boolean onOptionsItemSelected (MenuItem item) { 
		switch (item.getItemId ()) { 
//			case R.id.menu_action_annotate: 
//				userSelectAnnotateOptions (); 
//				break; 
			case R.id.menu_action_goto_page: 
				userSelectPage (); 
				break; 
			case R.id.menu_action_pen_mode: 
				item.setChecked (!item.isChecked ()); 
				enablePenMode (item.isChecked ()); 
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
	
//	void userSelectAnnotateOptions () { 
//		
//	} 
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
											 setPageIndex (number - 1); 
										 }
									 }) 
									 .setNegativeButton (R.string.label_cancel, null) 
									 .create (); 
		dialog.show (); 
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
	
	void initUserInterface () { 
		// Subfolder browser RecyclerView: 
		mRvSubfolderBrowser = (RecyclerView) getLayoutInflater () 
				.inflate (R.layout.subfolder_browser, 
						(ViewGroup) findViewById (R.id.vMainRoot), 
						false); 
		if (PngNotesAdapter.hasImages (mBrowsingFolder)) { 
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
		mRvSubfolderBrowser.setAdapter (mSubfoldersAdapter = 
												new SubfoldersAdapter (this, mBrowsingFolder)); 
		
		// Image annotation RecyclerView: 
		mRvBigPages = findViewById (R.id.rvBigPages); 
		mRvBigPages.setLayoutManager (mNotesLayoutManager = 
						new LinearLayoutManager (this, LinearLayoutManager.VERTICAL, false)); 
		mRvBigPages.setAdapter (mNotesAdapter = new PngNotesAdapter (this, mBrowsingFolder)); 
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
				prefs.edit ().putInt ("tool", currentTool).apply (); 
				// Update the touch event handler for PageView: 
				mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
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
				mNotesAdapter.notifyDataSetChanged (); 
			} 
		}); 
		mPensAdapter.setHeaderItemViews (new View [] {hand, eraser}); 
		// Update the views for the tool initially selected: 
		hand.findViewById (R.id.flEraser).setBackgroundResource (currentTool == TOOL_NONE ? 
				R.drawable.black_border : 0); 
		eraser.findViewById (R.id.flEraser).setBackgroundResource (currentTool == TOOL_ERASER ? 
				R.drawable.black_border : 0); 
		if (currentTool == TOOL_PEN) 
			mPensAdapter.setBorderedItemPosition (mPensAdapter.findColorPosition (currentColor)); 
		mNotesAdapter.mToolMode = currentTool != TOOL_NONE; 
		mNotesAdapter.notifyDataSetChanged (); 
		// Initialize the pen mode things: 
		updateViewsForPenMode (); 
		// Show the pen options only if there are images available for editing: 
		mRvPenOptions.setVisibility (canEdit () ? View.VISIBLE : View.GONE); 
		// Scroll to the initial scroll position, and forget the scroll position (so we 
		// don't mess up and reuse it when the user doesn't want us to): 
		setPageIndex (initialScrollItemPosition); 
		initialScrollItemPosition = 0; 
	} 
	
	void initActionBar () { 
		setTitle (getString (R.string.title_format).replace ("[folder]", 
				mBrowsingFolder.getName ())); 
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
		return !mBrowsingFolder.equals (mDCIM); 
	} 
	boolean canEdit () { 
		return PngNotesAdapter.hasImages (mBrowsingFolder); 
	} 
	
	
} 
