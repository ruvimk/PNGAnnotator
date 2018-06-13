package com.gradgoose.pennotepad;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import java.util.Vector;

public class RearrangeManager { 
	final Context mContext; 
	final RecyclerView mRecyclerView; 
	final View mRearrangeContainer; 
	final TextView tvRearrangeTitle; 
	final View btnRearrangeMoveUp; 
	final View btnRearrangeMoveDown; 
	final Vector<RearrangeRequestListener> onRearrangeRequestListeners = new Vector<> (); 
	int selectedItemCount = 0; 
	boolean visible = false; 
	int position_start = 0; 
	int position_end = 0; 
	Vector<Integer> mItemPositions = new Vector<> (); 
	View.OnClickListener mBtnOnClick = new View.OnClickListener () { 
		@Override public void onClick (View view) { 
			int direction = view == btnRearrangeMoveDown ? +1 : -1; 
			for (RearrangeRequestListener listener : onRearrangeRequestListeners) 
				listener.rearrangeOnRequestMove (position_start, position_end, direction); 
		} 
	}; 
	public RearrangeManager (Context context, RecyclerView recyclerView, View rearrangeCountainer) { 
		mContext = context; 
		mRecyclerView = recyclerView; 
		mRearrangeContainer = rearrangeCountainer; 
		tvRearrangeTitle = rearrangeCountainer.findViewById (R.id.tvRearrangeTitle); 
		btnRearrangeMoveUp = rearrangeCountainer.findViewById (R.id.btnRearrangeMoveUp); 
		btnRearrangeMoveDown = rearrangeCountainer.findViewById (R.id.btnRearrangeMoveDown); 
		btnRearrangeMoveUp.setOnClickListener (mBtnOnClick); 
		btnRearrangeMoveDown.setOnClickListener (mBtnOnClick); 
		show (false); 
	} 
	public interface RearrangeRequestListener { 
		boolean rearrangeCanMove (int position_start, int position_end, int direction); 
		void rearrangeOnRequestMove (int position_start, int position_end, int direction); 
	} 
	void show (boolean whetherShow) { 
		visible = whetherShow; 
		mRearrangeContainer.setVisibility (whetherShow ? View.VISIBLE : View.GONE); 
	} 
	void show () { 
		show (!mItemPositions.isEmpty ()); 
	} 
	private void setSelectedItemCount (int count) { 
		selectedItemCount = count; 
		String needText = count == 1 ? mContext.getString (R.string.title_1_item) : 
								  mContext.getString (R.string.title_n_items) 
				.replace ("[n]", String.valueOf (count)); 
		tvRearrangeTitle.setText (needText); 
	} 
	public void setSelectedItems (Vector<Integer> itemPositions) { 
		mItemPositions = itemPositions; 
		update (); 
	} 
	public void update () { 
		show (); 
		// TODO: Update where the rearrange container is ... (its margins) 
	} 
} 
