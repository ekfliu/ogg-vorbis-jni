/* Takes a vorbis bitstream from java callbacks from JNI and writes raw stereo PCM to
the jni callbacks. Decodes simple and chained OggVorbis files from beginning
to end. */

#include "org_xiph_vorbis_decoder_VorbisDecoder.h"

/*Define message codes*/
#define INVALID_OGG_BITSTREAM -21
#define ERROR_READING_FIRST_PAGE -22
#define ERROR_READING_INITIAL_HEADER_PACKET -23
#define NOT_VORBIS_HEADER -24
#define CORRUPT_SECONDARY_HEADER -25
#define PREMATURE_END_OF_FILE -26
#define SUCCESS 0

#define BUFFER_LENGTH 4096
#define DEBUG 0

extern void _VDBG_dump(void);

#if MIMIC_ANDROID
int __android_log_print(int prio, const char *tag, const char *fmt, ...) {
#if DEBUG
	va_list arglist;
	va_start(arglist, fmt);
	fprintf(stderr, "%s ", tag);
	vfprintf(stderr, fmt, arglist);
	fprintf(stderr, "\n");
	fflush(stderr);
	va_end(arglist);
#endif
	return 0;
}

int __android_log_write(int prio, const char *tag, const char *fmt, ...) {
#if DEBUG
	va_list arglist;
	va_start(arglist, fmt);
	fprintf(stderr, "%s ", tag);
	vfprintf(stderr, fmt, arglist);
	fprintf(stderr, "\n");
	fflush(stderr);
	va_end(arglist);
#endif
	return 0;
}
#endif

//Stops the vorbis data feed
void stopDecodeFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* stopMethodId) {
    (*env)->CallVoidMethod(env, (*vorbisDataFeed), (*stopMethodId));
}

//Reads raw vorbis data from the jni callback
int readVorbisDataFromVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* readVorbisDataMethodId, char* buffer, jbyteArray* jByteArrayReadBuffer) {
    //Call the read method
    int readByteCount = (*env)->CallIntMethod(env, (*vorbisDataFeed), (*readVorbisDataMethodId), (*jByteArrayReadBuffer), BUFFER_LENGTH);
    
    //Don't bother copying, just return 0
    if(readByteCount == 0) {
        return 0;
    }

    //Gets the bytes from the java array and copies them to the vorbis buffer
    jbyte* readBytes = (*env)->GetByteArrayElements(env, (*jByteArrayReadBuffer), NULL);
    memcpy(buffer, readBytes, readByteCount);
    
    //Clean up memory and return how much data was read
    (*env)->ReleaseByteArrayElements(env, (*jByteArrayReadBuffer), readBytes, JNI_ABORT);

    //Return the amount actually read
    return readByteCount;
}

//Callback on the elapsed seconds
int elapsedSecondVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* elapsedMethodId, long elapsed_seconds) {
	//Call the callback method
	(*env)->CallVoidMethod(env, (*vorbisDataFeed), (*elapsedMethodId), (jlong) elapsed_seconds);

	return elapsed_seconds;
}

//Writes the pcm data to the Java layer
jboolean writePCMDataFromVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* writePCMDataMethodId, ogg_int16_t* buffer, int bytes, jshortArray* jShortArrayWriteBuffer) {
    
    //No data to read, just exit
    if(bytes == 0) {
        return JNI_TRUE;
    }

    //Copy the contents of what we're writing to the java short array
    (*env)->SetShortArrayRegion(env, (*jShortArrayWriteBuffer), 0, bytes, (jshort *)buffer);
    
    //Call the write pcm data method
    jboolean success = (*env)->CallBooleanMethod(env, (*vorbisDataFeed), (*writePCMDataMethodId), (*jShortArrayWriteBuffer), bytes);

    return success;
}

//Writes the pcm data to the Java layer
void writePCMDataBytesFromVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* writePCMDataMethodId, char* buffer, int bytes, jbyteArray* jByteArrayWriteBuffer) {

    //No data to read, just exit
    if(bytes == 0) {
        return;
    }

    //Copy the contents of what we're writing to the java short array
    //(*env)->SetShortArrayRegion(env, (*jShortArrayWriteBuffer), 0, bytes, (jshort *)buffer);
    (*env)->SetByteArrayRegion(env, (*jByteArrayWriteBuffer), 0, bytes, (jbyte *)(buffer));

    //Call the write pcm data method
    (*env)->CallVoidMethod(env, (*vorbisDataFeed), (*writePCMDataMethodId), (*jByteArrayWriteBuffer), bytes);
}

//Starts the decode feed with the necessary information about sample rates, channels, etc about the stream
void start(JNIEnv *env, jobject *vorbisDataFeed, jmethodID* startMethodId, long sampleRate, long channels, char* vendor, long playtime) {
    __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Notifying decode feed");

    //Creates a java string for the vendor
    jstring vendorString = (*env)->NewStringUTF(env, vendor);

    //Get decode stream info class and constructor
    jclass decodeStreamInfoClass = (*env)->FindClass(env, "org/xiph/vorbis/decoder/DecodeStreamInfo");
    jmethodID constructor = (*env)->GetMethodID(env, decodeStreamInfoClass, "<init>", "(JJLjava/lang/String;J)V");

    //Create the decode stream info object
    jobject decodeStreamInfo = (*env)->NewObject(env, decodeStreamInfoClass, constructor, (jlong)sampleRate, (jlong)channels, vendorString, (jlong)playtime);

    //Call decode feed start
    (*env)->CallVoidMethod(env, (*vorbisDataFeed), (*startMethodId), decodeStreamInfo);

    //Cleanup decode feed object
    (*env)->DeleteLocalRef(env, decodeStreamInfo);

    //Cleanup java vendor string
    (*env)->DeleteLocalRef(env, vendorString);
}

//Starts reading the header information
void startReadingHeader(JNIEnv *env, jobject *vorbisDataFeed, jmethodID* startReadingHeaderMethodId) {
    __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Notifying decode feed to start reading the header");

    //Call header start reading method
    (*env)->CallVoidMethod(env, (*vorbisDataFeed), (*startReadingHeaderMethodId));
}


char *fullFilePath(JNIEnv *env, jobject* file, jmethodID* getFilePathMethodId) {
	//Call the callback method
	jstring returnPathString = (jstring) (*env)->CallObjectMethod(env, (*file), (*getFilePathMethodId));
	char* file_path = (*env)->GetStringUTFChars(env, returnPathString, NULL);
	(*env)->DeleteLocalRef(env, returnPathString);
	return file_path;
}

JNIEXPORT jobject JNICALL Java_org_xiph_vorbis_decoder_VorbisDecoder_decodeFileMetadata
  (JNIEnv *env, jclass cls, jobject vorbisFile){

	jclass javaFileClass = (*env)->FindClass(env, "java/io/File");
	jmethodID getAbsolutePathMethodId = (*env)->GetMethodID(env, javaFileClass, "getAbsolutePath", "()Ljava/lang/String;");
	char* fullPath = fullFilePath(env, &vorbisFile, &getAbsolutePathMethodId);
	__android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Decoding vorbis file %s for meta data", fullPath);

	OggVorbis_File ovf;

	int result = ov_fopen(fullPath, &ovf);

	if (result != 0){
		__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "failed open file %s for reading", fullPath);
		return NULL;
	}

	__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "getting vorbis_comment");
	vorbis_comment *vc = ov_comment(&ovf, -1);
	char **ptr = vc->user_comments;
	__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "getting vorbis_info");
	vorbis_info *vi = ov_info(&ovf, -1);

	long playtime = -1;
	if(ov_seekable(&ovf))
	{
		playtime = (long)ov_time_total(&ovf, -1);
		__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "seek playtime is %d seconds", playtime);
	}
	else
	{
		__android_log_print(ANDROID_LOG_WARN, "VorbisDecoder", "vorbis file %s is not seekable, unable to obtain full play time", fullPath);
	}

	//Creates a java string for the vendor
	jstring vendorString = (*env)->NewStringUTF(env, vc->vendor);

	//Get decode stream info class and constructor
	jclass decodeStreamInfoClass = (*env)->FindClass(env, "org/xiph/vorbis/decoder/DecodeStreamInfo");
	jmethodID constructor = (*env)->GetMethodID(env, decodeStreamInfoClass, "<init>", "(JJLjava/lang/String;J)V");

	__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "creating DecodeStreamInfo for return");
	//Create the decode stream info object
	jobject decodeStreamInfo = (*env)->NewObject(env, decodeStreamInfoClass, constructor, (jlong)vi->rate, (jlong)vi->channels, vendorString, (jlong)playtime);

	//Not needed, returning object
	//(*env)->DeleteLocalRef(env, decodeStreamInfo);

	//Not needded, returning object
	//(*env)->DeleteLocalRef(env, vendorString);

	__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "closing vorbis file handle");

	ov_clear(&ovf);

	//fclose(vorbis_file);

	return decodeStreamInfo;
}

JNIEXPORT jint JNICALL Java_org_xiph_vorbis_decoder_VorbisDecoder_startDecoding
  (JNIEnv *env, jclass cls, jobject vorbisDataFeed) {

    //Create a new java byte array to pass to the vorbis data feed method
    jbyteArray jByteArrayReadBuffer = (*env)->NewByteArray(env, BUFFER_LENGTH);

    //Create our write buffer
    jshortArray jShortArrayWriteBuffer = (*env)->NewShortArray(env, BUFFER_LENGTH*2);

    //Find our java classes we'll be calling
    jclass vorbisDataFeedClass = (*env)->FindClass(env, "org/xiph/vorbis/decoder/DecodeFeed");

    //Find our java method id's we'll be calling
    jmethodID readVorbisDataMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "readVorbisData", "([BI)I");
    jmethodID writePCMDataMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "writePCMData", "([SI)Z");
    jmethodID startMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "start", "(Lorg/xiph/vorbis/decoder/DecodeStreamInfo;)V");
    jmethodID startReadingHeaderMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "startReadingHeader", "()V");
    jmethodID stopMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "stop", "()V");
    jmethodID elapsedMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "elapsedSeconds", "(J)V");

    ogg_int16_t convbuffer[BUFFER_LENGTH]; /* take 8k out of the data segment, not the stack */
    int convsize=BUFFER_LENGTH;
    
    ogg_sync_state   oy; /* sync and verify incoming physical bitstream */
    ogg_stream_state os; /* take physical pages, weld into a logical stream of packets */
    ogg_page         og; /* one Ogg bitstream page. Vorbis packets are inside */
    ogg_packet       op; /* one raw packet of data for decode */
    
    vorbis_info      vi; /* struct that stores all the static vorbis bitstream settings */
    vorbis_comment   vc; /* struct that stores all the bitstream user comments */
    vorbis_dsp_state vd; /* central working state for the packet->PCM decoder */
    vorbis_block     vb; /* local working space for packet->PCM decode */
    
    char *buffer;
    int  bytes;
    
    /********** Decode setup ************/

    //Notify the decode feed we are starting to initialize
    startReadingHeader(env, &vorbisDataFeed, &startReadingHeaderMethodId);
    
    ogg_sync_init(&oy); /* Now we can read pages */
    
    while(1){
        /* we repeat if the bitstream is chained */
        int eos=0;
        int count_samples = 0;
        long elapsed_seconds = 0;
        int i;
        
        /* grab some data at the head of the stream. We want the first page
        (which is guaranteed to be small and only contain the Vorbis
        stream initial header) We need the first page to get the stream
        serialno. */
        
        /* submit a 4k block to libvorbis' Ogg layer */
        __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Submitting 4k block to libvorbis' Ogg layer");
        buffer=ogg_sync_buffer(&oy,BUFFER_LENGTH);
        bytes=readVorbisDataFromVorbisDataFeed(env, &vorbisDataFeed, &readVorbisDataMethodId, buffer, &jByteArrayReadBuffer);
        ogg_sync_wrote(&oy,bytes);
        
        /* Get the first page. */
        __android_log_print(ANDROID_LOG_DEBUG, "VorbisDecoder", "Getting the first page, read (%d) bytes", bytes);
        if(ogg_sync_pageout(&oy,&og)!=1){
            /* have we simply run out of data?  If so, we're done. */
            if(bytes<BUFFER_LENGTH)break;
            
            /* error case.  Must not be Vorbis data */
            stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
            return INVALID_OGG_BITSTREAM;
        }

        __android_log_write(ANDROID_LOG_INFO, "VorbisDecoder", "Successfully fetched the first page");

        /* Get the serial number and set up the rest of decode. */
        /* serialno first; use it to set up a logical stream */
        ogg_stream_init(&os,ogg_page_serialno(&og));

        /* extract the initial header from the first page and verify that the
        Ogg bitstream is in fact Vorbis data */

        /* I handle the initial header first instead of just having the code
        read all three Vorbis headers at once because reading the initial
        header is an easy way to identify a Vorbis bitstream and it's
        useful to see that functionality seperated out. */

        vorbis_info_init(&vi);
        vorbis_comment_init(&vc);
        if(ogg_stream_pagein(&os,&og)<0){
            /* error; stream version mismatch perhaps */
            stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
            return ERROR_READING_FIRST_PAGE;
        }


        if(ogg_stream_packetout(&os,&op)!=1){
            /* no page? must not be vorbis */
            stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
            return ERROR_READING_INITIAL_HEADER_PACKET;
        }


        if(vorbis_synthesis_headerin(&vi,&vc,&op)<0){
            /* error case; not a vorbis header */
            stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
            return NOT_VORBIS_HEADER;
        }


        /* At this point, we're sure we're Vorbis. We've set up the logical
        (Ogg) bitstream decoder. Get the comment and codebook headers and
        set up the Vorbis decoder */

        /* The next two packets in order are the comment and codebook headers.
        They're likely large and may span multiple pages. Thus we read
        and submit data until we get our two packets, watching that no
        pages are missing. If a page is missing, error out; losing a
        header page is the only place where missing data is fatal. */

        i=0;
        while(i<2){
            while(i<2){
                int result=ogg_sync_pageout(&oy,&og);
                if(result==0)break; /* Need more data */
                /* Don't complain about missing or corrupt data yet. We'll
                catch it at the packet output phase */
                if(result==1){
                    ogg_stream_pagein(&os,&og); /* we can ignore any errors here
                    as they'll also become apparent
                    at packetout */
                    while(i<2){
                        result=ogg_stream_packetout(&os,&op);
                        if(result==0)break;
                        if(result<0){
                            /* Uh oh; data at some point was corrupted or missing!
                            We can't tolerate that in a header.  Die. */
                            stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
                            return CORRUPT_SECONDARY_HEADER;
                        }
                        result=vorbis_synthesis_headerin(&vi,&vc,&op);
                        if(result<0){
                            stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
                            return CORRUPT_SECONDARY_HEADER;
                        }
                        i++;
                    }
                }
            }
            /* no harm in not checking before adding more */
            buffer=ogg_sync_buffer(&oy,BUFFER_LENGTH);
            bytes=readVorbisDataFromVorbisDataFeed(env, &vorbisDataFeed, &readVorbisDataMethodId, buffer, &jByteArrayReadBuffer);
            if(bytes==0 && i<2){
                stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
                return PREMATURE_END_OF_FILE;
            }
            ogg_sync_wrote(&oy,bytes);
        }


        /* Throw the comments plus a few lines about the bitstream we're
        decoding */
        {
            char **ptr=vc.user_comments;
            while(*ptr){
                ++ptr;
            }

            __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Bitstream is %d channel",vi.channels);
            __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Bitstream %d Hz",vi.rate);
            __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Encoded by: %s\n\n",vc.vendor);

            start(env, &vorbisDataFeed, &startMethodId, vi.rate, vi.channels, vc.vendor, -1);
        }

        convsize=BUFFER_LENGTH/vi.channels;

        /* OK, got and parsed all three headers. Initialize the Vorbis
        packet->PCM decoder. */
        if(vorbis_synthesis_init(&vd,&vi)==0){
            /* central decode state */
            vorbis_block_init(&vd,&vb);          /* local state for most of the decode
            so multiple block decodes can
            proceed in parallel. We could init
            multiple vorbis_block structures
            for vd here */

            /* The rest is just a straight decode loop until end of stream */
            while(!eos){
                while(!eos){
                    int result=ogg_sync_pageout(&oy,&og);
                    if(result==0)break; /* need more data */
                    if(result<0){
                        /* missing or corrupt data at this page position */
                        __android_log_write(ANDROID_LOG_WARN, "VorbisDecoder", "Corrupt or missing data in bitstream; continuing...");
                    }
                    else{
                        ogg_stream_pagein(&os,&og); /* can safely ignore errors at
                        this point */
                        while(1){
                            result=ogg_stream_packetout(&os,&op);

                            if(result==0)break; /* need more data */
                            if(result<0){
                                /* missing or corrupt data at this page position */
                                /* no reason to complain; already complained above */
                            }
                            else{

                                /* we have a packet.  Decode it */
                                float **pcm;
                                int samples;

                                if(vorbis_synthesis(&vb,&op)==0) /* test for success! */
                                vorbis_synthesis_blockin(&vd,&vb);
                                /*

                                **pcm is a multichannel float vector.  In stereo, for
                                example, pcm[0] is left, and pcm[1] is right.  samples is
                                the size of each channel.  Convert the float values
                                (-1.<=range<=1.) to whatever PCM format and write it out */

                                while((samples=vorbis_synthesis_pcmout(&vd,&pcm))>0){
                                	count_samples += samples;

                                	if (count_samples > vi.rate){
                                		int seconds = count_samples / vi.rate;
                                		count_samples = count_samples % vi.rate;
                                		elapsed_seconds += seconds;
                                		elapsedSecondVorbisDataFeed(env, &vorbisDataFeed, &elapsedMethodId, elapsed_seconds);
                                		__android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "elapsed %d seconds", elapsed_seconds);
                                	}

                                    int j;
                                    int clipflag=0;
                                    int bout=(samples<convsize?samples:convsize);

                                    /* convert floats to 16 bit signed ints (host order) and
                                    interleave */
                                    for(i=0;i<vi.channels;i++){
                                        ogg_int16_t *ptr=convbuffer+i;
                                        float  *mono=pcm[i];
                                        for(j=0;j<bout;j++){

                                            #if 1
                                            int val=floor(mono[j]*32767.f+.5f);
                                            #else /* optional dither */
                                            int val=mono[j]*32767.f+drand48()-0.5f;
                                            #endif
                                            /* might as well guard against clipping */
                                            if(val>32767){
                                                val=32767;
                                                clipflag=1;
                                            }

                                            if(val<-32768){
                                                val=-32768;
                                                clipflag=1;
                                            }

                                            *ptr=val;
                                            ptr+=vi.channels;
                                        }
                                    }

                                    if(clipflag) {
                                        __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Clipping in frame %ld\n",(long)(vd.sequence));
                                    }

                                    if (!writePCMDataFromVorbisDataFeed(env, &vorbisDataFeed, &writePCMDataMethodId, &convbuffer[0], bout*vi.channels, &jShortArrayWriteBuffer)){
                                    	eos = 1;
                                    }

                                    vorbis_synthesis_read(&vd,bout); /* tell libvorbis how many samples we actually consumed */
                                }
                            }
                        }
                        if(ogg_page_eos(&og))eos=1;
                    }
                }

                if(!eos){
                    buffer=ogg_sync_buffer(&oy,BUFFER_LENGTH);
                    bytes=readVorbisDataFromVorbisDataFeed(env, &vorbisDataFeed, &readVorbisDataMethodId, buffer, &jByteArrayReadBuffer);
                    ogg_sync_wrote(&oy,bytes);
                    if(bytes==0) {
                        eos=1;
                    }
                }
            }

            /* ogg_page and ogg_packet structs always point to storage in
            libvorbis.  They're never freed or manipulated directly */
            vorbis_block_clear(&vb);
            vorbis_dsp_clear(&vd);

        }
        else{
            __android_log_print(ANDROID_LOG_WARN, "VorbisDecoder", "Error: Corrupt header during playback initialization.");
        }

        /* clean up this logical bitstream; before exit we see if we're
        followed by another [chained] */

        ogg_stream_clear(&os);
        vorbis_comment_clear(&vc);
        vorbis_info_clear(&vi);  /* must be called last */
    }

    /* OK, clean up the framer */
    ogg_sync_clear(&oy);



    stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);

    //Clean up our buffers
    (*env)->DeleteLocalRef(env, jByteArrayReadBuffer);
    (*env)->DeleteLocalRef(env, jShortArrayWriteBuffer);

    return SUCCESS;
}

//Callback on the elapsed seconds
long seekSecondVorbisDataFeed(JNIEnv *env, jobject* vorbisDataFeed, jmethodID* seekMethodId) {
	//Call the callback method
	long seek_seconds = (long)(*env)->CallLongMethod(env, (*vorbisDataFeed), (*seekMethodId));
	return seek_seconds;
}

JNIEXPORT jint JNICALL Java_org_xiph_vorbis_decoder_VorbisDecoder_startDecodingFile(JNIEnv *env, jclass cls, jobject file, jobject vorbisDataFeed){

    //Find our java classes we'll be calling
    jclass vorbisDataFeedClass = (*env)->FindClass(env, "org/xiph/vorbis/decoder/DecodeFeed");

    //Find our java method id's we'll be calling
    jmethodID writePCMDataMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "writePCMData", "([SI)Z");
    jmethodID startMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "start", "(Lorg/xiph/vorbis/decoder/DecodeStreamInfo;)V");
    jmethodID startReadingHeaderMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "startReadingHeader", "()V");
    jmethodID stopMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "stop", "()V");
    jmethodID elapsedMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "elapsedSeconds", "(J)V");
    jmethodID seekMethodId = (*env)->GetMethodID(env, vorbisDataFeedClass, "seekToSeconds", "()J");


    jclass javaFileClass = (*env)->FindClass(env, "java/io/File");

    jmethodID getFilePathMethodId = (*env)->GetMethodID(env, javaFileClass, "getAbsolutePath", "()Ljava/lang/String;");

    char *file_path = fullFilePath(env, &file, &getFilePathMethodId);

    __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "decoding playback of file at path %s", file_path);

    //Create our write buffer
    jshortArray jShortArrayWriteBuffer = (*env)->NewShortArray(env, BUFFER_LENGTH*2);

    OggVorbis_File vf;
    int eof = 0;
    int current_section;

	if(ov_fopen(file_path, &vf) < 0) {
		__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "input does not appear to be an Ogg bitstream");
		stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
		ov_clear(&vf);
		return NOT_VORBIS_HEADER;
	}

	startReadingHeader(env, &vorbisDataFeed, &startReadingHeaderMethodId);
	vorbis_info *vi;
	{
		char **ptr=ov_comment(&vf,-1)->user_comments;
		vi = ov_info(&vf,-1);
		while(*ptr){
			fprintf(stderr,"%s\n",*ptr);
			++ptr;
		}

		char * vendor = ov_comment(&vf,-1)->vendor;
		__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "Bitstream is %d channel, %ldHz\n", vi->channels, vi->rate);
		__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "Decoded length: %ld samples\n", (long)ov_pcm_total(&vf,-1));
		__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "Encoded by: %s\n\n", vendor);

		long playtime = -1;
		if(ov_seekable(&vf))
		{
			playtime = (long)ov_time_total(&vf, -1);
			__android_log_print(ANDROID_LOG_ERROR, "VorbisDecoder", "seek playtime is %d seconds", playtime);
		}
		else
		{
			__android_log_print(ANDROID_LOG_WARN, "VorbisDecoder", "vorbis file %s is not seekable, unable to obtain full play time", file_path);
		}

		start(env, &vorbisDataFeed, &startMethodId, vi->rate, vi->channels, vendor, playtime);
	}

	float **pcm;
    ogg_int16_t convbuffer[BUFFER_LENGTH]; /* take 8k out of the data segment, not the stack */
    int convsize=BUFFER_LENGTH;
    long elapsed_seconds = 0;
    long last_seek_seconds = -1;

	int i;
	int j;
	long read_length = 0;
	while(!eof){
		read_length = ov_read_float(&vf, &pcm, BUFFER_LENGTH, &current_section);
		if (read_length == 0) {
			/* EOF */
			eof=1;
		} else if (read_length < 0) {
			/* error in the stream.  Not a problem, just reporting it in
			case we (the app) cares.  In this case, we don't. */
		} else {
			int clipflag=0;
			int bout=(read_length<convsize?read_length:convsize);

			/* convert floats to 16 bit signed ints (host order) and
			interleave */
			for(i=0;i<vi->channels;i++){
				ogg_int16_t *ptr=convbuffer+i;
				float  *mono=pcm[i];
				for(j=0;j<bout;j++){

					#if 1
					int val=floor(mono[j]*32767.f+.5f);
					#else /* optional dither */
					int val=mono[j]*32767.f+drand48()-0.5f;
					#endif
					/* might as well guard against clipping */
					if(val>32767){
						val=32767;
						clipflag=1;
					}

					if(val<-32768){
						val=-32768;
						clipflag=1;
					}

					*ptr=val;
					ptr+=vi->channels;
				}
			}

            if(clipflag) {
                __android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "Clipping in stream\n");
            }
            long current_seconds = ov_time_tell(&vf);
            if (current_seconds != elapsed_seconds){
            	elapsed_seconds = current_seconds;
        		elapsedSecondVorbisDataFeed(env, &vorbisDataFeed, &elapsedMethodId, elapsed_seconds);
        		__android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "elapsed %d seconds", elapsed_seconds);
            }

			/* we don't bother dealing with sample rate changes, etc, but
			you'll have to*/
			if (!writePCMDataFromVorbisDataFeed(env, &vorbisDataFeed, &writePCMDataMethodId, &convbuffer[0], bout*vi->channels, &jShortArrayWriteBuffer)){
				eof = 1;
			}

			long current_seek = seekSecondVorbisDataFeed(env, &vorbisDataFeed, &seekMethodId);
			if (current_seek != last_seek_seconds){
				last_seek_seconds = current_seek;
				if (last_seek_seconds >= 0){
					ov_time_seek(&vf, last_seek_seconds);
					__android_log_print(ANDROID_LOG_INFO, "VorbisDecoder", "detected seek request to %d seconds", last_seek_seconds);
				}
			}
		}
	}

	stopDecodeFeed(env, &vorbisDataFeed, &stopMethodId);
	ov_clear(&vf);

    (*env)->DeleteLocalRef(env, jShortArrayWriteBuffer);

	return SUCCESS;
}

