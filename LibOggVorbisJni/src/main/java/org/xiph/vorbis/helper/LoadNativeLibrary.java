package org.xiph.vorbis.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadNativeLibrary {
	private static final String[] LIBRARY_NAMES_32 = new String[] { "libogg32", "libvorbis32", "libvorbis-jni32" };
	private static final String[] LIBRARY_NAMES_64 = new String[] { "libogg64", "libvorbis64", "libvorbis-jni64" };
	private static final boolean SKIP_IF_EXIST = false;
	private static final long STALE_LAST_MODIFIED = 1000 * 60 * 60 * 24;
	private static AtomicBoolean LOADING = new AtomicBoolean(false);

	private LoadNativeLibrary() {}

	public static void loadLibraryFiles() {
		if (LOADING.compareAndSet(false, true)) {
			File tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "lib-vorbis-jni");
			tempDir.mkdir();
			for (String resource : LIBRARY_NAMES_32) {
				saveClassPathResourceToDir(resource + ".dll", tempDir);
			}
			for (String resource : LIBRARY_NAMES_64) {
				saveClassPathResourceToDir(resource + ".dll", tempDir);
			}

			try {
				for (String resource : LIBRARY_NAMES_32) {
					System.load(tempDir.getAbsolutePath() + File.separator + resource + ".dll");
				}
			} catch (UnsatisfiedLinkError ule) {
				for (String resource : LIBRARY_NAMES_64) {
					System.load(tempDir.getAbsolutePath() + File.separator + resource + ".dll");
				}
			}
		} else {
			// do nothing already loaded;
		}
	}

	private static void saveClassPathResourceToDir(String resourceName, File directory) {
		byte[] buffer = new byte[2048];
		FileOutputStream fos = null;
		InputStream input = LoadNativeLibrary.class.getResourceAsStream("/" + resourceName);
		try {
			final File library = new File(directory.getAbsolutePath() + File.separator + resourceName);

			if (! library.exists() || library.length() == 0 || ! SKIP_IF_EXIST
			        || System.currentTimeMillis() - library.lastModified() > STALE_LAST_MODIFIED) {
				fos = new FileOutputStream(library);
				for (int read = 0; read != - 1; read = input.read(buffer)) {
					fos.write(buffer, 0, read);
				}
			}
		} catch (IOException ioe) {

		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException ioe) {
					// ignore
				}
			}
			if (input != null) {
				try {
					input.close();
				} catch (IOException ioe) {
					// ignore
				}
			}
		}
	}
}
