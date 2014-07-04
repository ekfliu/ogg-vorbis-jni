package org.xiph.vorbis.decoder;

import java.io.Serializable;

/**
 * User: vincent Date: 3/29/13 Time: 8:17 PM
 */
public class DecodeStreamInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	private long sampleRate;
	private long channels;
	private String vendor;
	private long runtimeSeconds;

	public DecodeStreamInfo(long sampleRate, long channels, String vendor) {
		this(sampleRate, channels, vendor, - 1);
	}

	public DecodeStreamInfo(long sampleRate, long channels, String vendor, long runtimeSeconds) {
		this.sampleRate = sampleRate;
		this.channels = channels;
		this.vendor = vendor;
		this.runtimeSeconds = runtimeSeconds;
	}

	public long getSampleRate() {
		return sampleRate;
	}

	public void setSampleRate(long sampleRate) {
		this.sampleRate = sampleRate;
	}

	public long getChannels() {
		return channels;
	}

	public void setChannels(long channels) {
		this.channels = channels;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public void setRuntimeSeconds(long runtimeSeconds) {
		this.runtimeSeconds = runtimeSeconds;
	}

	public long getRuntimeSeconds() {
		return runtimeSeconds;
	}

	@Override
	public String toString() {
		return "DecodeStreamInfo [sampleRate=" + sampleRate + ", channels=" + channels + ", vendor=" + vendor + ", runtimeSeconds="
		        + runtimeSeconds + "]";
	}

}
