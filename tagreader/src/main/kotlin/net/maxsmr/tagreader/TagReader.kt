package net.maxsmr.tagreader

import com.mpatric.mp3agic.Mp3File
import net.maxsmr.commonutils.*
import net.maxsmr.commonutils.logger.BaseLogger
import net.maxsmr.commonutils.logger.SimpleSystemLogger
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder
import net.maxsmr.commonutils.text.*

import java.io.File
import java.util.concurrent.TimeUnit

private const val EXT_MP3 = "mp3"
private const val PATTERN_TAG_FORMAT = "%s\t%s\t%s\t%s\t%d\tL\t%d\t"

fun main(args: Array<String>) {

    // trim не пригодится, т.к. при наличии "\" (виндовый сепаратор) на конце какой-либо подстроки (в т.ч. в кавычках)
    // массив args уже будет кривой
    fun CharSequence?.trimEndSeparator() = this?.let {
        trimWithCondition(
                it,
                TrimDirection.END,
                File.separatorChar,
                CompareCondition.EQUAL
        ).toString()
    } ?: EMPTY_STRING

    val argsParser = ArgsParser(args.toList())

    val isLoggingEnabled = argsParser.containsArg("-log")

    BaseLoggerHolder.initInstance {
        object : BaseLoggerHolder() {
            override fun createLogger(className: String): BaseLogger? {
                return if (isLoggingEnabled) {
                    SimpleSystemLogger(className)
                } else {
                    null
                }
            }
        }
    }

    val logger = BaseLoggerHolder.instance.getLogger<BaseLogger>("TagReader")

    logger.d("Main args is: ${argsParser.args}")

    val rootDir = argsParser.findAssociatedArgByName("-rootDir")?.let {
        File(it.trimEndSeparator())
    }

    val recursive = argsParser.containsArg("-recursive")

    val append = argsParser.containsArg("-append")
    val targetTextFile = createFileOrThrow(
            argsParser.findAssociatedArgByName("-targetFile").trimEndSeparator(),
            argsParser.findAssociatedArgByName("-targetDir").trimEndSeparator(),
            !append)

    val header = argsParser.findAssociatedArgByName("-header")

    if (!isDirExistsOrThrow(rootDir)) {
        throw RuntimeException("Dir '$rootDir' not exists")
    }

    if (!isFileValidOrThrow(targetTextFile) && header != null) {
        logger.d("Writing header to '$targetTextFile'...")
        writeStringsToFileOrThrow(targetTextFile, listOf(header.replace("\\n", System.lineSeparator())), false)
    }

    logger.i("Scanning files in '$rootDir'...")
    val files = getFiles(
            rootDir,
            GetMode.FILES,
            depth = if (recursive) DEPTH_UNLIMITED else 1
    ).filter {
        EXT_MP3.equals(it.extension, true)
    }

    var currentTimestampMs = argsParser.findAssociatedArgByName("-initialTimestamp")
            ?.toLongOrNull()?.takeIf { it > 0 }
            ?: System.currentTimeMillis()
    logger.i("Initial timestamp is $currentTimestampMs ms")

    files.forEach loop@{
        logger.i("\nScanning file '$it'...")

        val mp3File = Mp3File(it)

        val duration = mp3File.lengthInSeconds
        val artist: String?
        val album: String?
        val title: String?
        val mergedTrackNumber: String?

        when {
            mp3File.hasId3v2Tag() -> {
                artist = mp3File.id3v2Tag.artist
                album = mp3File.id3v2Tag.album
                title = mp3File.id3v2Tag.title
                mergedTrackNumber = mp3File.id3v2Tag.track
            }
            mp3File.hasId3v1Tag() -> {
                artist = mp3File.id3v1Tag.artist
                album = mp3File.id3v1Tag.album
                title = mp3File.id3v1Tag.title
                mergedTrackNumber = mp3File.id3v1Tag.track
            }
            else -> {
                logger.e("Invalid mp3 file: '$mp3File' (no ID3s)")
                return@loop
            }
        }

        var trackNumber: String? = null

        mergedTrackNumber?.let {
            val parts = mergedTrackNumber.split("/")
            if (parts.isNotEmpty()) {
                trackNumber = parts[0]
            }
        }

        val scrobblerString = PATTERN_TAG_FORMAT.format(
                artist ?: EMPTY_STRING,
                album ?: EMPTY_STRING,
                title ?: EMPTY_STRING,
                trackNumber ?: EMPTY_STRING,
                duration,
                TimeUnit.MILLISECONDS.toSeconds(currentTimestampMs)
        )
        writeStringsToFileOrThrow(targetTextFile, listOf(scrobblerString), true)

        val trackDuration = mp3File.lengthInMilliseconds.takeIf { length -> length > 0 } ?: 1
        logger.i("Track duration: $trackDuration ms")
        currentTimestampMs += trackDuration
    }

    logger.i("Last timestamp is: $currentTimestampMs ms")
}