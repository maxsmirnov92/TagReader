# TagReader
Util to read some tag fields from MP3 + writing it to output text file in QTScrobbler format

usage:
java -jar tagreader.jar -initialTimestamp 0 -rootDir d:\\Temp\\1 -recursive -targetDir d:\\Temp\\1 -targetFile .scrobbler.log -append -header "#AUDIOSCROBBLER/1.1\n#TZ/UNKNOWN\n#CLIENT/Rockbox samsungypr0 $Revision$" -log

build:
gradlew jar