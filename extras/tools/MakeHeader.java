// package com.gradgoose.tools; 

import java.io.File; 
import java.io.DataOutputStream; 
import java.io.FileOutputStream; 
import java.io.IOException; 

public class MakeHeader { 
	public static void main (String [] argv) { 
		try { 
			File outputFile = new File ("header.bin"); 
			FileOutputStream fos = new FileOutputStream (outputFile, false); 
			DataOutputStream dos = new DataOutputStream (fos); 
			dos.writeFloat (170); 
			dos.writeFloat (220); 
			dos.writeFloat (0); 
			dos.writeFloat (0); 
			dos.flush (); 
			dos.close (); 
			fos.close (); 
		} catch (IOException err) { 
			err.printStackTrace (); 
		} 
	} 
} 