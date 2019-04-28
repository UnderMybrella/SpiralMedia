package info.spiralframework.media

import info.spiralframework.base.util.copyFromStream
import info.spiralframework.base.util.copyToStream
import info.spiralframework.core.formats.FormatResult
import info.spiralframework.core.formats.FormatWriteResponse
import info.spiralframework.core.formats.audio.SpiralAudioFormat
import info.spiralframework.formats.game.DRGame
import info.spiralframework.formats.utils.DataContext
import info.spiralframework.formats.utils.DataHandler
import info.spiralframework.formats.utils.DataSource
import info.spiralframework.formats.utils.use
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.job.FFmpegJob
import java.io.*
import java.util.*

open class HumbleAudioFormat(val format: String) : SpiralAudioFormat(format, format) {
    override val needsMediaPlugin: Boolean = false

    /**
     * Attempts to read the data source as [T]
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     * @param source A function that returns an input stream
     *
     * @return a FormatResult containing either [T] or null, if the stream does not contain the data to form an object of type [T]
     */
    override fun read(name: String?, game: DRGame?, context: DataContext, source: DataSource): FormatResult<File> {
        val tmp = DataHandler.createTmpFile(UUID.randomUUID().toString())
        source.use { stream -> FileOutputStream(tmp).use(stream::copyToStream) }

        val probeResult = HumbleMediaPlugin.ffprobe.probe(tmp.absolutePath)
        if (probeResult.hasError())
            return FormatResult.Fail(this, 1.0) //Extend with the error

        val format = probeResult.format
        if (format.format_name.equals(this.format, true) || format.format_long_name.equals(this.format, true))
            return FormatResult.Success(this, tmp, 1.0)

        return FormatResult.Fail(this, 1.0)
    }

    /**
     * Does this format support writing [data]?
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     *
     * @return If we are able to write [data] as this format
     */
    override fun supportsWriting(data: Any): Boolean = data is File || data is ByteArray || data is InputStream

    /**
     * Writes [data] to [stream] in this format
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     * @param data The data to wrote
     * @param stream The stream to write to
     *
     * @return An enum for the success of the operation
     */
    override fun write(name: String?, game: DRGame?, context: DataContext, data: Any, stream: OutputStream): FormatWriteResponse {
        when (data) {
            is File -> {
                val tmp = DataHandler.createTmpFile(UUID.randomUUID().toString())
                try {
                    val builder = FFmpegBuilder()
                        .setVerbosity(FFmpegBuilder.Verbosity.QUIET) //TOOD: Allow this to be changed
                        .addInput(data.absolutePath)
                        .addOutput(tmp.absolutePath)
                        .setFormat(this.format)
                        .done()

                    //TODO: Handle as a proper asynchronous request? Might not be actually
                    val job = HumbleMediaPlugin.ffmpegExecutor.createJob(builder)
                    job.run()

                    while (job.state == FFmpegJob.State.RUNNING || job.state == FFmpegJob.State.WAITING) {
                        Thread.sleep(100)
                    }

                    if (job.state == FFmpegJob.State.FAILED)
                        return FormatWriteResponse.FAIL()

                    FileInputStream(tmp).use(stream::copyFromStream)
                    return FormatWriteResponse.SUCCESS
                } catch (io: IOException) {
                    return FormatWriteResponse.FAIL(io)
                } finally {
                    tmp.delete()
                }
            }
            is ByteArray -> {
            }
            is InputStream -> {
            }
            else -> return FormatWriteResponse.WRONG_FORMAT
        }

        return FormatWriteResponse.SUCCESS
    }
}