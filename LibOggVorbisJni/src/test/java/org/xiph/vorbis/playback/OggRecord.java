package org.xiph.vorbis.playback;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiph.vorbis.helper.JavaSoundVorbisRecorder;
import org.xiph.vorbis.helper.RecorderListener;

public class OggRecord {
	private static final Logger LOG = LoggerFactory.getLogger(OggPlayback.class);

	public static void main(String[] args) throws Exception {
		final File recordFile = new File(args[0]);
		final JavaSoundVorbisRecorder recorder = new JavaSoundVorbisRecorder(recordFile, new RecorderListener() {
			@Override
			public void sendEmptyMessage(int message) {
				LOG.debug("EmptyMessage " + message);
			}
		});
		recorder.start(44100, 1l, 0.8f);
		Thread.sleep(5000);
		recorder.stop();
	}
}
