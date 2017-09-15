package com.gradgoose.pngannotator;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Created by Ruvim Kondratyev on 9/14/2017.
 */

public class FileListCache { 
	final Vector<File> mFolder; 
	
	static Comparator<File []> mFileComparator = new Comparator<File []> () { 
		@Override public int compare (File a [], File b []) { 
			return a[0].compareTo (b[0]); 
		} 
	}; 
	
	public interface OnFilesChangedListener { 
		void onFilesChanged (File [] [] list); 
		void onFilesNoChange (File [] [] list); 
	} 
	
	public FileListCache (Vector<File> targetFolder) { 
		mFolder = targetFolder; 
	} 
	
	static void writeLines (File to, Vector<String> lines) throws IOException {
		FileOutputStream outputStream = new FileOutputStream (to); 
		BufferedWriter writer = new BufferedWriter (new OutputStreamWriter (outputStream)); 
		for (String line : lines) 
			writer.write (line + "\n"); 
		writer.close (); 
		outputStream.close (); 
	} 
	static Vector<String> readLines (File from) throws IOException {
		FileInputStream inputStream = new FileInputStream (from);
		BufferedReader reader = new BufferedReader (new InputStreamReader (inputStream)); 
		String line; 
		Vector<String> lines = new Vector<> (); 
		while ((line = reader.readLine ()) != null) { 
			if (line.isEmpty ()) continue; 
			lines.add (line); 
		} 
		reader.close (); 
		inputStream.close (); 
		return lines; 
	} 
	static File [] makeFilesArray (Vector<String> filePaths) { 
		File list [] = new File [filePaths.size ()]; 
		for (int i = 0; i < filePaths.size (); i++) 
			list[i] = new File (filePaths.elementAt (i)); 
		return list; 
	} 
	
	void saveFileLists (Vector<File> folder, 
						@Nullable File [] [] old, 
						@NonNull File [] [] now, 
						OnFilesChangedListener onFilesChangedListener) { 
		boolean changed = false; 
		for (File f : folder) { 
			String pathPrefix = f.getAbsolutePath (); 
			Vector<String> lines = new Vector<> (now.length); 
			boolean needSave = old == null || old.length != now.length; 
			for (int i = 0; i < now.length; i++) { 
				for (File file : now[i]) 
					if (file.getAbsolutePath ().startsWith (pathPrefix)) { 
						String path = file.getAbsolutePath (); 
						lines.add (path); 
						if (needSave) continue; 
						boolean found = false; 
						for (File b : old[i]) 
							if (b.getAbsolutePath ().equals (path)) 
								found = true; 
						needSave = !found; 
					} 
			} 
			if (!needSave) continue; 
			changed = true; 
			try { 
				writeLines (new File (f, "ls.cache"), lines); 
			} catch (IOException err) { 
				err.printStackTrace (); 
			} 
		} 
		if (changed)
			onFilesChangedListener.onFilesChanged (now); 
		else onFilesChangedListener.onFilesNoChange (now); 
	} 
	
	public File [] [] asyncListFiles (final FileFilter filter, final OnFilesChangedListener listener) { 
		TreeMap<String,Vector<File>> children = new TreeMap<> (); 
		boolean noCacheFound = false; 
		final Vector<File> myFolder = mFolder; 
		for (File folder : myFolder) { 
			File listCache = new File (folder, "ls.cache"); 
			if (!listCache.exists ()) { 
				noCacheFound = true; 
				break; 
			} 
			try { 
				File list [] = makeFilesArray (readLines (listCache)); 
				for (File file : list) 
					if (!children.containsKey (file.getName ())) { 
						Vector<File> files = new Vector<> (); 
						files.add (file); 
						children.put (file.getName (), files); 
					} else children.get (file.getName ()).add (file); 
			} catch (IOException err) { 
				err.printStackTrace (); 
				noCacheFound = true; 
				break; 
			} 
		} 
		File list [] [] = new File [children.size ()] []; 
		if (!noCacheFound) { 
			int index = 0; 
			for (String name : children.keySet ()) { 
				Vector<File> possible = children.get (name); 
				list[index] = new File[possible.size ()]; 
				possible.toArray (list[index]); 
				index++; 
			} 
			final File [] [] oldList = list; 
			AsyncTask<Void,Void,File [] []> mTask = new AsyncTask<Void, Void, File[][]> () { 
				@Override protected File[][] doInBackground (Void... voids) { 
					return getFileLists (filter); 
				} 
				@Override protected void onPostExecute (File [] [] list) { 
					saveFileLists (myFolder, oldList, list, listener); 
				} 
			}; 
			mTask.execute (); 
		} else { 
			list = getFileLists (filter); 
			saveFileLists (myFolder, null, list, listener); 
		} 
		return list; 
	} 
	public File [] [] getFileLists (FileFilter filter) { 
		return getFileLists (filter, mFolder); 
	} 
	public static File [] [] getFileLists (FileFilter filter, Vector<File> inFolder) { 
		HashMap<String,Vector<File>> children = new HashMap<> (); 
		for (File folder : inFolder) { 
			File list [] = folder.listFiles (filter);  
			for (File file : list) 
				if (!children.containsKey (file.getName ())) { 
					Vector<File> files = new Vector<> (); 
					files.add (file); 
					children.put (file.getName (), files); 
				} else children.get (file.getName ()).add (file); 
		} 
		File list [] [] = new File [children.size ()] []; 
		int index = 0; 
		for (String name : children.keySet ()) { 
			Vector<File> possible = children.get (name); 
			list[index] = new File [possible.size ()]; 
			possible.toArray (list[index]); 
			index++; 
		} 
		Arrays.sort (list, mFileComparator); 
		return list; 
	} 
} 
