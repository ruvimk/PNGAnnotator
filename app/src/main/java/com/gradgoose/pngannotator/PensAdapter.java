package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.util.HashMap;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PensAdapter extends RecyclerView.Adapter { 
	final Context mContext; 
	final HashMap<Integer, Long> mStableIds; 
	
	long headerPersistantIdStart = 0; 
	View mHeaderItemViews [] = null; 
	
	int mList [] = null; 
	
	boolean mPenModeMiniHands = false; 
	
	static final int HEADER_CODE = Color.argb (0, 1, 1, 1); 
	public void setHeaderItemViews (View list []) { 
		mHeaderItemViews = list; 
		long maximum = 1; 
		for (Long l : mStableIds.values ()) 
			if (l > maximum) 
				maximum = l; 
		headerPersistantIdStart = maximum + list.length; 
		mStableIds.put (HEADER_CODE, headerPersistantIdStart); 
		notifyDataSetChanged (); 
	} 
	
	private void preparePenList () { 
		mList = new int [] { 
								   Color.BLACK, 
								   Color.RED, 
								   Color.GREEN, 
								   Color.BLUE 
		}; 
	} 
	
	public void reloadList () { 
		preparePenList (); 
		notifyDataSetChanged (); 
	} 
	
	private int getItemColor (int position) { 
		return position < mHeaderItemViews.length ? 
					   0 : 
					   (position - mHeaderItemViews.length < mList.length ? 
								mList[position - mHeaderItemViews.length] : 0); 
	} 
	
	private void loadIds (int list []) { 
		long maximum = 1; 
		for (Long l : mStableIds.values ()) 
			if (l > maximum) 
				maximum = l; 
		for (int color : list) 
			if (!mStableIds.containsKey (color)) 
				mStableIds.put (color, ++maximum); 
	} 
	private void loadIds () { 
		preparePenList (); 
		loadIds (mList); 
	} 
	
	int borderedItemPosition = -1; 
	OnPenColorSelectedListener mColorListener = null; 
	public interface OnPenColorSelectedListener { 
		void onPenColorSelected (int penColor); 
	} 
	public void setOnPenColorSelectedListener (OnPenColorSelectedListener listener) { 
		mColorListener = listener; 
	} 
	public void setBorderedItemPosition (int position) { 
		borderedItemPosition = position; 
		notifyDataSetChanged (); 
	} 
	
	public PensAdapter (Context context) { 
		super (); 
		mContext = context; 
		preparePenList (); 
		mStableIds = new HashMap<> (mList.length); 
		loadIds (mList); 
		setHasStableIds (true); 
	} 
	
	private View.OnClickListener mOnClick = new View.OnClickListener () {
		@Override public void onClick (View view) {
			Object tag = view.getTag (R.id.item_color); 
			if (tag != null && tag instanceof Integer) { 
				int color = (Integer) tag; 
				if (mColorListener != null) 
					mColorListener.onPenColorSelected (color); 
			} 
		}
	}; 
	
	public class Plain extends RecyclerView.ViewHolder { 
		public Plain (View root) { 
			super (root); 
		} 
	} 
	public class Holder extends RecyclerView.ViewHolder {
		final FrameLayout penLayout; 
		final PenIcon penIcon; 
		final View miniHand; 
		public Holder (View root) { 
			super (root); 
			penLayout = root.findViewById (R.id.flPen); 
			penIcon = root.findViewById (R.id.piPen); 
			miniHand = root.findViewById (R.id.ivMiniHand); 
			itemView.setOnClickListener (mOnClick); 
		} 
		public void bind (int color, int index) { 
			if (index == borderedItemPosition) 
				penLayout.setBackgroundResource (R.drawable.black_border); 
			else penLayout.setBackgroundResource (0); 
			penIcon.setColor (color); 
			penIcon.setContentDescription (mContext.getString (R.string.access_pen) 
				.replace ("[number]", String.valueOf (index + 1))); 
			itemView.setTag (R.id.item_color, color); 
			miniHand.setVisibility (mPenModeMiniHands ? View.VISIBLE : View.GONE); 
		} 
	} 
	
	@Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) { 
		// If it's of a header view type, just return a plain holder with the header view: 
		if (viewType >= 100) 
			return new Plain (mHeaderItemViews[viewType - 100]); 
		// Otherwise, it's just a list item type; return a regular list holder of ours: 
		View itemView = LayoutInflater.from (mContext).inflate (R.layout.icon_pen, parent, false); 
		return new Holder (itemView); 
	} 
	
	@Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) { 
		if (holder instanceof Holder) 
			((Holder) holder).bind (getItemColor (position), position - countHeaderViews ()); 
	} 
	
	@Override public int getItemViewType (int position) { 
		return position < mHeaderItemViews.length ? 
					   (position + 100) // Just use 100 and on for header view types. 
					   : 1; 
	} 
	
	@Override public long getItemId (int position) { 
		int itemColor = getItemColor (position); 
		if (mStableIds.containsKey (itemColor)) 
			return mStableIds.get (itemColor); 
		return super.getItemId (position); 
	} 
	
	@Override public int getItemCount () { 
		return (mList != null ? mList.length : 0) + 
					   (mHeaderItemViews != null ? mHeaderItemViews.length : 0); 
	} 
	
	public int countHeaderViews () { 
		return mHeaderItemViews != null ? mHeaderItemViews.length : 0; 
	} 
	public int countImages () { 
		return mList != null ? mList.length : 0; 
	} 
	
	public int findColorPosition (int color) { 
		for (int i = 0; i < mList.length; i++) 
			if (mList[i] == color) 
				return i; 
		return -1; 
	} 
	
} 
