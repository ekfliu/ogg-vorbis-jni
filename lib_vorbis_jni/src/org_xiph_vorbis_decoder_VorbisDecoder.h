#define MIMIC_ANDROID 1

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <stdarg.h>
#include <vorbis/codec.h>
#include <vorbis/vorbisfile.h>
#include <jni.h>
#ifdef _WIN32
#include <io.h>
#include <fcntl.h>
#endif

#if MIMIC_ANDROID
#include <jni_md.h>
#else
#include <android/log.h>
#endif



#ifndef _Included_org_xiph_vorbis_VorbisDecoder
#define _Included_org_xiph_vorbis_VorbisDecoder
#ifdef __cplusplus
extern "C" {
#endif

#if MIMIC_ANDROID
#define ANDROID_LOG_INFO "INFO"
#define ANDROID_LOG_WARN "WARN"
#define ANDROID_LOG_ERROR "ERROR"
#define ANDROID_LOG_DEBUG "DEBUG"

int __android_log_print(int prio, const char *tag, const char *fmt, ...);
int __android_log_write(int prio, const char *tag, const char *fmt, ...);
#endif

//Starts the decoding from a vorbis bitstream to pcm
/*
 * Class:     org_xiph_vorbis_decoder_VorbisDecoder
 * Method:    startDecoding
 * Signature: (Lorg/xiph/vorbis/decoder/DecodeFeed;)I
 */
JNIEXPORT jint JNICALL Java_org_xiph_vorbis_decoder_VorbisDecoder_startDecoding
  (JNIEnv *env, jclass cls, jobject vorbisDataFeed);

/*
 * Class:     org_xiph_vorbis_decoder_VorbisDecoder
 * Method:    startDecodingFile
 * Signature: (Ljava/io/File;Lorg/xiph/vorbis/decoder/DecodeFeed;)I
 */
JNIEXPORT jint JNICALL Java_org_xiph_vorbis_decoder_VorbisDecoder_startDecodingFile
  (JNIEnv *env, jclass cls, jobject file, jobject vorbisDataFeed);

/*
 * Class:     org_xiph_vorbis_decoder_VorbisDecoder
 * Method:    decodeFileMetadata
 * Signature: (Ljava/io/File;)Lorg/xiph/vorbis/decoder/DecodeStreamInfo;
 */
JNIEXPORT jobject JNICALL Java_org_xiph_vorbis_decoder_VorbisDecoder_decodeFileMetadata
  (JNIEnv *env, jclass cls, jobject vorbisFile);

//callback on elasped time in seconds
int elapsedSecondVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* elapsedMethodId, int elapsed_seconds);

//Stops the vorbis data feed
void stopDecodeFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* stopMethodId);

//Reads raw vorbis data from the jni callback
int readVorbisDataFromVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* readVorbisDataMethodId, char* buffer, jbyteArray* jByteArrayReadBuffer);

//Writes the pcm data to the Java layer
jboolean writePCMDataFromVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* writePCMDataMethodId, ogg_int16_t* buffer, int bytes, jshortArray* jShortArrayWriteBuffer);

//Starts the decode feed with the necessary information about sample rates, channels, etc about the stream
void start(JNIEnv *env, jobject *vorbisDataFeed, jmethodID* startMethodId, long sampleRate, long channels, char* vendor, long playtime);

//Starts reading the header information
void startReadingHeader(JNIEnv *env, jobject *vorbisDataFeed, jmethodID* startReadingHeaderMethodId);

#ifdef __cplusplus
}
#endif
#endif
