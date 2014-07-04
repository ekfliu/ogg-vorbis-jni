package org.xiph.vorbis.playback;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiph.vorbis.decoder.DecodeFeed;
import org.xiph.vorbis.decoder.DecodeStreamInfo;
import org.xiph.vorbis.decoder.VorbisDecoder;

import junit.framework.Assert;

public class OggFileTest {
	private static final Logger LOG = LoggerFactory.getLogger(OggFileTest.class);

	public static void main(String[] args) throws Exception {
		final File playFile = new File(args[0]);
		DecodeStreamInfo streamInfo = VorbisDecoder.decodeFileMetadata(playFile);
		Assert.assertTrue(streamInfo.getRuntimeSeconds() > 0);
	}
}
