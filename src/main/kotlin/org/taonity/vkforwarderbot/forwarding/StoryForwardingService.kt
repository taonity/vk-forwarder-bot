package org.taonity.vkforwarderbot.forwarding

import com.vk.api.sdk.objects.stories.FeedItem
import com.vk.api.sdk.objects.stories.Story
import com.vk.api.sdk.objects.stories.StoryType
import mu.KotlinLogging
import org.openqa.selenium.TimeoutException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.CacheService
import org.taonity.vkforwarderbot.CurlService
import org.taonity.vkforwarderbot.DateUtils
import org.taonity.vkforwarderbot.exceptions.DbUnexpectedResponseException
import org.taonity.vkforwarderbot.exceptions.StoryVideoException
import org.taonity.vkforwarderbot.exceptions.TgUnexpectedResponseException
import org.taonity.vkforwarderbot.tg.TgBotService
import org.taonity.vkforwarderbot.vk.VkBotService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import org.taonity.vkforwarderbot.vk.selenium.SeleniumService
import org.taonity.vkforwarderbot.vk.selenium.SeleniumVkWalker
import org.taonity.vkforwarderbot.vk.selenium.StoryVideoDownloader
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.Objects.nonNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors


private val LOGGER = KotlinLogging.logger {}
private val ZINE_ID = ZoneId.of("UTC")
private const val HOURS_TIME_PERIOD_TO_FORWARD_POSTS_IN = 24L
private const val STORY_CHUNK_SIZE = 3

@Component
class StoryForwardingService (
    private val vkBotService: VkBotService,
    private val tgService: TgBotService,
    private val seleniumService: SeleniumService,
    private val vkGroupDetailsRepository: VkGroupDetailsRepository,
    private val cacheService: CacheService,
    private val curlService: CurlService,
    @Value("\${forwarder.debug.max-stories-to-process}") private val maxStoriesToProcess: Int
) {
    fun forwardStories(vkBotGroupDetails: VkGroupDetailsEntity) {
        val stories = retrieveStories(vkBotGroupDetails)
            ?: return

        LOGGER.debug { "${stories.size} stories before trim" }

        val trimmedStories = stories.take(maxStoriesToProcess)

        LOGGER.debug { "${trimmedStories.size} stories are ready to forward" }
        if (trimmedStories.isEmpty()) {
            return
        }
        
        for (story in trimmedStories) {

            try {
                forwardStory(story, vkBotGroupDetails)
                LOGGER.debug { "Story have been forwarded" }
            } catch (e: TgUnexpectedResponseException) {
                LOGGER.error(e) { "Failed to forward post. Skip" }
            } finally {
                cacheService.clearCache()
                saveLastStoryLocalDateTime(getPostLocalDateTime(story), vkBotGroupDetails.vkGroupId)
            }
        }
    }

    private fun forwardStory(
        story: Story,
        vkBotGroupDetails: VkGroupDetailsEntity
    ) {
        val rawVideoUrl = getVideoUrlIfExists(story) ?:
            return

        val videoUrl = rawVideoUrl.toString().replace("\\", "")

        val videoFullPath = curlService.downloadVideoInCache(videoUrl, story.id.toString())
            ?: throw StoryVideoException("Video with story is and base name ${story.id} not found")

        tgService.sendVideo(File(videoFullPath), null, vkBotGroupDetails.tgChannelId)
    }

    private fun getVideoUrlIfExists(story: Story): URI? {
        if (nonNull(story.video)) {
            val video = story.video
            // TODO try to get other format as well
            if (nonNull(video.files.mp4480)) {
                return video.files.mp4480
            } else {
                LOGGER.warn { "Story ${story.id} doesn't have mp4_480 video format. Skip." }
            }
        } else {
            LOGGER.warn { "Story ${story.id} doesn't have video. Skip." }
        }
        return null
    }

    private fun getPostLocalDateTime(post: Story): LocalDateTime {
        return DateUtils.epochMilliToLocalDateTime(post.date)
    }

    @Deprecated("New story downloading has been added and is under test")
    private fun forwardStories(stories: List<Story>, vkBotGroupDetails: VkGroupDetailsEntity) {
        val seleniumVkWalker = seleniumService.buildVkWalker()
        // TODO: refactor to use several groups
        try {
            forwardStoriesUsingSeleniumVkWalker(seleniumVkWalker, stories, vkBotGroupDetails)
        } catch (e: Exception) {
            throw e
        } finally {
            // TODO: causes Tried to run command without establishing a connection
            LOGGER.debug { "Web driver quit initiated" }
            try {
                seleniumVkWalker.quit()
                LOGGER.debug { "Web driver quit complete" }
            } catch (e: Exception) {
                LOGGER.error(e) { "Web driver quit failed" }
            }
        }
    }

    private fun forwardStoriesUsingSeleniumVkWalker(
        seleniumVkWalker: SeleniumVkWalker,
        stories: List<Story>,
        vkBotGroupDetails: VkGroupDetailsEntity
    ) {
        loginIntoVkWith2Attempts(seleniumVkWalker)
        val storyChunks = divideStoriesOnChunks(stories)
        storyChunks.forEachIndexed { index, storyChunk ->
            forwardStoryChunk(index, storyChunk, seleniumVkWalker, vkBotGroupDetails)
        }
        StoryVideoDownloader.clearUrlsOfDownloadingVideos()
    }

    private fun retrieveStories(vkBotGroupDetails: VkGroupDetailsEntity): List<Story>? {
        val feedItems = vkBotService.retrieveFeedItems(vkBotGroupDetails.vkGroupId)
        val availableStoriesWithVideos = getFilteredAvailableStoriesWithVideos(feedItems)
        if (availableStoriesWithVideos.isEmpty()) {
            LOGGER.debug { "There are no available stories with videos" }
            return null
        }
        val lastStoryLocalDateTime = getLastStoryLocalDateTime(availableStoriesWithVideos)
        val postDateTimeToBeginFrom =
            calculateStoryDateTimeToBeginFrom(vkBotGroupDetails.lastForwardedStoryDateTime, lastStoryLocalDateTime)
        return filterStoriesAfterGivenTime(availableStoriesWithVideos, postDateTimeToBeginFrom)
    }

    private fun loginIntoVkWith2Attempts(seleniumVkWalker: SeleniumVkWalker) {
        // TODO: bruh
        try {
            seleniumVkWalker.loginIntoVk()
        } catch (e: TimeoutException) {
            LOGGER.warn { "Timeout while downloading story videos in cache. Retry..." }
            seleniumVkWalker.loginIntoVk()
        }
    }

    private fun divideStoriesOnChunks(stories: List<Story>): MutableCollection<List<Story>> {
        val counter = AtomicInteger()
        return stories.stream()
            .collect(Collectors.groupingBy { counter.getAndIncrement() / STORY_CHUNK_SIZE })
            .values
    }

    private fun forwardStoryChunk(
        index: Int,
        storyChunk: List<Story>,
        seleniumVkWalker: SeleniumVkWalker,
        vkBotGroupDetails: VkGroupDetailsEntity
    ) {
        LOGGER.debug { "About to forward story chunk ${index + 1} that contains ${storyChunk.size} elements" }
        seleniumVkWalker.downloadStoryVideosInCache(storyChunk)
        sendStoryVideosFromCacheToTg(vkBotGroupDetails.tgChannelId)

        val lastStoryChunkLocalDateTime = getLastStoryLocalDateTime(storyChunk)
        saveLastStoryLocalDateTime(lastStoryChunkLocalDateTime, vkBotGroupDetails.vkGroupId)
        LOGGER.debug { "Story chunk forwarded" }
    }

    private fun filterStoriesAfterGivenTime(
        availableStoriesWithVideos: List<Story>,
        postDateTimeToBeginFrom: LocalDateTime
    ): List<Story> = availableStoriesWithVideos.stream()
        .filter { story -> epochMilliToLocalDateTime(story.date).isAfter(postDateTimeToBeginFrom) }
        .toList()

    private fun getLastStoryLocalDateTime(stories: List<Story>): LocalDateTime {
        val lastStoryEpochMilli = stories.stream()
            .sorted(Comparator.comparing { story -> story.date })
            .toList().last().date
        return epochMilliToLocalDateTime(lastStoryEpochMilli)
    }

    private fun getFilteredAvailableStoriesWithVideos(feedItems: List<FeedItem>): List<Story> =
        feedItems.stream()
            .flatMap { storyBlock -> storyBlock.stories.stream() }
            .filter { story -> story.type == StoryType.VIDEO }
            .filter { story -> story.canSee() }
            .toList()

    private fun sendStoryVideosFromCacheToTg(tgTargetId: String) {
        val storyVideoPaths = cacheService.listMp4FilesInCache()
        for (storyVideoPath in storyVideoPaths) {
            LOGGER.debug { "Found item in cache $storyVideoPath" }
            val storyVideoFile = File(storyVideoPath)
            if (storyVideoFile.exists()) {
                tgService.sendVideo(storyVideoFile, null, tgTargetId)
            } else {
                LOGGER.error{ "Failed to download video, video not found in cache" }
            }
        }
        cacheService.clearCache()
    }


    private fun epochMilliToLocalDateTime(lastPostEpochMilli: Int): LocalDateTime =
        LocalDateTime.ofInstant(
            Instant.ofEpochSecond(lastPostEpochMilli.toLong()),
            TimeZone.getTimeZone(ZINE_ID).toZoneId()
        )

    private fun saveLastStoryLocalDateTime(lastStoryLocalDateTime: LocalDateTime, vkGroupId: Long) {
        val vkBotGroupDetails = vkGroupDetailsRepository.findByVkGroupId(vkGroupId)
            ?: throw DbUnexpectedResponseException("Failed to retrieve vk group details")

        vkBotGroupDetails.lastForwardedStoryDateTime = lastStoryLocalDateTime
        vkGroupDetailsRepository.save(vkBotGroupDetails)
    }

    private fun calculateStoryDateTimeToBeginFrom(lastForwardedPostDateTime: LocalDateTime?, lastPostLocalDateTime: LocalDateTime
    ): LocalDateTime = if (Objects.isNull(lastForwardedPostDateTime)) {
        lastPostLocalDateTime.minusHours(HOURS_TIME_PERIOD_TO_FORWARD_POSTS_IN)
    } else {
        lastForwardedPostDateTime!!
    }

}