#include <stdio.h> 
#include <string.h> 

typedef unsigned char byte; 

void packageResource (FILE * output, char * filename) { 
	char dotExt [4] = {0}; 
	size_t len = strlen (filename); 
	size_t lastDot = len; 
	while (lastDot > 0 && filename[lastDot] != '.') lastDot--; 
	if (!lastDot) { 
		printf ("Could not determine filename extension of %s", filename); 
		return; 
	} 
	printf ("Packaging file %s\n", filename); 
	for (size_t i = 0; i < 4 && lastDot + i < len; i++) 
		dotExt[i] = filename[lastDot + i]; 
	FILE * input = fopen (filename, "rb"); 
	fseek (input, 0, SEEK_END); 
	long int fileSize = ftell (input); 
	fseek (input, 0, SEEK_SET); 
	byte szBuf [4] = {0}; 
	szBuf[0] = (byte) (fileSize >> 24); 
	szBuf[1] = (byte) ((fileSize >> 16) & 0xFF); 
	szBuf[2] = (byte) ((fileSize >>  8) & 0xFF); 
	szBuf[3] = (byte) (fileSize & 0xFF); 
	fwrite (szBuf, 4, 1, output); 
	fwrite (dotExt, 4, 1, output); 
	byte buf [4096]; 
	size_t ofs = 0; 
	while (ofs < fileSize - 4096) { 
		size_t bRead = fread (buf, 1, 4096, input); 
		if (!bRead) { 
			if (ofs < fileSize) 
				printf ("Could only read %d bytes from a file of size %d; bRead = %d\n", ofs, fileSize, bRead); 
			break; 
		} 
		fwrite (buf, 1, bRead, output); 
		ofs += bRead; 
	} 
	if (ofs < fileSize) { 
		size_t bRead = fread (buf, 1, fileSize - ofs, input); 
		fwrite (buf, 1, bRead, output); 
	} 
	fclose (input); 
} 

int main (int argc, char * argv []) { 
	if (argc < 2) return 0; 
	FILE * file = fopen (argv[1], "wb+"); 
	fprintf (file, "GE12"); 
	size_t resCount = argc - 2; 
	byte szBuf [8] = {0}; 
	szBuf[4] = (byte) (resCount >> 24); 
	szBuf[5] = (byte) ((resCount >> 16) & 0xFF); 
	szBuf[6] = (byte) ((resCount >>  8) & 0xFF); 
	szBuf[7] = (byte) (resCount & 0xFF); 
	fwrite (szBuf, 4, 2, file); 
	for (size_t i = 0; i < resCount; i++) 
		packageResource (file, argv[2 + i]); 
	long int streamPos = ftell (file); 
	fseek (file, 4, SEEK_SET); 
	szBuf[0] = (byte) (streamPos >> 24); 
	szBuf[1] = (byte) ((streamPos >> 16) & 0xFF); 
	szBuf[2] = (byte) ((streamPos >>  8) & 0xFF); 
	szBuf[3] = (byte) (streamPos & 0xFF); 
	fwrite (szBuf, 4, 1, file); 
	fseek (file, streamPos, SEEK_SET); 
	// Possibly also write some stroke data here. 
	fclose (file); 
	printf ("Done.\n"); 
	return 0; 
} 

