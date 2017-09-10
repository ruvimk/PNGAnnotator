package com.gradgoose.pngannotator;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/9/2017.
 */

public class PaperGenerator { 
	int mDPI = 300; 
	float mPaperW = 8.5f; // inches 
	float mPaperH = 11f; // inches 
	static File makeNewPaperFile (File inFolder, @Nullable File insertBefore) { 
		Vector<File> browsing = new Vector<> (); 
		browsing.add (inFolder); 
		File list [] = PngNotesAdapter.prepareFileList (browsing); 
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
				return new File (inFolder, lastName.replace (".png", "") + " 002.png"); 
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
				int pxSpan = (int) (mDPI * 0.25f); // 4x4 paper. 
				Paint paint = new Paint (); 
				paint.setStrokeWidth (mDPI * 0.3f /*mm*/ / 25.4f /*mm/in*/); 
				paint.setColor (Color.rgb (150, 200, 255)); 
				for (int x = pxSpan / 2; 
						x < pxPaperW; x += pxSpan) 
				{ 
					can.drawLine (x, 0, x, pxPaperH, paint); 
				} 
				for (int y = pxSpan / 2; 
						y < pxPaperH; y += pxSpan) 
				{ 
					can.drawLine (0, y, pxPaperW, y, paint); 
				} 
				
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
} 
