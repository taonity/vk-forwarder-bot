package org.taonity.vkforwarderbot

import com.vk.api.sdk.objects.video.Video
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.stream.Collectors
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString


private val logger = KotlinLogging.logger {}

@Component
class YtYlpService(
    @Value("\${forwarder.vk.cookies-file-path}") private val vkCookiesFilePath: String,
    @Value("\${forwarder.cache-dir-path}") private val cacheDirPath: String,
    @Value("\${forwarder.yt-ylp-file-path}") private val ytYlpFilePath: String
) {

    fun downloadVideoInCache(video: Video) : String? {
        val vkVideoUrl = "https://vk.com/video${video.ownerId}_${video.id}"
        logger.debug { "Start downloading the video: $vkVideoUrl" }

        val process = ProcessBuilder(ytYlpFilePath, "-P $cacheDirPath", "--cookies", vkCookiesFilePath, vkVideoUrl)
            .start()
        val videoDownloadingDuration = waitForVideoToDownalod(process)

        val ytDlpErrorLog = process.errorReader().lines().collect(Collectors.joining())
        if(ytDlpErrorLog.isNotEmpty()) {
            logger.error { ytDlpErrorLog }
            return null
        }
        val videoName = listFilesInCache().stream()
            .filter { videoName -> videoName.contains(String.format("%s_%s", video.ownerId, video.id)) }
            .findAny()
            .get()

        logger.debug { "Finish downloading the video with downloading duration of $videoDownloadingDuration sec" }
        return videoName
    }

    private fun waitForVideoToDownalod(process: Process): Long {
        val downloadingStartTime = Instant.now()

        // TODO: find a better way to do the waiting
        InputStreamReader(process.inputStream).use { inputStreamReader ->
            while (inputStreamReader.read() >= 0) {
            }
        }

        val videoDownloadingDuration = Duration.between(downloadingStartTime, Instant.now()).toSeconds()
        return videoDownloadingDuration
    }

    fun clearCache() {
        Files.list(Paths.get(cacheDirPath)).forEach {
                file -> file.deleteIfExists()
        }
    }

    private fun listFilesInCache(): Set<String> {
        Files.list(Paths.get(cacheDirPath)).use {
                stream -> return stream
            .filter { file -> !Files.isDirectory(file) }
            .map { file -> file.pathString }
            .collect(Collectors.toSet())
        }
    }
}