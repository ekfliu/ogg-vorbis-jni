package org.xiph.vorbis.helper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiph.vorbis.decoder.DecodeFeed;
import org.xiph.vorbis.decoder.DecodeStreamInfo;
import org.xiph.vorbis.decoder.VorbisDecoder;

public class JavaSoundVorbisPlayer implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(JavaSoundVorbisPlayer.class);

	/**
	 * Playing state which can either be stopped, playing, or reading the header before playing
	 */
	private static enum PlayerState {
		PLAYING,
		STOPPED,
		READING_HEADER,
		BUFFERING
	}

	/**
	 * Playing finished handler message
	 */
	public static final int PLAYING_FINISHED = 46314;

	/**
	 * Playing failed handler message
	 */
	public static final int PLAYING_FAILED = 46315;

	/**
	 * Playing started handler message
	 */
	public static final int PLAYING_STARTED = 46316;

	/**
	 * Handler for sending status updates
	 */
	private final PlayerListener handler;

	/**
	 * Logging tag
	 */
	private static final String TAG = "VorbisPlayer";

	private final File decodeFile;
	/**
	 * The decode feed to read and write pcm/vorbis data respectively
	 */
	private final DecodeFeed decodeFeed;

	private volatile boolean paused = false;
	private volatile long seekSeconds = - 1;

	/**
	 * Current state of the vorbis player
	 */
	private final AtomicReference<PlayerState> currentState = new AtomicReference<PlayerState>(PlayerState.STOPPED);

	/**
	 * Custom class to easily decode from a file and write to an {@link AudioTrack}
	 */
	private class AudioOutOnlyDecodeFeed implements DecodeFeed {
		/**
		 * The audio track to write the raw pcm bytes to
		 */
		private SourceDataLine audioTrack;

		private byte[] convertBuffer = new byte[2];

		/**
		 * Creates a decode feed that reads from a file and writes to an {@link AudioTrack}
		 * 
		 * @param fileToDecode the file to decode
		 */
		private AudioOutOnlyDecodeFeed() throws FileNotFoundException {}

		@Override
		public synchronized int readVorbisData(byte[] buffer, int amountToWrite) {
			waitForResume();
			return 0;
		}

		@Override
		public synchronized boolean writePCMData(short[] pcmData, int amountToRead) {
			LOG.trace("FileDecodeFeed writePCMData() for {}...", amountToRead);
			// If we received data and are playing, write to the audio track
			if (pcmData != null && amountToRead > 0 && audioTrack != null && isPlaying()) {
				final int byteSize = convertToBuffer(pcmData, amountToRead);
				audioTrack.write(convertBuffer, 0, byteSize);
				waitForResume();
			}

			return currentState.get() != PlayerState.STOPPED;
		}

		protected int convertToBuffer(short[] pcmData, int amountToRead) {
			int byteSize = amountToRead * 2;
			if (byteSize > convertBuffer.length) {
				convertBuffer = new byte[byteSize];
			}

			for (int i = 0; i < amountToRead; i++) {
				convertBuffer[i * 2] = (byte) (pcmData[i] & 0x00FF);
				convertBuffer[i * 2 + 1] = (byte) ((pcmData[i] & 0xFF00) >> 8);
			}

			return byteSize;
		}

		@Override
		public void elapsedSeconds(long seconds) {
			LOG.trace("FileDecodeFeed elapsed {} seconds...", seconds);
			handler.sendPlayingProgress(seconds);
		}

		@Override
		public long seekToSeconds() {
			return seekSeconds;
		}

		@Override
		public void stop() {
			LOG.trace("FileDecodeFeed stop() called...");
			if (isPlaying() || isReadingHeader()) {
				// Stop the audio track
				if (audioTrack != null) {
					audioTrack.stop();
					audioTrack.close();
					audioTrack = null;
				}
			}

			// Set our state to stopped
			currentState.set(PlayerState.STOPPED);
			handler.sendEmptyMessage(PLAYING_FINISHED);
		}

		@Override
		public void start(DecodeStreamInfo decodeStreamInfo) {
			LOG.trace("FileDecodeFeed start() called...");
			if (currentState.get() != PlayerState.READING_HEADER) {
				throw new IllegalStateException("Must read header first!");
			}
			if (decodeStreamInfo.getChannels() != 1 && decodeStreamInfo.getChannels() != 2) {
				throw new IllegalArgumentException("Channels can only be one or two");
			}
			if (decodeStreamInfo.getSampleRate() <= 0) {
				throw new IllegalArgumentException("Invalid sample rate, must be above 0");
			}
			handler.sendDecodeStreamInfo(decodeStreamInfo);
			// Create the audio track
			audioTrack = getAudioFormatFromInput(decodeStreamInfo);
			audioTrack.start();
			// We're starting to read actual content
			currentState.set(PlayerState.PLAYING);
			handler.sendEmptyMessage(PLAYING_STARTED);
		}

		@Override
		public void startReadingHeader() {
			LOG.trace("FileDecodeFeed startReadingHeader() called...");
			currentState.set(PlayerState.READING_HEADER);
		}

	}

	/**
	 * Custom class to easily buffer and decode from a stream and write to an {@link AudioTrack}
	 */
	private class BufferedDecodeFeed implements DecodeFeed {
		/**
		 * The audio track to write the raw pcm bytes to
		 */
		private SourceDataLine audioTrack;

		/**
		 * The initial buffer size
		 */
		private final long bufferSize;

		/**
		 * The input stream to decode from
		 */
		private InputStream inputStream;

		/**
		 * The amount of written pcm data to the audio track
		 */
		private long writtenPCMData = 0;

		private byte[] convertBuffer = new byte[2];

		/**
		 * Creates a decode feed that reads from a file and writes to an {@link AudioTrack}
		 * 
		 * @param streamToDecode the stream to decode
		 */
		private BufferedDecodeFeed(InputStream streamToDecode, long bufferSize) {
			if (streamToDecode == null) {
				throw new IllegalArgumentException("Stream to decode must not be null.");
			}
			this.inputStream = streamToDecode;
			this.bufferSize = bufferSize;
		}

		@Override
		public int readVorbisData(byte[] buffer, int amountToWrite) {
			// If the player is not playing or reading the header, return 0 to
			// end the native decode method
			if (currentState.get() == PlayerState.STOPPED) {
				return 0;
			}

			// Otherwise read from the file
			try {

				LOG.trace("Reading...dataToRead:" + amountToWrite + " bufferLength:" + buffer.length);
				int read = inputStream.read(buffer, 0, amountToWrite);
				LOG.trace("Read... readCount" + read);
				waitForResume();
				return read == - 1 ? 0 : read;
			} catch (IOException e) {
				// There was a problem reading from the file
				LOG.error("Failed to read vorbis data from file.  Aborting.", e);
				return 0;
			}
		}

		@Override
		public boolean writePCMData(short[] pcmData, int amountToRead) {
			// If we received data and are playing, write to the audio track
			LOG.trace("Writing data to track, pcmData.length:{} amountToRead:{}", pcmData.length, amountToRead);
			if (pcmData != null && amountToRead > 0 && audioTrack != null && (isPlaying() || isBuffering())) {
				final int byteSize = convertToBuffer(pcmData, amountToRead);
				audioTrack.write(convertBuffer, 0, byteSize);
				writtenPCMData += amountToRead;
				if (writtenPCMData >= bufferSize) {
					audioTrack.start();
					currentState.set(PlayerState.PLAYING);
				}
				waitForResume();
			}
			return currentState.get() != PlayerState.STOPPED;
		}

		protected int convertToBuffer(short[] pcmData, int amountToRead) {
			int byteSize = amountToRead * 2;
			if (byteSize > convertBuffer.length) {
				convertBuffer = new byte[byteSize];
			}

			for (int i = 0; i < amountToRead; i++) {
				convertBuffer[i * 2] = (byte) (pcmData[i] & 0x00FF);
				convertBuffer[i * 2 + 1] = (byte) ((pcmData[i] & 0xFF00) >> 8);
			}

			return byteSize;
		}

		@Override
		public void elapsedSeconds(long seconds) {
			LOG.trace("elapsed {} seconds...", seconds);
			handler.sendPlayingProgress(seconds);
		}

		@Override
		public long seekToSeconds() {
			return seekSeconds;
		}

		@Override
		public void stop() {
			if (! isStopped()) {
				// If we were in a state of buffering before we actually started
				// playing, start playing and write some silence to the track
				if (currentState.get() == PlayerState.BUFFERING) {
					audioTrack.start();
					audioTrack.write(new byte[20000], 0, 20000);
				}

				// Closes the file input stream
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (IOException e) {
						LOG.error("Failed to close file input stream", e);
					}
					inputStream = null;
				}

				// Stop the audio track
				if (audioTrack != null) {
					audioTrack.stop();
					audioTrack.close();
					audioTrack = null;
				}
			}

			// Set our state to stopped
			currentState.set(PlayerState.STOPPED);
			handler.sendEmptyMessage(PLAYING_FINISHED);
		}

		@Override
		public void start(DecodeStreamInfo decodeStreamInfo) {
			if (currentState.get() != PlayerState.READING_HEADER) {
				throw new IllegalStateException("Must read header first!");
			}
			if (decodeStreamInfo.getChannels() != 1 && decodeStreamInfo.getChannels() != 2) {
				throw new IllegalArgumentException("Channels can only be one or two");
			}
			if (decodeStreamInfo.getSampleRate() <= 0) {
				throw new IllegalArgumentException("Invalid sample rate, must be above 0");
			}
			handler.sendDecodeStreamInfo(decodeStreamInfo);
			// Create the audio track
			audioTrack = getAudioFormatFromInput(decodeStreamInfo);
			audioTrack.start();

			// We're starting to read actual content
			currentState.set(PlayerState.BUFFERING);
		}

		@Override
		public void startReadingHeader() {
			if (isStopped()) {
				handler.sendEmptyMessage(PLAYING_STARTED);
				currentState.set(PlayerState.READING_HEADER);
			}
		}

	}

	/**
	 * Constructs a new instance of the player with default parameters other than it will decode from a file
	 * 
	 * @param fileToPlay the file to play
	 * @param handler handler to send player status updates to
	 * @throws FileNotFoundException thrown if the file could not be located/opened to playing
	 */
	public JavaSoundVorbisPlayer(File fileToPlay, PlayerListener handler) throws FileNotFoundException {
		if (fileToPlay == null) {
			throw new IllegalArgumentException("File to play must not be null.");
		}
		if (handler == null) {
			throw new IllegalArgumentException("Handler must not be null.");
		}
		this.decodeFile = fileToPlay;
		this.decodeFeed = new AudioOutOnlyDecodeFeed();
		this.handler = handler;
	}

	/**
	 * Constructs a player that will read from an {@link InputStream} and write to an {@link AudioTrack}
	 * 
	 * @param audioDataStream the audio data stream to read from
	 * @param handler handler to send player status updates to
	 */
	public JavaSoundVorbisPlayer(InputStream audioDataStream, PlayerListener handler) {
		if (audioDataStream == null) {
			throw new IllegalArgumentException("Input stream must not be null.");
		}
		if (handler == null) {
			throw new IllegalArgumentException("Handler must not be null.");
		}

		this.decodeFile = null;
		this.decodeFeed = new BufferedDecodeFeed(audioDataStream, 24000);
		this.handler = handler;
	}

	/**
	 * Constructs a player with a custom {@link DecodeFeed}
	 * 
	 * @param decodeFeed the custom decode feed
	 * @param handler handler to send player status updates to
	 */
	public JavaSoundVorbisPlayer(DecodeFeed decodeFeed, PlayerListener handler) {
		if (decodeFeed == null) {
			throw new IllegalArgumentException("Decode feed must not be null.");
		}
		if (handler == null) {
			throw new IllegalArgumentException("Handler must not be null.");
		}
		this.decodeFile = null;
		this.decodeFeed = decodeFeed;
		this.handler = handler;
	}

	public SourceDataLine getAudioFormatFromInput(DecodeStreamInfo decodeStreamInfo) {
		final AudioFormat format = new AudioFormat(decodeStreamInfo.getSampleRate(), 16, (int) decodeStreamInfo.getChannels(), true, false);

		final Info info = new Info(SourceDataLine.class, format);
		SourceDataLine dataLine = null;
		try {
			dataLine = (SourceDataLine) AudioSystem.getLine(info);
			dataLine.open(format, 32768 * format.getChannels());
			return dataLine;
		} catch (LineUnavailableException e1) {
			LOG.error("LineUnavailableException while attempting to get line out for " + decodeStreamInfo, e1);
			return null;
		}
	}

	/**
	 * Starts the audio recorder with a given sample rate and channels
	 */
	@SuppressWarnings("all")
	public synchronized void start() {
		if (isStopped()) {
			new Thread(this).start();
		}
	}

	/**
	 * Stops the player and notifies the decode feed
	 */
	public synchronized void stop() {
		awake();
		decodeFeed.stop();
	}

	protected synchronized void awake() {
		paused = false;
		this.notifyAll();
	}

	protected void waitForResume() {
		if (paused && currentState.get() == PlayerState.PLAYING) {
			LOG.trace("Player is paused");
			synchronized (this) {
				try {
					while (paused && currentState.get() == PlayerState.PLAYING) {
						JavaSoundVorbisPlayer.this.wait();
					}
				} catch (InterruptedException ie) {}
			}
		}
	}

	public synchronized void pause() {
		if (! paused) {
			paused = true;
		}
	}

	public synchronized void resume() {
		if (paused) {
			paused = false;
			this.notifyAll();
		}
	}

	public synchronized void seekToSeconds(long seekSeconds) {
		this.seekSeconds = seekSeconds;
	}

	@Override
	public void run() {
		// Start the native decoder
		int result;
		if (decodeFile != null) {
			result = VorbisDecoder.startDecodingFile(decodeFile, decodeFeed);
		} else {
			result = VorbisDecoder.startDecoding(decodeFeed);
		}
		switch (result) {
			case DecodeFeed.SUCCESS:
				LOG.debug("Successfully finished decoding");
				handler.sendEmptyMessage(PLAYING_FINISHED);
				break;
			case DecodeFeed.INVALID_OGG_BITSTREAM:
				handler.sendEmptyMessage(PLAYING_FAILED);
				LOG.error("Invalid ogg bitstream error received");
				break;
			case DecodeFeed.ERROR_READING_FIRST_PAGE:
				handler.sendEmptyMessage(PLAYING_FAILED);
				LOG.error("Error reading first page error received");
				break;
			case DecodeFeed.ERROR_READING_INITIAL_HEADER_PACKET:
				handler.sendEmptyMessage(PLAYING_FAILED);
				LOG.error("Error reading initial header packet error received");
				break;
			case DecodeFeed.NOT_VORBIS_HEADER:
				handler.sendEmptyMessage(PLAYING_FAILED);
				LOG.error("Not a vorbis header error received");
				break;
			case DecodeFeed.CORRUPT_SECONDARY_HEADER:
				handler.sendEmptyMessage(PLAYING_FAILED);
				LOG.error("Corrupt secondary header error received");
				break;
			case DecodeFeed.PREMATURE_END_OF_FILE:
				handler.sendEmptyMessage(PLAYING_FAILED);
				LOG.error("Premature end of file error received");
				break;
		}
	}

	/**
	 * Checks whether the player is currently playing
	 * 
	 * @return <code>true</code> if playing, <code>false</code> otherwise
	 */
	public synchronized boolean isPlaying() {
		return currentState.get() == PlayerState.PLAYING;
	}

	/**
	 * Checks whether the player is currently stopped (not playing)
	 * 
	 * @return <code>true</code> if playing, <code>false</code> otherwise
	 */
	public synchronized boolean isStopped() {
		return currentState.get() == PlayerState.STOPPED;
	}

	/**
	 * Checks whether the player is currently reading the header
	 * 
	 * @return <code>true</code> if reading the header, <code>false</code> otherwise
	 */
	public synchronized boolean isReadingHeader() {
		return currentState.get() == PlayerState.READING_HEADER;
	}

	/**
	 * Checks whether the player is currently buffering
	 * 
	 * @return <code>true</code> if buffering, <code>false</code> otherwise
	 */
	public synchronized boolean isBuffering() {
		return currentState.get() == PlayerState.BUFFERING;
	}
}