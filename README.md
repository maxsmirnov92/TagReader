# TagReader
Util to read some tag fields from MP3 + writing it to output text file in QTScrobbler format

usage:
java -jar tagreader.jar -rootDir d:\\Temp\\1 -isRecursive true -targetTextDir d:\\Temp\\1 -targetTextFile .scrobbler.log -append true -header "#AUDIOSCROBBLER/1.1\n#TZ/UNKNOWN\n#CLIENT/Rockbox samsungypr0 $Revision$"