package com.gradgoose.pngannotator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/9/2017.
 */

public class PaperGenerator { 
	int mDPI = 300; 
	float mPaperW = 8.5f; // inches 
	float mPaperH = 11f; // inches 
	static float getPxPerMm (float pageW, float pageH) { 
		return pageW / 8.5f / 25.4f; // Pixels per inch / mm/in = Pixels / mm; 
	} 
	static File makeNewPaperFile (File inFolder, @Nullable File insertBefore) { 
		Vector<File> browsing = new Vector<> (); 
		browsing.add (inFolder); 
		File list [] = PngNotesAdapter.getFlattenedList (
				FileListCache.getFileLists (PngNotesAdapter.mFilterJustImages, browsing) 
		); 
		if (insertBefore == null) { 
			if (list.length == 0) { 
				// If it's an empty folder, start by adding a file with this numbered name: 
				return new File (inFolder, "001.png"); 
			} 
			String lastName = list[list.length - 1].getName (); 
			// Find first numerical digit, etc.: 
			int firstDigit = -1; 
			for (int i = 0; i < lastName.length (); i++) { 
				char a = lastName.charAt (i); 
				if (a >= 48 && a < 58) { 
					firstDigit = i; 
					break; 
				} 
			} 
			if (firstDigit == -1) { 
				// If the last filename doesn't have a number part, add one to it when creating new file: 
				int lastDot = lastName.lastIndexOf ('.'); 
				return new File (inFolder, lastName.substring (0, lastDot) + " 002.png"); 
			} 
			// Otherwise, we have found where the number part of the filename starts, and to 
			// make the next file that will come after it, we need to +1 (increment) that 
			// number part. 
			int lastDigit = firstDigit; 
			for (int i = lastDigit; i < lastName.length (); i++) { 
				char a = lastName.charAt (i); 
				if (a >= 48 && a < 58) { 
					lastDigit = i; 
				} else break; 
			} 
			int number = Integer.valueOf (lastName.substring (firstDigit, lastDigit + 1)); 
			String nextNumber = String.valueOf (number + 1); 
			while (nextNumber.length () < 3) 
				nextNumber = "0" + nextNumber; 
			String needName = lastName.substring (0, firstDigit) + 
									  nextNumber + 
									  lastName.substring (lastDigit + 1); 
			return new File (inFolder, needName); 
		} else { 
			// TODO: Insert new page before the specified file. 
			return null; 
		} 
	} 
	public void copyGraphPaper (Context context, File intoFolder, File insertBefore) { 
		File file = makeNewPaperFile (intoFolder, insertBefore); 
		if (file == null) 
			return; 
		try { 
			String pngPath = file.getAbsolutePath (); 
			int lastDot = pngPath.lastIndexOf ('.'); 
			File outFile = new File (pngPath.substring (0, lastDot) + ".apg"); 
			FileOutputStream fos = new FileOutputStream (outFile, false); 
			byte buffer [] = new byte [4096]; 
			int bRead; 
			try { 
				InputStream src = context.getResources ().openRawResource (R.raw.plain_graph_paper_4x4_apg); 
				while ((bRead = src.read (buffer, 0, buffer.length)) > 0) 
					fos.write (buffer, 0, bRead); 
				src.close (); 
				fos.close (); 
			} catch (IOException e) { 
				e.printStackTrace (); 
				// If the copy was not found, then make one from scratch: 
				makeGraphPaper (intoFolder, insertBefore, null); 
			} 
		} catch (FileNotFoundException e) { 
			e.printStackTrace (); 
		} 
	} 
	public void makeGraphPaper (final File inFolder, @Nullable final File insertBefore, 
								   @Nullable final Runnable onComplete) { 
		(new Thread () { 
			@Override public void run () { 
				File file = makeNewPaperFile (inFolder, insertBefore); 
				if (file == null) 
					return; // TODO: Take this check out when we figure makeNewPaperFile () out. 
				int pxPaperW = (int) (mPaperW * mDPI); 
				int pxPaperH = (int) (mPaperH * mDPI); 
				Bitmap bmp = Bitmap.createBitmap (pxPaperW, pxPaperH, Bitmap.Config.ARGB_8888); 
				Canvas can = new Canvas (bmp); 
				Paint paint = new Paint (); 
				drawGraphPaper (can, paint); 
				
				try 
				{ 
					FileOutputStream fos = new FileOutputStream (file, false); 
					bmp.compress (Bitmap.CompressFormat.PNG, 100, fos); 
					fos.close (); 
				} catch (IOException err) 
				{ 
					err.printStackTrace (); 
				} 
				bmp.recycle (); 
				
				if (onComplete != null) 
					onComplete.run (); 
			} 
		}).start (); 
	} 
	public void drawGraphPaper (Canvas canvas, Paint paint) { 
		int pxPaperW = (int) (mPaperW * mDPI); 
		int pxPaperH = (int) (mPaperH * mDPI); 
		int pxSpan = (int) (mDPI * 0.25f); // 4x4 paper. 
		paint.setStrokeWidth (mDPI * 0.3f /*mm*/ / 25.4f /*mm/in*/ * 
									  /* scale to window width */ canvas.getWidth () / pxPaperW); 
		paint.setColor (Color.rgb (150, 200, 255)); 
		for (int x = pxSpan / 2;
			 x < pxPaperW; x += pxSpan)
		{
			canvas.drawLine (x * canvas.getWidth () / pxPaperW, 0, 
					x * canvas.getWidth () / pxPaperW, 
					canvas.getHeight (), paint); 
		}
		for (int y = pxSpan / 2;
			 y < pxPaperH; y += pxSpan)
		{
			canvas.drawLine (0, y * canvas.getHeight () / pxPaperH, 
					canvas.getWidth (), y * canvas.getHeight () / pxPaperH, paint); 
		} 
	} 
	void setupGraphPaperPaint (int width, Paint paint) { 
		int pxPaperW = (int) (mPaperW * mDPI); 
		int pxPaperH = (int) (mPaperH * mDPI); 
		int pxSpan = (int) (mDPI * 0.25f); // 4x4 paper. 
		paint.setStyle (Paint.Style.STROKE); 
		paint.setStrokeWidth (mDPI * 0.3f /*mm*/ / 25.4f /*mm/in*/ * 
									  /* scale to window width */ width / pxPaperW); 
		paint.setColor (Color.rgb (150, 200, 255)); 
	} 
	float [] makeGraphPaperLines (int width, int height) { 
		int pxPaperW = (int) (mPaperW * mDPI); 
		int pxPaperH = (int) (mPaperH * mDPI); 
		int pxSpan = (int) (mDPI * 0.25f); // 4x4 paper. 
		int xLines = (pxPaperW - pxSpan / 2) / pxSpan + 1; 
		int yLines = (pxPaperH - pxSpan / 2) / pxSpan + 1; 
		float points [] = new float [4 * xLines + 4 * yLines]; 
		for (int i = 0; i < xLines; i++) { 
			points[4 * i] = points[4 * i + 2] = 
									(float) (pxSpan * i + pxSpan / 2) * width / pxPaperW; 
			points[4 * i + 1] = 0; 
			points[4 * i + 3] = height; 
		} 
		for (int i = 0; i < yLines; i++) { 
			points[4 * (i + xLines) + 0] = 0; 
			points[4 * (i + xLines) + 2] = width; 
			points[4 * (i + xLines) + 1] = 
					points[4 * (i + xLines) + 3] = 
							(float) (pxSpan * i + pxSpan / 2) * height / pxPaperH; 
		} 
		return points; 
	} 
	static void scalePoints (float points [], int oldWidth, int oldHeight, int newWidth, int newHeight) { 
		float wScale = (float) newWidth / oldWidth; 
		float hScale = (float) newHeight / oldHeight; 
		for (int i = 0; i < points.length; i++) 
			points[i] *= (i % 2 == 0 ? wScale : hScale); 
	} 
	float getScaleFactor (int width) { 
		int pxPaperW = (int) (mPaperW * mDPI); 
		return (float) width / pxPaperW; 
	} 
} 
