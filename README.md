ogg-vorbis-jni
==============

this is just a modification of android libogg vorbis from https://github.com/vincentjames501/libvorbis-libogg-android 
but made to work on windows.

the only difference it is using the original latest source code downloaded from http://xiph.org/ogg/ which seems to 
produce smaller dll files on windows.

it also has few modification to allow random seek on file based playback and callbacks for elapsed time. should not be
too hard to backport to android if need be, but I probably wont be doing it.

the original c code is compiled with mingw64 and msys on windows for the 64 bit dll and mingw32 for the 32bit dll under
eclipse CDT ide.

if compilation is required then download mingw at http://www.mingw.org/ and mingw64 http://mingw-w64.sourceforge.net/
or you can just use https://github.com/ekfliu/ogg-vorbis-jni/blob/master/LibOggVorbisJni/LibOggVorbisJni-0.0.8-SNAPSHOT.jar
directly.

see example on the original libvorbis for android
or
see test example at
https://github.com/ekfliu/ogg-vorbis-jni/tree/master/LibOggVorbisJni/src/test/java/org/xiph/vorbis/playback
