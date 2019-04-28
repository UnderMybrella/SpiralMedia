package info.spiralframework.media

import info.spiralframework.base.config.SpiralConfig
import info.spiralframework.console.Cockpit
import info.spiralframework.console.commands.shared.GurrenShared
import info.spiralframework.core.formats.audio.AudioFormats
import info.spiralframework.core.plugins.BaseSpiralPlugin
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object HumbleMediaPlugin: BaseSpiralPlugin(HumbleMediaPlugin::class, "spiralframework_media_plugin.yaml") {
    val LIST_OF_ARCHIVES_FFMPEG = arrayOf("ffmpeg.exe", "ffmpeg")
    val LIST_OF_ARCHIVES_FFPROBE = arrayOf("ffprobe.exe", "ffprobe")

    val extractDir: File
    val ffmpegFile: File
    val ffprobeFile: File

    val ffmpeg: FFmpeg
    val ffprobe: FFprobe
    val ffmpegExecutor: FFmpegExecutor

    @JvmStatic
    fun main(args: Array<String>) {
        load()

        val cockpit = Cockpit.invoke(args)
        cockpit.start()
    }

    override fun load() {
        println("Loading")

        removeAudioFormats()
        AudioFormats.wav = HumbleAudioFormat("wav")
        AudioFormats.ogg = HumbleAudioFormat("ogg")
        AudioFormats.mp3 = HumbleAudioFormat("mp3")
        addAudioFormats()
    }

    override fun unload() {
        println("Unloading!")

        removeAudioFormats()
        AudioFormats.wav = AudioFormats.DEFAULT_WAV
        AudioFormats.ogg = AudioFormats.DEFAULT_OGG
        AudioFormats.mp3 = AudioFormats.DEFAULT_MP3
        addAudioFormats()
    }

    fun removeAudioFormats() {
        GurrenShared.READABLE_FORMATS.remove(AudioFormats.wav)
        GurrenShared.READABLE_FORMATS.remove(AudioFormats.ogg)
        GurrenShared.READABLE_FORMATS.remove(AudioFormats.mp3)

        GurrenShared.WRITABLE_FORMATS.remove(AudioFormats.wav)
        GurrenShared.WRITABLE_FORMATS.remove(AudioFormats.ogg)
        GurrenShared.WRITABLE_FORMATS.remove(AudioFormats.mp3)
    }

    fun addAudioFormats() {
        GurrenShared.READABLE_FORMATS.add(AudioFormats.wav)
        GurrenShared.READABLE_FORMATS.add(AudioFormats.ogg)
        GurrenShared.READABLE_FORMATS.add(AudioFormats.mp3)

        GurrenShared.WRITABLE_FORMATS.add(AudioFormats.wav)
        GurrenShared.WRITABLE_FORMATS.add(AudioFormats.ogg)
        GurrenShared.WRITABLE_FORMATS.add(AudioFormats.mp3)
    }

    fun checkAndFindProgram(program: String): String? {
        try {
            val process = ProcessBuilder(program).start()
            process.waitFor()

            val output = String(process.inputStream.use(InputStream::readBytes))
            if (output.startsWith("$program version")) {
                //Alright, we're installed

                val osName = System.getenv("os.name").toLowerCase()

                if (osName.startsWith("win")) {
                    val whereProcess = ProcessBuilder("where", program).start()
                    whereProcess.waitFor()

                    return String(whereProcess.inputStream.use(InputStream::readBytes))
                } else if (osName.contains("mac") || osName.contains("nix") || osName.contains("nux")) {
                    val whichProcess = ProcessBuilder("which", program).start()
                    whichProcess.waitFor()

                    return String(whichProcess.inputStream.use(InputStream::readBytes))
                }
            }

            return null
        } catch (io: IOException) {
            //We're definitely not installed
            return null
        }
    }

    init {
        //First, check to see if we've already extracted the files
        extractDir = File(SpiralConfig.projectDirectories.dataLocalDir, "spiral-media-binaries")
        extractDir.mkdirs()

        val extractedFFmpeg = LIST_OF_ARCHIVES_FFMPEG.map { str -> File(extractDir, str) }.firstOrNull(File::exists)

        if (extractedFFmpeg == null) {
            //Check if FFmpeg is installed

            val ffmpegPath = checkAndFindProgram("ffmpeg")
            
            if (ffmpegPath == null) {
                //Extract
                
                val ffmpegName = LIST_OF_ARCHIVES_FFMPEG.firstOrNull { str -> HumbleMediaPlugin::class.java.classLoader.getResourceAsStream(str)?.close() == Unit } ?: error("No FFmpeg executable found!")
                ffmpegFile = File(extractDir, ffmpegName)
                
                FileOutputStream(ffmpegFile).use { out ->
                    HumbleMediaPlugin::class.java.classLoader.getResourceAsStream(ffmpegName).use { inStream ->
                        inStream.copyTo(out)
                    }
                }
            } else {
                ffmpegFile = File(ffmpegPath)
            }
        } else {
            ffmpegFile = extractedFFmpeg
        }
        
        val extractedFFprobe = LIST_OF_ARCHIVES_FFPROBE.map { str -> File(extractDir, str) }.firstOrNull(File::exists)
        if (extractedFFprobe == null) {
            //Check if FFprobe is installed

            val ffprobePath = checkAndFindProgram("ffprobe")

            if (ffprobePath == null) {
                //Extract

                val ffprobeName = LIST_OF_ARCHIVES_FFPROBE.firstOrNull { str -> HumbleMediaPlugin::class.java.classLoader.getResourceAsStream(str)?.close() == Unit } ?: error("No Ffprobe executable found!")
                ffprobeFile = File(extractDir, ffprobeName)

                FileOutputStream(ffprobeFile).use { out ->
                    HumbleMediaPlugin::class.java.classLoader.getResourceAsStream(ffprobeName).use { inStream ->
                        inStream.copyTo(out)
                    }
                }
            } else {
                ffprobeFile = File(ffprobePath)
            }
        } else {
            ffprobeFile = extractedFFprobe
        }

        ffmpeg = FFmpeg(ffmpegFile.absolutePath)
        ffprobe = FFprobe(ffprobeFile.absolutePath)
        ffmpegExecutor = FFmpegExecutor(ffmpeg, ffprobe)
    }
}