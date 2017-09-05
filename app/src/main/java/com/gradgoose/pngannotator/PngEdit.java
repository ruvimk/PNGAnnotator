package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/4/2017.
 */

public class PngEdit { 
	private static final String TAG = "PngEdit"; 
	
	final Context mContext; 
	File mTarget = null; 
	
	File mVectorEdits = null; 
	final Vector<LittleEdit> mEdits = new Vector<> (); 
	
	float windowWidth = 1; 
	float windowHeight = 1; 
	public void setWindowSize (float width, float height) { 
		if (width == 0 || height == 0) { 
			return; // Do nothing if the window has not been measured/laid out yet. 
		} 
		// Scale all the points from the old window size to the new window size: 
		synchronized (mEdits) { 
			for (LittleEdit edit : mEdits) { 
				// Scale the brush width: 
				edit.brushWidth *= width / windowWidth; 
				// Scale the coordinates: 
				for (int i = 0; i < edit.points.length / 2; i++) { 
					edit.points[2 * i + 0] *= width / windowWidth; 
					edit.points[2 * i + 1] *= height / windowHeight; 
				} 
			} 
		} 
		// Change the window size: 
		windowWidth = width; 
		windowHeight = height; 
	} 
	
	public void addEdit (LittleEdit e) { 
		synchronized (mEdits) { 
			mEdits.add (e); 
		} 
	} 
	public boolean removeEdit (LittleEdit e) { 
		synchronized (mEdits) { 
			return mEdits.remove (e); 
		} 
	} 
	
	public void erase (float eraserPath [], float eraserRadius) { 
		
	} 
	
	public static class MN { 
		float m; 
		float n; 
		MN (float arg_m, float arg_n) { m = arg_m; n = arg_n; } 
	} 
	public static float findTwoLineSegmentIntersectionArcLengthPosition (
			float ax, float ay, float bx, float by, 
			float cx, float cy, float dx, float dy 
	) { 
		float sx = bx - ax; 
		float sy = by - ay; 
		float tx = dx - cx; 
		float ty = dy - cy; 
		float drx = ax - cx; 
		float dry = ay - cy; 
		float magr = (float) Math.sqrt (drx * drx + dry * dry); 
		if (magr == 0) return 0; // They intersect for when s = 0. 
		float magt = (float) Math.sqrt (tx * tx + ty * ty); 
		float mags = (float) Math.sqrt (sx * sx + sy * sy); 
		float cost = (sx * tx + sy * ty) / (mags * magt); 
		float cosa = -(drx * tx + dry * ty) / (magr * magt); 
		float sint = (float) Math.sqrt (1 - cost * cost); 
		float sina = (float) Math.sqrt (1 - cosa * cosa); 
		return -magr * sina / sint; 
	} 
	public static MN findLineSegmentCircleIntersectionArcLengthPosition ( 
			float ax, float ay, float bx, float by, 
			float cx, float cy, float R 
	) { 
		float abx = bx - ax; 
		float aby = by - ay; 
		float acx = cx - ax; 
		float acy = cy - ay; 
		float abm = (float) Math.sqrt (abx * abx + aby * aby); 
		float acm = (float) Math.sqrt (acx * acx + acy * acy); 
		if (acm == 0) return new MN (-R, R); 
		float cost = (acx * abx + acy * aby) / (acm * abm); 
		float sint = (float) Math.sqrt (1 - cost * cost); 
		float h = acm * sint; 
		float mn2 = (float) Math.sqrt (acm * acm - h * h); 
		float nm2 = (float) Math.sqrt (R * R - h * h); 
		return new MN (mn2 - nm2, mn2 + nm2); 
	} 
	
	static class LittleEdit { 
		int color = Color.BLACK; 
		float brushWidth = 3.0f; // in target image pixels 
		float points [] = null; 
		@Override public boolean equals (@Nullable Object other) { 
			boolean preliminaryCheck = other != null && other instanceof LittleEdit && 
						   ((LittleEdit) other).color == color && 
						   ((LittleEdit) other).brushWidth == brushWidth && 
						   ((LittleEdit) other).points.length == points.length; 
			if (!preliminaryCheck) return false; 
			for (int i = 0; i < points.length; i++) 
				if (((LittleEdit) other).points[i] != points[i]) 
					return false; 
			return true; 
		} 
	} 
	
	
	
	
	public void loadEdits () throws IOException { 
		if (mVectorEdits == null || !mVectorEdits.exists ()) return; 
		mEdits.clear (); 
		InputStream inputStream = new FileInputStream (mVectorEdits); 
		DataInputStream dataInput = new DataInputStream (inputStream); 
		while (dataInput.available () >= 12) { 
			LittleEdit littleEdit = new LittleEdit (); 
			littleEdit.color = dataInput.readInt (); 
			float relativeBrushWidth = dataInput.readFloat (); 
			littleEdit.brushWidth = relativeBrushWidth * windowWidth; 
			int numberCount = dataInput.readInt (); 
			littleEdit.points = new float [numberCount]; 
			for (int i = 0; i < numberCount / 2; i++) { 
				littleEdit.points[2 * i + 0] = windowWidth * dataInput.readFloat (); 
				littleEdit.points[2 * i + 1] = windowHeight * dataInput.readFloat (); 
			} 
			mEdits.add (littleEdit); 
		} 
		dataInput.close (); 
		inputStream.close (); 
	} 
	public void saveEdits () throws IOException {
		OutputStream outputStream = new FileOutputStream (mVectorEdits, false);
		DataOutputStream dataOutput = new DataOutputStream (outputStream); 
		for (LittleEdit edit : mEdits) { 
			dataOutput.writeInt (edit.color); 
			dataOutput.writeFloat (edit.brushWidth / windowWidth); 
			dataOutput.writeInt (edit.points.length); 
			for (int i = 0; i < edit.points.length / 2; i++) { 
				dataOutput.writeFloat (edit.points[2 * i + 0] / windowWidth); 
				dataOutput.writeFloat (edit.points[2 * i + 1] / windowHeight); 
			} 
		} 
		dataOutput.close (); 
		outputStream.close (); 
	} 
	
	
	
	
	
	
	private PngEdit (Context context, File file) { 
		mContext = context; 
		mTarget = file; 
	} 
	
	public static String calculateMD5 (File file) throws IOException {
		MessageDigest digest; 
		try {
			digest = MessageDigest.getInstance ("MD5"); 
		} catch (NoSuchAlgorithmException err) { 
			// Kind of wrong, but it should semi-work for our application here 
			// to identify the file: 
			return file.getAbsolutePath ().replace ('/', '_'); 
		} 
		InputStream inputStream; 
		inputStream = new FileInputStream (file);
		byte buffer [] = new byte [8192]; 
		int bRead; 
		while ((bRead = inputStream.read (buffer)) > 0) { 
			digest.update (buffer, 0, bRead); 
		} 
		byte md5sum [] = digest.digest ();
		BigInteger bigInteger = new BigInteger (1, md5sum); 
		return String.format ("%32s", bigInteger.toString (16)).replace (' ', '0'); 
	} 
	private static File getEditsDir (Context context) {
		File appFilesRoot = context.getFilesDir (); 
		File editsDir = new File (appFilesRoot, "User-Edits"); 
		if (!editsDir.exists () && !editsDir.mkdirs ()) {
			Log.e (TAG, "Could not create private folder for user edits. "); 
		} 
		return editsDir; 
	} 
	public static PngEdit forFile (Context context, File pngFile) throws IOException { 
		String md5sum = calculateMD5 (pngFile); 
		PngEdit edit = new PngEdit (context, pngFile); 
		File ourDir = getEditsDir (context); 
		edit.mVectorEdits = new File (ourDir, md5sum + ".dat"); 
		try { 
			edit.loadEdits (); 
		} catch (IOException err) { 
			// Do nothing. It's okay at this point because it may be a new PNG, etc. 
		} 
		return edit; 
	} 
	
	
} 
