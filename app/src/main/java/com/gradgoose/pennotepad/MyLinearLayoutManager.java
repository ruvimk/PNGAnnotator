package com.gradgoose.pennotepad;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

public class MyLinearLayoutManager extends LinearLayoutManager { 
	static final String TAG = "MyLinearLayoutManager"; 
	public MyLinearLayoutManager (Context context) { 
		super (context); 
	} 
	public MyLinearLayoutManager (Context context, int orientation, boolean reverse) { 
		super (context, orientation, reverse); 
	} 
	@Override public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) { 
		super.onLayoutChildren (recycler, state); 
		Log.i (TAG, "onLayoutChildren ()"); 
		printChildrenLayout (); 
	} 
	@Override public View onFocusSearchFailed (View focused, int focusDirection, 
											   RecyclerView.Recycler recycler, 
											   RecyclerView.State state) { 
		View result = super.onFocusSearchFailed (focused, focusDirection, recycler, state); 
		Log.i (TAG, "onFocusSearchFailed ()"); 
		printChildrenLayout (); 
		return result; 
	} 
	protected void printChildrenLayout () { 
		for (int i = 0; i < getChildCount (); i++) { 
			View child = getChildAt (i); 
			Log.i (TAG, "child[" + i + "]: at (" + child.getLeft () + ", " + child.getTop () + ") and size (" + 
								child.getWidth () + ", " + child.getHeight () + ")"); 
		} 
	} 
} 
