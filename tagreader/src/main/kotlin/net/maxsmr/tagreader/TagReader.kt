package net.maxsmr.tagreader

import com.mpatric.mp3agic.Mp3File
import net.maxsmr.commonutils.data.*
import net.maxsmr.commonutils.data.text.EMPTY_STRING
import net.maxsmr.commonutils.data.text.TrimDirection
import net.maxsmr.commonutils.data.text.trimWithCondition
import net.maxsmr.commonutils.logger.BaseLogger
import net.maxsmr.commonutils.logger.SimpleSystemLogger
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder
import java.io.File
import java.util.concurrent.TimeUnit

private const val EXT_MP3 = "mp3"
private const val PATTERN_TAG_FORMAT = "%s\t%s\t%s\t%s\t%d\tL\t%d\t"

private val argsNames = arrayOf("-rootDir", "-recursive", "-targetDir", "-targetFile", "-append", "-header", "-log")

fun main(args: Array<String>) {

    fun CharSequence?.trimEndSeparator() = this?.let {
        trimWithCondition(
                it,
                TrimDirection.END,
                File.separatorChar,
                CompareCondition.EQUAL
        ).toString()
    } ?: EMPTY_STRING

    val argsParser = ArgsParser(*argsNames)
    argsParser.setArgs(*args)

    val isLoggingEnabled = argsParser.containsArg(6, true)

    BaseLoggerHolder.initInstance {
        object : BaseLoggerHolder(true) {
            override fun createLogger(className: String): BaseLogger? {
                return if (isLoggingEnabled) {
                    SimpleSystemLogger(className)
                } else {
                    null
                }
            }
        }
    }

    val logger = BaseLoggerHolder.getInstance().getLogger<BaseLogger>("TagReader")


    val rootDir = argsParser.findPairArg(0, true)?.let {
        File(it.trimEndSeparator())
    }

    val recursive = argsParser.containsArg(1, true)

    val append = argsParser.containsArg(4, true)
    val targetTextFile = createFileOrThrow(
            argsParser.findPairArg(3, true).trimEndSeparator(),
            argsParser.findPairArg(2, true).trimEndSeparator(),
            !append)

    val header = argsParser.findPairArg(5, true)

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
        EXT_MP3.equals(getFileExtension(it), true)
    }

    val currentTimestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

    files.forEach loop@ {
        logger.i("")
        logger.i("Scanning file '$it'...")

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
                currentTimestamp
        )
        writeStringsToFileOrThrow(targetTextFile, listOf(scrobblerString), true)
    }
}