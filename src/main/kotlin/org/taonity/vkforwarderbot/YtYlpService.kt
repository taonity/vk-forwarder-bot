package org.taonity.vkforwarderbot

import com.vk.api.sdk.objects.video.Video
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStreamReader
import java.time.Duration
import java.time.Instant
import java.util.stream.Collectors


private val logger = KotlinLogging.logger {}

@Component
class YtYlpService(
    private val cacheService: CacheService,
    @Value("\${forwarder.vk.username}") private val vkUsername: String,
    @Value("\${forwarder.vk.password}") private val vkPassword: String,
    @Value("\${forwarder.yt-ylp-file-path}") private val ytYlpFilePath: String
) {

    fun downloadVideoInCache(video: Video) : String? {
        val vkVideoUrl = "https://vk.com/video${video.ownerId}_${video.id}"
        logger.debug { "Start downloading the video: $vkVideoUrl" }

        val process = ProcessBuilder(ytYlpFilePath,
            "-P ${cacheService.cacheDirPath}",
            "--username", vkUsername,
            "--password", vkPassword,
            vkVideoUrl)
            .start()
        val videoDownloadingDuration = waitForVideoToDownalod(process)

        val ytDlpErrorLog = process.errorReader().lines().collect(Collectors.joining())
        if(ytDlpErrorLog.isNotEmpty()) {
            logger.error { ytDlpErrorLog }
            return null
        }
        val videoName = cacheService.listFilesInCache().stream()
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
}