package org.xiph.vorbis.helper;

import org.xiph.vorbis.decoder.DecodeStreamInfo;

public interface PlayerListener {
	void sendEmptyMessage(int message);

	void sendDecodeStreamInfo(DecodeStreamInfo streamInfo);

	void sendPlayingProgress(long progressSeconds);
}
