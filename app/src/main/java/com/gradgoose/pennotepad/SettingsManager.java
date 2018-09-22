package com.gradgoose.pennotepad;

import android.content.Context;

public class SettingsManager {
	final Context mContext; 
	public SettingsManager (Context context) { 
		mContext = context; 
	} 
	boolean isInvisiblePageNavTapEnabled () { 
		return true; 
	} 
}
