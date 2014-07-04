package org.xiph.vorbis.decoder;

import java.io.File;

import org.xiph.vorbis.helper.LoadNativeLibrary;

/**
 * The native vorbis decoder to be used in conjunction with JNI User: vincent Date: 3/27/13 Time: 9:07 AM
 */
public class VorbisDecoder {

	/**
	 * Load our vorbis-jni library and other dependent libraries
	 */
	static {
		LoadNativeLibrary.loadLibraryFiles();
	}

	/**
	 * Start decoding the data by way of a jni call
	 * 
	 * @param decodeFeed the custom decode feed
	 * @return the result code
	 */
	public static native int startDecoding(DecodeFeed decodeFeed);
	
	public static native int startDecodingFile(File file, DecodeFeed decodeFeed);

	public static native DecodeStreamInfo decodeFileMetadata(File file);
}
