package com.gradgoose.pennotepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.Nullable;
import android.view.ViewGroup;
import android.widget.EditText;

public class CustomEditDialog { 
	final Context mContext; 
	static final int INPUT_TEXT = 0; 
	static final int INPUT_NUMBER = 1; 
	static final int INPUT_FLOAT = 2; 
	static final int INPUT_URI = 3; 
	String userResponse = ""; 
	CustomEditDialog (Context context) { 
		mContext = context; 
	} 
	void showDialog (String title, String message, String initialText, int inputType, 
					 String positiveButtonLabel, 
					 String negativeButtonLabel, 
					 @Nullable final Runnable positiveButtonCallback, 
					 @Nullable final Runnable negativeButtonCallback, 
					 @Nullable final Runnable cancelCallback) { 
		if (!(mContext instanceof Activity)) { 
			if (cancelCallback != null) 
				cancelCallback.run (); 
			return; 
		} 
		Activity activity = (Activity) mContext; 
		int layout_id = R.layout.edit_text; 
		switch (inputType) { 
			case INPUT_NUMBER: 
				layout_id = R.layout.edit_number; 
				break; 
			case INPUT_FLOAT: 
				layout_id = R.layout.edit_float; 
				break; 
			case INPUT_URI: 
				layout_id = R.layout.edit_file_name; 
				break; 
		} 
		final EditText editText = (EditText) activity.getLayoutInflater ().inflate (layout_id, 
				(ViewGroup) activity.findViewById (R.id.vMainRoot), false); 
		editText.setText (initialText); 
		editText.setSelection (0, initialText.length ()); 
		AlertDialog dialog = new AlertDialog.Builder (mContext) 
				.setTitle (title) 
				.setMessage (message) 
				.setView (editText) 
				.setPositiveButton (positiveButtonLabel, new DialogInterface.OnClickListener () { 
					@Override public void onClick (DialogInterface dialogInterface, int i) { 
						userResponse = editText.getText ().toString (); 
						if (positiveButtonCallback != null) 
							positiveButtonCallback.run (); 
					} 
				}) 
				.setNegativeButton (negativeButtonLabel, new DialogInterface.OnClickListener () { 
					@Override public void onClick (DialogInterface dialogInterface, int i) { 
						if (negativeButtonCallback != null) 
							negativeButtonCallback.run (); 
					} 
				}) 
				.setOnCancelListener (new DialogInterface.OnCancelListener () { 
					@Override public void onCancel (DialogInterface dialogInterface) { 
						if (cancelCallback != null) 
							cancelCallback.run (); 
					} 
				}) 
				.create (); 
		dialog.show (); 
	} 
} 
