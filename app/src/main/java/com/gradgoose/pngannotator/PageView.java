package com.gradgoose.pngannotator;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.io.File;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PageView extends ImageView { 
	
	File itemFile = null; 
	
	public PageView (Context context, AttributeSet attributeSet) { 
		super (context, attributeSet); 
	} 
	
	public void setItemFile (File file) { 
		itemFile = file; 
		setImageURI (Uri.fromFile (file)); 
	} 
	
	
} 
