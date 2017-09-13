package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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
	
	int mLastIoEditCount = 0; 
	boolean useDifferentialSave = true; 
	
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
		if (eraserPath.length < 2) return; 
		synchronized (mEdits) { 
			// Perform erase operations on all the edits: 
			for (LittleEdit edit : mEdits) { 
				if (edit.points.length < 2) continue; 
				float prevX = eraserPath[0]; 
				float prevY = eraserPath[1]; 
//				eraseCircle (edit, prevX, prevY, eraserRadius); 
				for (int i = 2; i < eraserPath.length; i += 2) { 
					float x = eraserPath[i + 0]; 
					float y = eraserPath[i + 1]; 
					// We do the following check in case we are given Canvas.drawLines ()-like data: 
					if (x == prevX && y == prevY) continue; 
					// Do the operations: 
					eraseLineSegment (edit, prevX, prevY, x, y, eraserRadius); 
//					eraseCircle (edit, x, y, eraserRadius); 
					// Continue: 
					prevX = x; 
					prevY = y; 
				} 
			} 
			// Remove any empty edits (some may have become empty after erasing points): 
			for (int i = 0; i < mEdits.size (); i++) 
				if (mEdits.get (i).points.length == 0) 
					mEdits.remove (i); 
		} 
	} 
	static void skipSegment (Vector<Float> result, 
							 float ax, float ay, float bx, float by) { 
		result.add (ax); 
		result.add (ay); 
		result.add (bx); 
		result.add (by); 
	} 
	static void eraseSubSegment (Vector<Float> result, 
										 float ax, float ay, float bx, float by, 
										 float m, float n) { 
		float abx = bx - ax; 
		float aby = by - ay; 
		float abm = (float) Math.sqrt (abx * abx + aby * aby); 
		if (n <= 0 || m >= abm || Float.isNaN (m) || Float.isNaN (n)) { 
			result.add (ax); 
			result.add (ay); 
			result.add (bx); 
			result.add (by); 
			return; 
		} 
		float abhx = abx / abm; 
		float abhy = aby / abm; 
		if (m > 0 && m < abm) { 
			result.add (ax); 
			result.add (ay); 
			result.add (ax + abhx * m); 
			result.add (ay + abhy * m); 
		} 
		if (n > 0 && n < abm) { 
			result.add (ax + abhx * n); 
			result.add (ay + abhy * n); 
			result.add (bx); 
			result.add (by); 
		} 
	} 
	private static void finishErasing (LittleEdit edit, Vector<Float> result) { 
		float now []; 
		if (result.size () != edit.points.length) 
			now = new float [result.size ()]; 
		else now = edit.points; 
		for (int i = 0; i < now.length; i++) 
			now[i] = result.elementAt (i); 
		edit.points = now; 
	} 
	static void eraseCircle (LittleEdit edit, float cx, float cy, float R) { 
		Vector<Float> result = new Vector<> (edit.points.length); 
		for (int i = 2; i < edit.points.length; i += 4) { 
			float ax = edit.points[i - 2]; 
			float ay = edit.points[i - 1]; 
			float bx = edit.points[i + 0]; 
			float by = edit.points[i + 1]; 
			MN isect = findLineSegmentCircleIntersectionArcLengthPosition ( 
					ax, ay, bx, by, 
					cx, cy, R 
			); 
			eraseSubSegment (result, ax, ay, bx, by, isect.m, isect.n); 
		} 
		finishErasing (edit, result); 
	} 
	static void eraseLineSegment (LittleEdit edit, 
										  float cx, float cy, 
										  float dx, float dy, 
										  float R) { 
		Vector<Float> result = new Vector<> (edit.points.length); 
		for (int i = 2; i < edit.points.length; i += 4) { 
			float ax = edit.points[i - 2]; 
			float ay = edit.points[i - 1]; 
			float bx = edit.points[i + 0]; 
			float by = edit.points[i + 1]; 
			float abx = bx - ax; 
			float aby = by - ay; 
			float cdx = dx - cx; 
			float cdy = dy - cy; 
			float abm = (float) Math.sqrt (abx * abx + aby * aby); 
			float cdm = (float) Math.sqrt (cdx * cdx + cdy * cdy); 
			// Find an orthogonal unit vector to use for shifting the reference frame: 
			float ox = cdx / cdm; 
			float oy = -cdy / cdm; 
			// Create sets of m and n line segments to use for the m and n s values: 
			float cxm = cx - ox * R; 
			float cym = cy - oy * R; 
			float cxn = cx + ox * R; 
			float cyn = cy + oy * R; 
			float dxm = dx - ox * R; 
			float dym = dy - oy * R; 
			float dxn = dx + ox * R; 
			float dyn = dx + oy * R; 
			// Find intersections: 
			float s0 = findTwoLineSegmentIntersectionArcLengthPosition (
					ax, ay, bx, by, cxm, cym, dxm, dym 
			); 
			float s1 = findTwoLineSegmentIntersectionArcLengthPosition (
					ax, ay, bx, by, cxn, cyn, dxn, dyn 
			); 
			float s2 = findTwoLineSegmentIntersectionArcLengthPosition ( 
					ax, ay, bx, by, cxm, cym, cxn, cyn 
			); 
			float s3 = findTwoLineSegmentIntersectionArcLengthPosition ( 
					ax, ay, bx, by, dxm, dym, dxn, dyn 
			); 
			float numbers [] = new float [] {s0, s1, s2, s3};
			Arrays.sort (numbers); 
			float sm = numbers[1]; 
			float sn = numbers[2]; 
			// Erase the intersected area: 
			eraseSubSegment (result, ax, ay, bx, by, sm, sn); 
		} 
		finishErasing (edit, result); 
	} 
	
	public static float calculateDistance (float ax, float ay, float bx, float by) { 
		float dx = bx - ax; 
		float dy = by - ay; 
		return (float) Math.sqrt (dx * dx + dy * dy); 
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
		float qpx = cx - ax; 
		float qpy = cy - ay; 
		float rx = bx - ax; 
		float ry = by - ay; 
		float sx = dx - cx; 
		float sy = dy - cy; 
		float cs = qpx * sy - qpy * sx; 
		float rs = rx * sy - ry * sx; 
		return cs / rs; 
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
		synchronized (mEdits) { 
			mEdits.clear (); 
			InputStream inputStream = new FileInputStream (mVectorEdits); 
			byte magic [] = new byte [4]; 
			int version = 0; 
			if (inputStream.read (magic) >= 4 && magic[0] == 'G' && magic[1] == 'E') { 
				if (magic[2] == '1' && magic[3] == '0') 
					version = 1; 
			} else ((FileInputStream) inputStream).getChannel ().position (0); // Seek back. 
			int totalBytes = inputStream.available (); 
			ByteBuffer buffer = version > 0 ? ByteBuffer.allocate (totalBytes) : null; 
			if (version == 1) { 
				((FileInputStream) inputStream).getChannel ().read (buffer); 
				float buf [] = new float [totalBytes / 4]; 
				buffer.rewind (); // Seek back to position 0, for reading now. 
				buffer.asFloatBuffer ().get (buf); 
				// Process it: 
				int ptCount; 
				for (int i = 0; i < buf.length; i += 6 + ptCount) { 
					// Get things: 
					float colorA = buf[i + 0]; 
					float colorR = buf[i + 1]; 
					float colorG = buf[i + 2]; 
					float colorB = buf[i + 3]; 
					float brushW = buf[i + 4]; 
					float countP = buf[i + 5]; 
					ptCount = Math.round (countP); 
					// Store these: 
					LittleEdit littleEdit = new LittleEdit (); 
					littleEdit.color = Color.argb ((int) (colorA * 255), 
							(int) (colorR * 255), 
							(int) (colorG * 255), 
							(int) (colorB * 255)); 
					littleEdit.brushWidth = brushW * windowWidth; 
					littleEdit.points = new float [ptCount]; 
					for (int j = i + 6; j < i + 6 + ptCount; j++) 
						buf[j] *= (j % 2 == 0 ? windowWidth : windowHeight); 
					System.arraycopy (buf, i + 6, littleEdit.points, 0, ptCount); 
					// Continue: 
					mEdits.add (littleEdit); 
				} 
				// Clean up: 
				mLastIoEditCount = mEdits.size (); 
				inputStream.close (); 
				return; 
			} 
			DataInputStream dataInput = new DataInputStream (inputStream); 
			while (dataInput.available () >= 12) { 
				LittleEdit littleEdit = new LittleEdit (); 
				littleEdit.color = dataInput.readInt (); 
				float relativeBrushWidth = dataInput.readFloat (); 
				littleEdit.brushWidth = relativeBrushWidth * windowWidth; 
				int numberCount = dataInput.readInt (); 
				littleEdit.points = new float[numberCount]; 
				for (int i = 0; i < numberCount / 2; i++) { 
					littleEdit.points[2 * i + 0] = windowWidth * dataInput.readFloat (); 
					littleEdit.points[2 * i + 1] = windowHeight * dataInput.readFloat (); 
				} 
				mEdits.add (littleEdit); 
			} 
			if (version != SAVE_VERSION) { 
				// Need to RESAVE the whole thing. Different file version. 
				useDifferentialSave = false; 
				mLastIoEditCount = 0; 
			} else mLastIoEditCount = mEdits.size (); 
			dataInput.close (); 
			inputStream.close (); 
		} 
	} 
	int SAVE_VERSION = 1; 
	public void saveEdits () throws IOException { 
		synchronized (mEdits) { 
			// The following statement is needed because the magic number needs to be 
			// written to the file the first time it is saved, but it will not be 
			// written if 'useDifferentialSave' flag is set: 
			if (mLastIoEditCount == 0) 
				useDifferentialSave = false; 
			// Open the file for writing, with the append flag set to true if saving differentially: 
			OutputStream outputStream = new FileOutputStream (mVectorEdits, useDifferentialSave); 
			if (SAVE_VERSION == 1) { 
				if (!useDifferentialSave) { 
					// Write magic number: 
					byte magic [] = new byte [] { 'G', 'E', '1', '0' }; 
					outputStream.write (magic); 
					// Reset last edit count: 
					mLastIoEditCount = 0; 
				} 
				int totalEditCount = mEdits.size () - mLastIoEditCount; 
				int totalPointCount = 0; 
				for (int i = mLastIoEditCount; i < mEdits.size (); i++) { 
					totalPointCount += mEdits.elementAt (i).points.length; 
				} 
				ByteBuffer buffer = ByteBuffer.allocate ((totalEditCount * 6 + totalPointCount) * 4); 
				float buf [] = new float [totalEditCount * 6 + totalPointCount]; 
				int index = 0; 
				int ptCount; 
				for (int i = mLastIoEditCount; i < mEdits.size (); i++) { 
					LittleEdit edit = mEdits.elementAt (i); 
					ptCount = edit.points.length; 
					// Get things: 
					float colorA = (float) Color.alpha (edit.color) / 255f; 
					float colorR = (float) Color.red (edit.color) / 255f; 
					float colorG = (float) Color.green (edit.color) / 255f; 
					float colorB = (float) Color.blue (edit.color) / 255f; 
					float brushW = edit.brushWidth / windowWidth; 
					float countP = (float) ptCount; 
					// Copy things: 
					buf[index + 0] = colorA; 
					buf[index + 1] = colorR; 
					buf[index + 2] = colorG; 
					buf[index + 3] = colorB; 
					buf[index + 4] = brushW; 
					buf[index + 5] = countP; 
					System.arraycopy (edit.points, 0, buf, index + 6, ptCount); 
					for (int j = index + 6; j < index + 6 + ptCount; j++) 
						buf[j] /= (j % 2 == 0 ? windowWidth : windowHeight); 
					// Continue: 
					index += 6 + ptCount; 
				} 
				// Save to file: 
				buffer.asFloatBuffer ().put (buf); 
				((FileOutputStream) outputStream).getChannel ().write (buffer); 
				// For next time: 
				useDifferentialSave = true; 
				mLastIoEditCount = mEdits.size (); 
			} else { 
				DataOutputStream dataOutput = new DataOutputStream (outputStream); 
				if (!useDifferentialSave) 
					mLastIoEditCount = 0; 
				for (int i = mLastIoEditCount; i < mEdits.size (); i++) { 
					LittleEdit edit = mEdits.elementAt (i); 
					dataOutput.writeInt (edit.color); 
					dataOutput.writeFloat (edit.brushWidth / windowWidth); 
					dataOutput.writeInt (edit.points.length); 
					for (int j = 0; j < edit.points.length / 2; j++) { 
						dataOutput.writeFloat (edit.points[2 * j + 0] / windowWidth); 
						dataOutput.writeFloat (edit.points[2 * j + 1] / windowHeight); 
					} 
				} 
				useDifferentialSave = true; 
				mLastIoEditCount = mEdits.size (); 
				dataOutput.close (); 
			} 
			outputStream.close (); 
		} 
	} 
	
	
	
	
	
	
	private PngEdit (Context context, File file) { 
		mContext = context; 
		mTarget = file; 
	} 
	
	public static @NonNull String calculateMD5 (File file) throws IOException { 
		return calculateMD5 (file, null); 
	} 
	public static @NonNull 
	String calculateMD5 (File file, @Nullable byte additional_tail []) throws IOException { 
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
		if (additional_tail != null) 
			digest.update (additional_tail); 
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
	
	public static String getFullFileName (Context context, File pngFile) throws IOException { 
		return getFullFileName (context, pngFile, ".dat"); 
	} 
	public static String getFullFileName (Context context, File pngFile, String ext) throws IOException { 
		String md5sum = calculateMD5 (pngFile); 
		// Create a filename that is based on this version of the file: 
		return md5sum + "-" + Long.toString (pngFile.lastModified (), 16) + ext; 
		// The edits' filename takes into account that the user may have created a second copy 
		// of a picture file, hoping to mark up the second copy with different edits. 
		// For example, it may be a picture of some graph paper, and the user wants to 
		// write different things on different pages of the same graph paper. 
		// The MD5 checksum will be the same for both pages, but the lastModified () 
		// will hopefully be different, so we know it's a different page. 
		// If the picture file is modified, then BOTH the MD5 and the lastModified () 
		// will change, so our edits are locked onto just this version of the file. 
	} 
	public static File getEditsFile (Context context, File pngFile) throws IOException { 
		// Get file name: 
		String fullFilename = getFullFileName (context, pngFile); 
		
		// Get the file for the full filename: 
		File ourDir = getEditsDir (context); 
		return new File (ourDir, fullFilename); 
	} 
	public static PngEdit forFile (Context context, File pngFile) throws IOException {
		PngEdit edit = new PngEdit (context, pngFile); 
		edit.mVectorEdits = getEditsFile (context, pngFile); 
		try { 
			edit.loadEdits (); 
		} catch (IOException err) { 
			// Do nothing. It's okay at this point because it may be a new PNG, etc. 
		} 
		return edit; 
	} 
	
	
} 
