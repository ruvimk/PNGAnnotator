package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Environment;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Ruvim Kondratyev on 9/10/2017.
 */

public class ZipRenderer { 
	public interface OnRenderProgress { 
		void onRenderProgress (int current, int total); 
	} 
	public static File render (Context context, File fromTargets [],
							   @Nullable String zipFolderName, 
							   @Nullable OnRenderProgress progressCallback) throws IOException { 
		File tmp = File.createTempFile ((zipFolderName != null ? (zipFolderName + "-") : "") + 
				"Pages", ".zip", Environment.getExternalStorageDirectory ()); 
		FileOutputStream fos = new FileOutputStream (tmp); 
		ZipOutputStream zos = new ZipOutputStream (fos); 
		int current = 0; 
		int total = fromTargets.length; 
		for (File backgroundFile : fromTargets) { 
			if (progressCallback != null) 
				progressCallback.onRenderProgress (current, total); 
			zos.putNextEntry (new ZipEntry (backgroundFile.getName ())); 
			Bitmap bmp = renderPage (context, backgroundFile); 
			bmp.compress (Bitmap.CompressFormat.PNG, 100, zos); 
			bmp.recycle (); 
			zos.closeEntry (); 
			current++; 
		} 
		zos.finish (); 
		return tmp; 
	} 
	static Bitmap renderPage (Context context, File targetBackground) throws IOException { 
		// Load our background bitmap and our handwritten edits: 
		Bitmap bg = BitmapFactory.decodeFile (targetBackground.getPath ()); 
		Bitmap bmp = Bitmap.createBitmap (bg.getWidth (), bg.getHeight (), Bitmap.Config.ARGB_8888); 
		PngEdit edit = PngEdit.forFile (context, targetBackground); 
		edit.setWindowSize (bmp.getWidth (), bmp.getHeight ()); 
		edit.setImageSize (bmp.getWidth (), bmp.getHeight ()); 
		// Initialize our drawing tools: 
		Canvas canvas = new Canvas (bmp); 
		Paint paint = new Paint (); 
		paint.setStyle (Paint.Style.STROKE); 
		paint.setStrokeJoin (Paint.Join.ROUND); 
		paint.setStrokeCap (Paint.Cap.ROUND); 
		// Draw the background, and get rid of the background: 
		canvas.drawBitmap (bg, 0, 0, null); 
		bg.recycle (); 
		// Draw all the edits: 
		for (PngEdit.LittleEdit e : edit.mEdits) { 
			paint.setColor (e.color); 
			paint.setStrokeWidth (e.brushWidth); 
			canvas.drawLines (e.points, paint); 
		} 
		return bmp; 
	} 
} 
