package org.xiph.vorbis.playback;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiph.vorbis.decoder.DecodeStreamInfo;
import org.xiph.vorbis.helper.JavaSoundVorbisPlayer;
import org.xiph.vorbis.helper.PlayerListener;

public class OggPlayback {
	private static final Logger LOG = LoggerFactory.getLogger(OggPlayback.class);

	public static void main(String[] args) throws Exception {
		final File playFile = new File(args[0]);
		JavaSoundVorbisPlayer player = new JavaSoundVorbisPlayer(playFile, new PlayerListener() {
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
		player.seekToSeconds(1);
		Thread.sleep(6000);
		player.seekToSeconds(-1);
		Thread.sleep(10000);
		player.stop();
	}
}
