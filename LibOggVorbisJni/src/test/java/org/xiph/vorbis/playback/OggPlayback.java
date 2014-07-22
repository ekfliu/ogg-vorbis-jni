package org.xiph.vorbis.playback;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

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

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String line = null;

		while (! "quit".equals(line)) {
			System.out.println("Enter \"quit\" to exit recording");
			System.out.println("Enter \"play\" to play");
			System.out.println("Enter \"stop\" to stop");
			System.out.println("Enter \"pause\" to pause");
			System.out.println("Enter \"resume\" to resume");
			System.out.println("Enter \"seek <seconds>\" to seek");
			line = reader.readLine();
			if ("play".equals(line)) {
				System.out.println("start playing");
				player.start();
			} else if ("stop".equals(line)) {
				System.out.println("stop playing");
				player.stop();
			} else if ("pause".equals(line)) {
				System.out.println("pause playing");
				player.pause();
			} else if ("resume".equals(line)) {
				System.out.println("resume playing");
				player.resume();
			} else if (line != null && line.startsWith("seek")) {
				int seconds = Integer.parseInt(line.substring("seek".length()).trim());
				System.out.println("seeking to " + seconds + " seconds");
				player.seekToSeconds(seconds);
			}
		}
		player.stop();
	}
}
