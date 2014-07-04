project designed to build in eclipse with C for development under windows

download and install eclipse windows for C development

download opensource c compiler at http://sourceforge.net/projects/mingw-w64/

unzip to any directory and ensure bin directory with command such as gcc 
is on the system path

download opensource make program at http://www.mingw.org/wiki/MSYS
MSYS only comes in 32bit but will work fine in windows 64bit.

if installing using a mingw installer DO NOT install the mingw32 as that is only for 32 bit
machine, only select the MSYS packages.

lib_vorbis_jni requires header files from jdk installation

goto the project properties and add the include directory of jdk
Project Properties -> C/C++ General -> Paths and Symbols -> includes tab

add following 

C:\Program Files\Java\jdk1.7.0_51\include
C:\Program Files\Java\jdk1.7.0_51\include\win32