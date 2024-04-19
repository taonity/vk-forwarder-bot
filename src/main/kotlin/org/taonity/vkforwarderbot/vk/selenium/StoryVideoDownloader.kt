package org.taonity.vkforwarderbot.vk.selenium

import com.vk.api.sdk.objects.stories.Story
import mu.KotlinLogging
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.Wait
import org.taonity.vkforwarderbot.exceptions.StoryVideoException
import java.io.File
import java.io.FileFilter
import java.time.Duration
import java.util.regex.Pattern

private val LOGGER = KotlinLogging.logger {}

class StoryVideoDownloader(
    private var driver: WebDriver,
    private var wait: Wait<WebDriver>,
    private val cacheDirPath: String,
) {
    private var storyDownloadFails = 0
    companion object {
        private val urlsOfDownloadingVideos = ArrayList<String>()
        fun clearUrlsOfDownloadingVideos() {
            urlsOfDownloadingVideos.clear()
        }
    }

    fun downloadStoryVideosInCache(stories: List<Story>) {
        for (story in stories) {
            tryToStartDownloadStoryVideos(story)
        }

        tryToWaitUntilNoPartVideosLeftInCache()
    }

    private fun tryToWaitUntilNoPartVideosLeftInCache() {
        val fluentWait = buildFluentWaitForParts()
        LOGGER.debug { "About to wait for videos to download" }
        var unexpectedFiles: Array<out File> = arrayOf()
        try {
            // TODO: for some reason on timeout waits more then needed
            fluentWait.until {
                unexpectedFiles = findFilesInCacheByRegex(it) ?: arrayOf()
                unexpectedFiles.isEmpty()
            }
            LOGGER.debug { "All files have been downloaded" }
        } catch (e: TimeoutException) {
            val unexpectedFileNames = unexpectedFiles.toList().stream().map { it.name }.toList()
            LOGGER.error { "Failed to download files. Files $unexpectedFileNames are not expected. $e" }
        }
    }

    private fun buildFluentWaitForParts(): FluentWait<String> {
        return FluentWait("*.mp4.part")
            .withTimeout(Duration.ofSeconds(120))
            .pollingEvery(Duration.ofSeconds(1))
    }

    private fun tryToStartDownloadStoryVideos(story: Story) {
        try {
            startDownloadStoryVideosIfNoMuchExceptions(story)
        } catch (e: StoryVideoException) {
            LOGGER.error { e }
        }
    }

    private fun startDownloadStoryVideosIfNoMuchExceptions(story: Story) {
        val storyLink = "https://vk.com/feed?w=story${story.ownerId}_${story.id}%2Ffeed"
        LOGGER.debug { "About to go to $storyLink" }
        LOGGER.debug { "Detail link is ${story.link}" }
        driver.get(storyLink)
        LOGGER.debug { "Page loaded" }
        try {
            startDownloadUniqueVideo()
        } catch (e: TimeoutException) {
            throwIfIsThirdException(e)
        }
    }

    private fun throwIfIsThirdException(e: TimeoutException) {
        storyDownloadFails++
        if (storyDownloadFails == 3) {
            throw e
        }
        LOGGER.debug { "Failed to download story, fail $storyDownloadFails" }
    }

    private fun startDownloadUniqueVideo() {
        val videoUrl: String? = retrieveVideoUrl()
        if (videoUrl.isNullOrBlank()) {
            throw StoryVideoException("Video URL is null or blank")
        }

        LOGGER.debug { "Video URL retrieved $videoUrl" }

        startDownloadingVideo(videoUrl)
        tryToWaitForStoryVideoDownloadToStart(videoUrl)

        LOGGER.debug { "Video started to download" }
        urlsOfDownloadingVideos.add(videoUrl)
    }
    private fun tryToWaitForStoryVideoDownloadToStart(videoUrl: String) {
        val videoName = getVideoIdFromUrl(videoUrl)
            ?: throw StoryVideoException("Can't extract story video name from url $videoUrl")

        val fluentWait = FluentWait("${videoName}*")
            .withTimeout(Duration.ofSeconds(20))
            .pollingEvery(Duration.ofSeconds(1))

        try {
            fluentWait.until { existsInCacheByRegex(it) }
        } catch (e: TimeoutException) {
            LOGGER.error { "Timeout on waiting for story video to start downloading. $e" }
        }
    }

    private fun existsInCacheByRegex(regex: String): Boolean {
        val files = findFilesInCacheByRegex(regex) ?: return false
        return files.isNotEmpty()
    }

    private fun findFilesInCacheByRegex(regex: String) : Array<out File>? {
        val cacheDir = File(cacheDirPath)
        val fileFilter : FileFilter = WildcardFileFilter(regex)
        return cacheDir.listFiles(fileFilter)
    }

    private fun getVideoIdFromUrl(videoUrl: String) : String? {
        val pattern: Pattern = Pattern.compile("id=(.*)$")
        val matcher = pattern.matcher(videoUrl)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun retrieveVideoUrl(): String? {
        return wait.until { d: WebDriver? ->
            val shortElement = d!!.findElement(By.cssSelector("video"))
            val videoUrl = shortElement.getAttribute("src")
            if (!urlsOfDownloadingVideos.contains(videoUrl)) {
                videoUrl
            } else {
                null
            }
        }
    }

    private fun startDownloadingVideo(videoUrl: String?) {
        if (!videoUrl.isNullOrBlank()) {
            driver.get(videoUrl)
            urlsOfDownloadingVideos.add(videoUrl)
        }
    }
}