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

import java.io.File;

public class NoteActivity extends Activity { 
	
	File mDCIM = null; // Keep track of the root camera images folder. 
	
	File mBrowsingFolder = null; 
	private void setBrowsingPath (@Nullable String browsingPath) { 
		if (browsingPath == null) return; 
		mBrowsingFolder = new File (browsingPath); 
	} 
	
	static final String STATE_BROWSING_PATH = "com.gradgoose.pngannotator.browse_path"; 
	
	@Override protected void onCreate (Bundle savedInstanceState) { 
		super.onCreate (savedInstanceState); 
		setContentView (R.layout.activity_main); 
		mDCIM = Environment.getExternalStoragePublicDirectory ( 
				Environment.DIRECTORY_DCIM 
		); 
		if (savedInstanceState != null) { 
			if (savedInstanceState.containsKey (STATE_BROWSING_PATH)) 
				setBrowsingPath (savedInstanceState.getString (STATE_BROWSING_PATH)); 
		} 
		if (mBrowsingFolder == null) { /* If the above did not give us a folder ... */ 
			Intent sourceIntent = getIntent (); 
			Bundle extras = sourceIntent.getExtras (); 
			if (extras != null) { 
				if (extras.containsKey (STATE_BROWSING_PATH)) 
					setBrowsingPath (extras.getString (STATE_BROWSING_PATH)); 
			} 
		} 
		if (mBrowsingFolder == null) // Else use the default of the DCIM folder. 
			mBrowsingFolder = mDCIM; 
		initUserInterface (); 
		initActionBar (); 
	} 
	@Override protected void onSaveInstanceState (Bundle outState) { 
		outState.putString (STATE_BROWSING_PATH, mBrowsingFolder.getAbsolutePath ()); 
	} 
	
	@Override public boolean onCreateOptionsMenu (Menu menu) { 
		getMenuInflater ().inflate (R.menu.main_menu, menu); 
		return true; 
	} 
	@Override public boolean onPrepareOptionsMenu (Menu menu) { 
		super.onPrepareOptionsMenu (menu); 
		boolean hasImages = PngNotesAdapter.hasImages (mBrowsingFolder); 
		menu.findItem (R.id.menu_action_goto_page).setVisible (hasImages); 
		menu.findItem (R.id.menu_action_annotate).setVisible (hasImages); 
		return true; 
	} 
	@Override public boolean onOptionsItemSelected (MenuItem item) { 
		switch (item.getItemId ()) { 
			case R.id.menu_action_annotate: 
				userSelectAnnotateOptions (); 
				break; 
			case R.id.menu_action_goto_page: 
				userSelectPage (); 
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
	
	void userSelectAnnotateOptions () { 
		
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
	
	boolean canGoBack () { 
		return !mBrowsingFolder.equals (mDCIM); 
	} 
	
	
} 
