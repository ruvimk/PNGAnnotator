package com.gradgoose.pngannotator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Ruvim Kondratyev on 23.01.2018.
 */

public class TimeLog { 
	static void logTime (File inFolder, long timestampFrom, long timestampTo) throws IOException { 
//		if (BuildConfig.DEBUG) { 
//			new File (inFolder, "time-spent.log").renameTo (new File (inFolder, "time-spent.log.bin")); 
//		} 
		File logFile = new File (inFolder, "time-spent.log.bin"); 
		FileOutputStream fos = new FileOutputStream (logFile, true);
		ByteBuffer buffer = ByteBuffer.allocate (16); 
		long arr [] = new long [2]; 
		arr[0] = timestampFrom; 
		arr[1] = timestampTo; 
		buffer.asLongBuffer ().put (arr); 
		fos.write (buffer.array ()); 
		fos.close (); 
	} 
} 
