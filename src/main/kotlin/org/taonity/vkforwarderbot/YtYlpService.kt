package org.taonity.vkforwarderbot

import com.vk.api.sdk.objects.video.Video
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStreamReader
import java.util.stream.Collectors


private val LOGGER = KotlinLogging.logger {}
private const val YT_YLP_ALLOWED_ERROR_LOG = "WARNING: [vk] Failed to download m3u8 information: HTTP Error 404: Not Found"

@Component
class YtYlpService(
    private val cacheService: CacheService,
    @Value("\${forwarder.vk.username}") private val vkUsername: String,
    @Value("\${forwarder.vk.password}") private val vkPassword: String,
    @Value("\${forwarder.yt-ylp-file-path}") private val ytYlpFilePath: String
) {

    fun downloadVideoInCache(video: Video) : String? {
        val vkVideoUrl = "https://vk.com/video${video.ownerId}_${video.id}"
        LOGGER.debug { "Start downloading the video: $vkVideoUrl" }

        val ytDlpErrorLog = runDownloadingProcessAndWait(vkVideoUrl)
        if(ytDlpErrorLog.isNotEmpty()) {
            if (ytDlpErrorLog == YT_YLP_ALLOWED_ERROR_LOG) {
                LOGGER.warn { ytDlpErrorLog }
            } else {
                LOGGER.error { ytDlpErrorLog }
                return null
            }
        }

        return cacheService.listFilesInCache().stream()
            .filter { videoName -> videoName.contains(String.format("%s_%s", video.ownerId, video.id)) }
            .findAny()
            .get()

    }

    private fun runDownloadingProcessAndWait(vkVideoUrl: String): String {
        val process = ProcessBuilder(
            ytYlpFilePath,
            "-P ${cacheService.cacheDirPath}",
            "--username", vkUsername,
            "--password", vkPassword,
            vkVideoUrl
        )
            .start()
        waitForVideoToDownload(process)
        return process.errorReader().lines().collect(Collectors.joining())
    }

    private fun waitForVideoToDownload(process: Process) {
        // TODO: find a better way to do the waiting
        InputStreamReader(process.inputStream).use { inputStreamReader ->
            while (inputStreamReader.read() >= 0) {
            }
        }

        LOGGER.debug { "Finish downloading the video" }
    }
}