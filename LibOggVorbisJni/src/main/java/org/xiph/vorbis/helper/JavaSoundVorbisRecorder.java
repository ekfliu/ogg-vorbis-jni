package org.xiph.vorbis.helper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiph.vorbis.encoder.EncodeFeed;
import org.xiph.vorbis.encoder.VorbisEncoder;

/**
 * The VorbisRecorder is responsible for receiving raw pcm data from the {@link AudioRecord} and feeding that data to the native
 * {@link VorbisEncoder}
 * <p/>
 * This class is primarily intended as a demonstration of how to work with the JNI java interface {@link VorbisEncoder}
 * <p/>
 * User: vincent Date: 3/28/13 Time: 12:47 PM
 */
public class JavaSoundVorbisRecorder {
	private static final Logger LOG = LoggerFactory.getLogger(JavaSoundVorbisRecorder.class);
	/**
	 * Vorbis recorder status flag to notify handler to start encoding
	 */
	public static final int START_ENCODING = 1;

	/**
	 * Vorbis recorder status flag to notify handler to that it has stopped encoding
	 */
	public static final int STOP_ENCODING = 2;

	/**
	 * Vorbis recorder status flag to notify handler that the recorder has finished successfully
	 */
	public static final int FINISHED_SUCCESSFULLY = 0;

	/**
	 * Vorbis recorder status flag to notify handler that the encoder has failed for an unknown reason
	 */
	public static final int FAILED_FOR_UNKNOWN_REASON = - 2;

	/**
	 * Vorbis recorder status flag to notify handler that the encoder couldn't initialize an {@link AudioRecord}
	 */
	public static final int UNSUPPORTED_AUDIO_TRACK_RECORD_PARAMETERS = - 3;

	/**
	 * Vorbis recorder status flag to notify handler that the encoder has failed to initialize properly
	 */
	public static final int ERROR_INITIALIZING = - 1;

	/**
	 * Whether the recording will encode with a quality percent or average bitrate
	 */
	private static enum RecordingType {
		WITH_QUALITY,
		WITH_BITRATE
	}

	/**
	 * The record handler to post status updates to
	 */
	private final RecorderListener recordHandler;

	/**
	 * The sample rate of the recorder
	 */
	private long sampleRate;

	/**
	 * The number of channels for the recorder
	 */
	private long numberOfChannels;

	/**
	 * The output quality of the encoding
	 */
	private float quality;

	/**
	 * The target encoding bitrate
	 */
	private long bitrate;

	/**
	 * Whether the recording will encode with a quality percent or average bitrate
	 */
	private RecordingType recordingType;

	/**
	 * The state of the recorder
	 */
	private static enum RecorderState {
		RECORDING,
		STOPPED,
		STOPPING
	}

	/**
	 * Logging tag
	 */
	private static final String TAG = "VorbisRecorder";

	/**
	 * The encode feed to feed raw pcm and write vorbis data
	 */
	private final EncodeFeed encodeFeed;

	/**
	 * The current state of the recorder
	 */
	private final AtomicReference<RecorderState> currentState = new AtomicReference<RecorderState>(RecorderState.STOPPED);

	/**
	 * Helper class that implements {@link EncodeFeed} that will write the processed vorbis data to a file and will read raw PCM
	 * data from an {@link AudioRecord}
	 */
	private class FileEncodeFeed implements EncodeFeed {
		/**
		 * The file to write to
		 */
		private final File fileToSaveTo;

		/**
		 * The output stream to write the vorbis data to
		 */
		private OutputStream outputStream;

		/**
		 * The audio recorder to pull raw pcm data from
		 */
		private TargetDataLine audioRecorder;

		/**
		 * Constructs a file encode feed to write the encoded vorbis output to
		 * 
		 * @param fileToSaveTo the file to save to
		 */
		public FileEncodeFeed(File fileToSaveTo) {
			if (fileToSaveTo == null) {
				throw new IllegalArgumentException("File to save to must not be null");
			}
			this.fileToSaveTo = fileToSaveTo;
		}

		@Override
		public long readPCMData(byte[] pcmDataBuffer, int amountToRead) {
			// If we are no longer recording, return 0 to let the native encoder
			// know
			if (isStopped() || isStopping()) {
				return 0;
			}

			// Otherwise read from the audio recorder
			int read = audioRecorder.read(pcmDataBuffer, 0, amountToRead);
			LOG.trace("FileEncodeFeed readPCMData() for {} out of {}...", read, amountToRead);
			switch (read) {
				case - 1:
					return 0;
				default:
					// Successfully read from audio recorder
					return read;
			}
		}

		@Override
		public int writeVorbisData(byte[] vorbisData, int amountToWrite) {
			LOG.trace("FileEncodeFeed writeVorbisData() for {}...", amountToWrite);
			// If we have data to write and we are recording, write the data
			if (vorbisData != null && amountToWrite > 0 && outputStream != null && ! isStopped()) {
				try {
					// Write the data to the output stream
					outputStream.write(vorbisData, 0, amountToWrite);
					return amountToWrite;
				} catch (IOException e) {
					// Failed to write to the file
					LOG.error("Failed to write data to file, stopping recording", e);
					stop();
				}
			}
			// Otherwise let the native encoder know we are done
			return 0;
		}

		@Override
		public void stop() {
			LOG.trace("FileEncodeFeed stop() called...");
			if (isRecording() || isStopping()) {
				// Set our state to stopped
				currentState.set(RecorderState.STOPPED);

				// Close the output stream
				if (outputStream != null) {
					try {
						outputStream.flush();
						outputStream.close();
					} catch (IOException e) {
						LOG.error("Failed to close output stream", e);
					}
					outputStream = null;
				}

				// Stop and clean up the audio recorder
				if (audioRecorder != null) {
					audioRecorder.stop();
					audioRecorder.close();
					audioRecorder = null;
				}
				recordHandler.sendEmptyMessage(STOP_ENCODING);
			}
		}

		@Override
		public void stopEncoding() {
			LOG.trace("FileEncodeFeed stopEncoding() called...");
			if (isRecording()) {
				// Set our state to stopped
				currentState.set(RecorderState.STOPPING);
			}
		}

		@Override
		public void start() {
			LOG.trace("FileEncodeFeed start() called...");
			if (isStopped()) {
				// Creates the audio recorder
				audioRecorder = getAudioRecorder();
				if (audioRecorder == null) {
					return;
				}
				// Start recording
				currentState.set(RecorderState.RECORDING);
				audioRecorder.start();

				// Create the output stream
				if (outputStream == null) {
					try {
						outputStream = new BufferedOutputStream(new FileOutputStream(fileToSaveTo));
					} catch (FileNotFoundException e) {
						LOG.error("Failed to write to file", e);
					}
				}
				recordHandler.sendEmptyMessage(START_ENCODING);
			}
		}

	}

	/**
	 * Helper class that implements {@link EncodeFeed} that will write the processed vorbis data to an output stream and will read
	 * raw PCM data from an {@link AudioRecord}
	 */
	private class OutputStreamEncodeFeed implements EncodeFeed {
		/**
		 * The output stream to write the vorbis data to
		 */
		private OutputStream outputStream;

		/**
		 * The audio recorder to pull raw pcm data from
		 */
		private TargetDataLine audioRecorder;

		/**
		 * Constructs a file encode feed to write the encoded vorbis output to
		 * 
		 * @param outputStream the {@link OutputStream} to write the encoded information to
		 */
		public OutputStreamEncodeFeed(OutputStream outputStream) {
			if (outputStream == null) {
				throw new IllegalArgumentException("The output stream must not be null");
			}
			this.outputStream = outputStream;
		}

		@Override
		public long readPCMData(byte[] pcmDataBuffer, int amountToRead) {
			// If we are no longer recording, return 0 to let the native encoder
			// know
			if (isStopped() || isStopping()) {
				return 0;
			}
			LOG.trace("amount to read from pcm recorder " + amountToRead);
			// Otherwise read from the audio recorder
			int read = audioRecorder.read(pcmDataBuffer, 0, amountToRead);
			switch (read) {
				case - 1:
					return 0;
				default:
					// Successfully read from audio recorder
					return read;
			}
		}

		@Override
		public int writeVorbisData(byte[] vorbisData, int amountToWrite) {
			// If we have data to write and we are recording, write the data
			if (vorbisData != null && amountToWrite > 0 && outputStream != null && ! isStopped()) {
				try {
					LOG.trace("amount to write to ogg file " + amountToWrite);
					// Write the data to the output stream
					outputStream.write(vorbisData, 0, amountToWrite);
					return amountToWrite;
				} catch (IOException e) {
					// Failed to write to the file
					LOG.error("Failed to write data to file, stopping recording", e);
					stop();
				}
			}
			// Otherwise let the native encoder know we are done
			return 0;
		}

		@Override
		public void stop() {

			if (isRecording() || isStopping()) {
				// Set our state to stopped
				currentState.set(RecorderState.STOPPED);

				// Close the output stream
				if (outputStream != null) {
					try {
						outputStream.flush();
						outputStream.close();
					} catch (IOException e) {
						LOG.error("Failed to close output stream", e);
					}
					outputStream = null;
				}

				// Stop and clean up the audio recorder
				if (audioRecorder != null) {
					audioRecorder.stop();
					audioRecorder.close();
				}
				recordHandler.sendEmptyMessage(STOP_ENCODING);
			}
		}

		@Override
		public void stopEncoding() {
			if (isRecording()) {
				// Set our state to stopped
				currentState.set(RecorderState.STOPPING);
			}
		}

		@Override
		public void start() {
			if (isStopped()) {
				// Creates the audio recorder
				audioRecorder = getAudioRecorder();
				if (audioRecorder == null) {
					return;
				}
				// Start recording
				currentState.set(RecorderState.RECORDING);
				audioRecorder.start();
				recordHandler.sendEmptyMessage(START_ENCODING);
			}
		}
	}

	protected TargetDataLine getAudioRecorder() {
		try {
			final AudioFormat recordFormat = getRecordAudioFormat();
			final DataLine.Info recordDataInfo = new DataLine.Info(TargetDataLine.class, recordFormat);
			final TargetDataLine audioLine = (TargetDataLine) AudioSystem.getLine(recordDataInfo);
			audioLine.open(recordFormat);
			return audioLine;
		} catch (IllegalArgumentException iae) {
			LOG.warn("IllegalArgumentException while attempting to get java sound recorder", iae);
			recordHandler.sendEmptyMessage(UNSUPPORTED_AUDIO_TRACK_RECORD_PARAMETERS);
		} catch (LineUnavailableException lue) {
			LOG.warn("LineUnavailableException while attempting to get java sound recorder", lue);
			recordHandler.sendEmptyMessage(UNSUPPORTED_AUDIO_TRACK_RECORD_PARAMETERS);
		}
		return null;
	}

	protected AudioFormat getRecordAudioFormat() {
		float sampleRate = this.sampleRate;
		// 8000,11025,16000,22050,44100
		int sampleSizeInBits = 16;
		// 8,16
		int channels = (int) numberOfChannels;
		// 1,2
		boolean signed = true;
		// true,false
		boolean bigEndian = false;
		// true,false
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
	}

	/**
	 * Constructs a recorder that will record an ogg file
	 * 
	 * @param fileToSaveTo the file to save to
	 * @param recordHandler the handler for receiving status updates about the recording process
	 */
	public JavaSoundVorbisRecorder(File fileToSaveTo, RecorderListener recordHandler) {
		if (fileToSaveTo == null) {
			throw new IllegalArgumentException("File to play must not be null.");
		}

		// Delete the file if it exists
		if (fileToSaveTo.exists()) {
			fileToSaveTo.delete();
		}

		this.encodeFeed = new FileEncodeFeed(fileToSaveTo);
		this.recordHandler = recordHandler;
	}

	/**
	 * Constructs a recorder that will record an ogg output stream
	 * 
	 * @param streamToWriteTo the output stream to write the encoded information to
	 * @param recordHandler the handler for receiving status updates about the recording process
	 */
	public JavaSoundVorbisRecorder(OutputStream streamToWriteTo, RecorderListener recordHandler) {
		if (streamToWriteTo == null) {
			throw new IllegalArgumentException("File to play must not be null.");
		}

		this.encodeFeed = new OutputStreamEncodeFeed(streamToWriteTo);
		this.recordHandler = recordHandler;
	}

	/**
	 * Constructs a vorbis recorder with a custom {@link EncodeFeed}
	 * 
	 * @param encodeFeed the custom {@link EncodeFeed}
	 * @param recordHandler the handler for receiving status updates about the recording process
	 */
	public JavaSoundVorbisRecorder(EncodeFeed encodeFeed, RecorderListener recordHandler) {
		if (encodeFeed == null) {
			throw new IllegalArgumentException("Encode feed must not be null.");
		}

		this.encodeFeed = encodeFeed;
		this.recordHandler = recordHandler;
	}

	/**
	 * Starts the recording/encoding process
	 * 
	 * @param sampleRate the rate to sample the audio at, should be greater than <code>0</code>
	 * @param numberOfChannels the nubmer of channels, must only be <code>1/code> or <code>2</code>
	 * @param quality the quality at which to encode, must be between <code>-0.1</code> and <code>1.0</code>
	 */
	@SuppressWarnings("all")
	public synchronized void start(long sampleRate, long numberOfChannels, float quality) {
		if (isStopped()) {
			if (numberOfChannels != 1 && numberOfChannels != 2) {
				throw new IllegalArgumentException("Channels can only be one or two");
			}
			if (sampleRate <= 0) {
				throw new IllegalArgumentException("Invalid sample rate, must be above 0");
			}
			if (quality < - 0.1f || quality > 1.0f) {
				throw new IllegalArgumentException("Quality must be between -0.1 and 1.0");
			}

			this.sampleRate = sampleRate;
			this.numberOfChannels = numberOfChannels;
			this.quality = quality;
			this.recordingType = RecordingType.WITH_QUALITY;

			// Starts the recording process
			new Thread(new AsyncEncoding()).start();
		}
	}

	/**
	 * Starts the recording/encoding process
	 * 
	 * @param sampleRate the rate to sample the audio at, should be greater than <code>0</code>
	 * @param numberOfChannels the nubmer of channels, must only be <code>1/code> or <code>2</code>
	 * @param bitrate the bitrate at which to encode, must be greater than <code>-0</code>
	 */
	@SuppressWarnings("all")
	public synchronized void start(long sampleRate, long numberOfChannels, long bitrate) {
		if (isStopped()) {
			if (numberOfChannels != 1 && numberOfChannels != 2) {
				throw new IllegalArgumentException("Channels can only be one or two");
			}
			if (sampleRate <= 0) {
				throw new IllegalArgumentException("Invalid sample rate, must be above 0");
			}
			if (bitrate <= 0) {
				throw new IllegalArgumentException("Target bitrate must be greater than 0");
			}

			this.sampleRate = sampleRate;
			this.numberOfChannels = numberOfChannels;
			this.bitrate = bitrate;
			this.recordingType = RecordingType.WITH_BITRATE;

			// Starts the recording process
			new Thread(new AsyncEncoding()).start();
		}
	}

	/**
	 * Stops the audio recorder and notifies the {@link EncodeFeed}
	 */
	public synchronized void stop() {
		encodeFeed.stopEncoding();
	}

	/**
	 * Starts the encoding process in a background thread
	 */
	private class AsyncEncoding implements Runnable {
		@Override
		public void run() {
			// Start the native encoder
			int result = 0;
			switch (recordingType) {
				case WITH_BITRATE:
					result = VorbisEncoder.startEncodingWithBitrate(sampleRate, numberOfChannels, bitrate, encodeFeed);
					break;
				case WITH_QUALITY:
					result = VorbisEncoder.startEncodingWithQuality(sampleRate, numberOfChannels, quality, encodeFeed);
					break;
			}
			switch (result) {
				case EncodeFeed.SUCCESS:
					LOG.debug("Encoder successfully finished");
					recordHandler.sendEmptyMessage(FINISHED_SUCCESSFULLY);
					break;
				case EncodeFeed.ERROR_INITIALIZING:
					recordHandler.sendEmptyMessage(ERROR_INITIALIZING);
					LOG.error("There was an error initializing the native encoder");
					break;
				default:
					recordHandler.sendEmptyMessage(FAILED_FOR_UNKNOWN_REASON);
					LOG.error("Encoder returned an unknown result code");
					break;
			}
		}
	}

	/**
	 * Checks whether the recording is currently recording
	 * 
	 * @return <code>true</code> if recording, <code>false</code> otherwise
	 */
	public synchronized boolean isRecording() {
		return currentState.get() == RecorderState.RECORDING;
	}

	/**
	 * Checks whether the recording is currently stopped (not recording)
	 * 
	 * @return <code>true</code> if stopped, <code>false</code> otherwise
	 */
	public synchronized boolean isStopped() {
		return currentState.get() == RecorderState.STOPPED;
	}

	/**
	 * Checks whether the recording is currently stopping (not recording)
	 * 
	 * @return <code>true</code> if stopping, <code>false</code> otherwise
	 */
	public synchronized boolean isStopping() {
		return currentState.get() == RecorderState.STOPPING;
	}
}
