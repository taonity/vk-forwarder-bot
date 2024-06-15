package org.taonity.vkforwarderbot

import mu.KotlinLogging
import org.apache.commons.lang3.SystemUtils
import org.springframework.stereotype.Component
import java.io.InputStreamReader
import java.util.stream.Collectors
import kotlin.jvm.optionals.getOrNull

private val LOGGER = KotlinLogging.logger {}

@Component
class CurlService(
    private val cacheService: CacheService
) {
    fun downloadVideoInCache(videoUrl: String, videoBaseName: String) : String? {
        val videoName = "${videoBaseName}.mp4"

        LOGGER.debug { "Start downloading the video $videoUrl with $videoBaseName name" }

        val processBuilder = buildProcessBuilder(videoName, videoUrl)
        val process = processBuilder.start()

        waitForVideoToDownload(process)

        val curlErrorLog = process.errorReader().lines().collect(Collectors.joining())

        if(curlErrorLog.isNotEmpty()) {
            LOGGER.error { curlErrorLog }
            return null
        }

        return cacheService.listFilesInCache().stream()
            .filter { cachedVideoName -> cachedVideoName.contains(videoName) }
            .findAny()
            .getOrNull()
    }

    private fun buildProcessBuilder(videoName: String, videoUrl: String) = if (SystemUtils.IS_OS_WINDOWS) {
        ProcessBuilder(
            "bash.exe",
            "-c",
            "curl --silent --output ${cacheService.cacheDirPath}/${videoName} '${videoUrl}'",
        )
    } else if (SystemUtils.IS_OS_UNIX) {
        ProcessBuilder(
            "curl",
            "--silent",
            "--output",
            "${cacheService.cacheDirPath}/${videoName}",
            videoUrl
        )
    } else {
        throw RuntimeException(String.format("Unknown os encountered: %s", SystemUtils.OS_NAME))
    }

    private fun waitForVideoToDownload(process: Process) {
        // TODO: find a better way to do the waiting
        InputStreamReader(process.inputStream).use { inputStreamReader ->
            while (inputStreamReader.read() >= 0) {
            }
        }

        LOGGER.info { "Finish downloading the video" }
    }
}