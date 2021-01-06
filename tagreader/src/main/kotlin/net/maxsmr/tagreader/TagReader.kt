package net.maxsmr.tagreader

import com.mpatric.mp3agic.Mp3File
import net.maxsmr.commonutils.data.*
import net.maxsmr.commonutils.logger.BaseLogger
import net.maxsmr.commonutils.logger.SimpleSystemLogger
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder
import java.io.File
import java.util.concurrent.TimeUnit

private const val EXT_MP3 = "mp3"
private const val PATTERN_TAG_FORMAT = "%s\t%s\t%s\t%d\t%d\tL\t%d\t"

fun main(args: Array<String>) {

    BaseLoggerHolder.initInstance {
        object : BaseLoggerHolder(false) {
            override fun createLogger(className: String): BaseLogger {
                return SimpleSystemLogger(className)
            }
        }
    }

    val argsNames = arrayOf("-rootDir", "-isRecursive", "-targetTextDir", "-targetTextFile", "-append", "-header")

    val argsParser = ArgsParser(*argsNames)
    argsParser.setArgs(*args)

    val rootDir = argsParser.findPairArg(0, true)?.let {
        File(it)
    }

    val isRecursive = argsParser.findPairArg(1, true)?.toBoolean() ?: false

    val append = argsParser.findPairArg(4, true)?.toBoolean() ?: false
    val targetTextFile = createFileOrThrow(argsParser.findPairArg(3, true), argsParser.findPairArg(2, true), !append)

    val header = argsParser.findPairArg(5, true)

    if (!isDirExistsOrThrow(rootDir)) {
        throw RuntimeException("Dir '$rootDir' not exists")
    }

    if (!isFileValidOrThrow(targetTextFile) && header != null) {
        writeStringsToFileOrThrow(targetTextFile, listOf(header.replace("\\n", System.lineSeparator())), false)
    }

    val files = getFiles(
            rootDir,
            GetMode.FILES,
            depth = if (isRecursive) DEPTH_UNLIMITED else 1
    ).filter {
        EXT_MP3.equals(getFileExtension(it), true)
    }

    val currentTimestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

    files.forEach {
        val mp3File = Mp3File(it)

        val duration = mp3File.lengthInSeconds
        var artist: String? = null
        var album: String? = null
        var title: String? = null
        var mergedTrackNumber: String? = null
        var trackNumber: Int? = null

        if (mp3File.hasId3v2Tag()) {
            artist = mp3File.id3v2Tag.artist
            album = mp3File.id3v2Tag.album
            title = mp3File.id3v2Tag.title
            mergedTrackNumber = mp3File.id3v2Tag.track
        } else if (mp3File.hasId3v1Tag()) {
            artist = mp3File.id3v1Tag.artist
            album = mp3File.id3v1Tag.album
            title = mp3File.id3v1Tag.title
            mergedTrackNumber = mp3File.id3v1Tag.track
        }

        mergedTrackNumber?.let {
            val parts = mergedTrackNumber.split("/")
            if (parts.isNotEmpty()) {
                trackNumber = parts[0].toIntOrNull()
            }
        }

        val scrobblerString = PATTERN_TAG_FORMAT.format(artist, album, title, trackNumber, duration, currentTimestamp)
        writeStringsToFileOrThrow(targetTextFile, listOf(scrobblerString), append)
    }
}