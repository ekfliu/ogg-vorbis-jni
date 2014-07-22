package org.xiph.vorbis.playback;

import java.io.File;
import java.io.FileInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiph.vorbis.decoder.DecodeStreamInfo;
import org.xiph.vorbis.helper.JavaSoundVorbisPlayer;
import org.xiph.vorbis.helper.PlayerListener;

public class OggStreamPlayback {
	private static final Logger LOG = LoggerFactory.getLogger(OggStreamPlayback.class);

	public static void main(String[] args) throws Exception {
		final File playFile = new File(args[0]);
		final FileInputStream input = new FileInputStream(playFile);
		JavaSoundVorbisPlayer player = new JavaSoundVorbisPlayer(input, new PlayerListener() {
			@Override
			public void sendEmptyMessage(int message) {
				LOG.debug("EmptyMessage " + message);
			}

			@Override
			public void sendDecodeStreamInfo(DecodeStreamInfo streamInfo) {
				LOG.debug("sendDecodeStreamInfo " + streamInfo);
			}

			@Override
			public void sendPlayingProgress(long progressSeconds) {
				LOG.debug("sendPlayingProgress " + progressSeconds);
			}

		});
		player.start();
		Thread.sleep(6000);
		player.pause();
		Thread.sleep(2000);
		player.resume();
		Thread.sleep(10000);
		player.pause();
		Thread.sleep(200);
		player.stop();
		input.close();
	}
}
