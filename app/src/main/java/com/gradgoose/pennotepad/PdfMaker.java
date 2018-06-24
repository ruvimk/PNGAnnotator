package com.gradgoose.pennotepad;

import android.content.Context;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.FileChannel;

/**
 * Created by Ruvim Kondratyev on 20.01.2018.
 */

public class PdfMaker { 
	final Context mContext; 
	public PdfMaker (Context context) { 
		mContext = context; 
	} 
	void render (@NonNull File sourcePages [], @NonNull File destinationPDF) throws IOException {
		FileOutputStream fos = new FileOutputStream (destinationPDF); 
		FileChannel channel = fos.getChannel ();
		OutputStreamWriter writer = new OutputStreamWriter (fos, "UTF-8"); 
		int pageCount = sourcePages.length; 
		PdfiumCore pdfiumCore = null; 
		PdfDocument pdfDocument = null; 
		if (sourcePages[0].getName ().toLowerCase ().endsWith (".pdf")) { 
			pdfiumCore = new PdfiumCore (mContext); 
			ParcelFileDescriptor pfd = ParcelFileDescriptor.open (sourcePages[0], ParcelFileDescriptor.MODE_READ_ONLY); 
			pdfDocument = pdfiumCore.newDocument (pfd); 
			pageCount = pdfiumCore.getPageCount (pdfDocument); 
		} 
		long offsets [] = new long [4 + 2 * pageCount]; // Two (page, strokes) for each page, plus three: catalog, pages, and background strokes (e.g., graph paper). 
		writer.write ("%PDF-1.7\r\n\r\n"); writer.flush (); 
		offsets[0] = channel.position (); 
		writer.write ("1 0 obj\r\n<<\r\n\t/Type /Catalog\r\n\t/Pages 2 0 R\r\n>>\r\nendobj\r\n\r\n"); writer.flush (); 
		offsets[1] = channel.position (); 
		StringBuilder sbKids = new StringBuilder (7 * pageCount); 
		for (int i = 0; i < pageCount; i++) { 
			sbKids.append (2 * i + 4); 
			sbKids.append (" 0 R "); 
		} 
		writer.write ("2 0 obj\r\n<<\r\n\t/Type /Pages\r\n\t/MediaBox [0 0 612 792]\r\n\t/Count " + pageCount + "\r\n\t/Kids [ " + 
							  sbKids.toString () + "]\r\n>>\r\nendobj\r\n\r\n"); writer.flush (); 
		offsets[2] = channel.position (); 
		renderBackground (writer); 
		offsets[3] = channel.position (); 
		for (int i = 0; i < pageCount; i++) { 
			if (pdfDocument != null) { 
				pdfiumCore.openPage (pdfDocument, i); 
				renderPage (sourcePages[0], writer, 2 * i + 4, channel, offsets, i); 
			} else 
				renderPage (sourcePages[i], writer, 2 * i + 4, channel, offsets, 1); 
		} 
		StringBuilder sbXref = new StringBuilder (20 * offsets.length); 
		sbXref.append (encodeOffset (0, 10)); 
		sbXref.append (" 65535 f\r\n"); 
		for (int i = 1; i < offsets.length; i++) { 
			long at = offsets[i - 1]; 
			sbXref.append (encodeOffset (at, 10)); 
			sbXref.append (" 00000 n\r\n"); 
		} 
		String xref = sbXref.toString (); 
		writer.write ("xref\r\n0 " + offsets.length + "\r\n"); 
		writer.write (xref); 
		writer.write ("trailer\r\n<<\r\n\t/Size " + offsets.length + "\r\n\t/Root 1 0 R\r\n>>\r\nstartxref\r\n" + offsets[offsets.length - 1] + "\r\n"); 
		writer.write ("%%EOF"); 
		writer.flush (); 
		writer.close (); 
		fos.close (); 
		if (pdfDocument != null) 
			pdfiumCore.closeDocument (pdfDocument); 
	} 
	static String makePdfColor (int color) { 
		float r = (float) Color.red (color) / 255f; 
		float g = (float) Color.green (color) / 255f; 
		float b = (float) Color.blue (color) / 255f; 
		return r + " " + g + " " + b; 
	} 
	private void renderBackground (@NonNull Writer to) throws IOException { 
		PaperGenerator generator = new PaperGenerator (); 
		generator.mDPI = 72; // PDF uses PT, and there's 72 PT per inch. 
		float lines [] = generator.makeGraphPaperLines (612, 792); 
		StringBuilder ruleStrokes = new StringBuilder (); 
		// Page 133 of https://www.adobe.com/content/dam/acom/en/devnet/pdf/pdfs/PDF32000_2008.pdf is useful. 
		ruleStrokes.append ("0 j 0 J "); // Default line-cap, line-join. 
		ruleStrokes.append (String.valueOf (generator.calcStrokeWidth (612))); 
		ruleStrokes.append (" w "); // Stroke width. 
		ruleStrokes.append (makePdfColor (PaperGenerator.COLOR_GRAPH_PAPER)); 
		ruleStrokes.append (" RG "); 
		for (int i = 0; i < lines.length; i += 4) { 
			ruleStrokes.append (lines[i + 0]); 
			ruleStrokes.append (' '); 
			ruleStrokes.append (lines[i + 1]); 
			ruleStrokes.append (" m "); 
			ruleStrokes.append (lines[i + 2]); 
			ruleStrokes.append (' '); 
			ruleStrokes.append (lines[i + 3]); 
			ruleStrokes.append (" l"); 
			ruleStrokes.append (' '); 
		} 
		ruleStrokes.append ('S'); 
		to.write ("3 0 obj\r\n<<\r\n\t/Length " + ruleStrokes.length () + "\r\n>>\r\nstream\r\n"); 
		to.write (ruleStrokes.toString ()); 
		to.write ("\r\nendstream\r\nendobj\r\n\r\n"); 
		to.flush (); 
	} 
	private void renderPage (@NonNull File from, @NonNull Writer to, int offsetIndex, @Nullable FileChannel channel, @Nullable long outOffsets [], int pageIndex) throws IOException { 
		PngEdit edit = PngEdit.forFile (mContext, from, pageIndex); 
		String contents; 
		if (edit.srcPageBackground == 1) { 
			edit.setWindowSize (612, 792); // Letter size. 
			edit.setImageSize (612, 792); 
			contents = "[3 0 R " + (offsetIndex + 1) + " 0 R]"; 
		} else { 
			contents = (offsetIndex + 1) + " 0 R"; 
		} 
		to.write (String.valueOf (offsetIndex)); 
		to.write (" 0 obj\r\n<<\r\n\t/Type /Page\r\n\t/Parent 2 0 R\r\n\t/Contents " + contents + "\r\n>>\r\nendobj\r\n\r\n"); 
		to.flush (); 
		if (outOffsets != null && channel != null) 
			outOffsets[offsetIndex + 0] = channel.position (); 
		StringBuilder sbStrokes = new StringBuilder (); 
		sbStrokes.append ("1 j 1 J"); // Round cap, round join. 
		for (PngEdit.LittleEdit e : edit.mEdits) { 
			for (int j = 0; j < e.points.length; j += 4) { 
				sbStrokes.append (' '); 
				sbStrokes.append (e.points[j + 0]); 
				sbStrokes.append (' '); 
				sbStrokes.append (edit.windowHeight - e.points[j + 1]); 
				sbStrokes.append (" m"); 
				sbStrokes.append (' '); 
				sbStrokes.append (e.points[j + 2]); 
				sbStrokes.append (' '); 
				sbStrokes.append (edit.windowHeight - e.points[j + 3]); 
				sbStrokes.append (" l"); 
			} 
			sbStrokes.append (' '); 
			sbStrokes.append (String.valueOf (e.brushWidth)); 
			sbStrokes.append (" w "); // Stroke-width. 
			sbStrokes.append (makePdfColor (e.color)); 
			sbStrokes.append (" RG "); // Set color. 
			sbStrokes.append ('S'); 
		} 
		String strokeDataStream = sbStrokes.toString (); 
		to.write (String.valueOf (offsetIndex + 1)); 
		to.write (" 0 obj\r\n<<\r\n\t/Length " + strokeDataStream.length () + "\r\n>>\r\nstream\r\n"); 
		to.write (strokeDataStream); 
		to.write ("\r\nendstream\r\nendobj\r\n\r\n"); 
		to.flush (); 
		if (outOffsets != null && channel != null) 
			outOffsets[offsetIndex + 1] = channel.position (); 
	} 
	static String encodeOffset (long offset, int digits) { 
		String n = String.valueOf (offset); 
		StringBuilder sb = new StringBuilder (digits); 
		for (int i = n.length (); i < digits; i++) 
			sb.append ('0'); 
		return sb.append (n).toString (); 
	} 
} 
