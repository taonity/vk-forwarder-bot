package org.taonity.vkforwarderbot.forwarding

import com.vk.api.sdk.objects.stories.FeedItem
import com.vk.api.sdk.objects.stories.Story
import com.vk.api.sdk.objects.stories.StoryType
import mu.KotlinLogging
import org.openqa.selenium.TimeoutException
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.CacheService
import org.taonity.vkforwarderbot.exceptions.DbUnexpectedResponseException
import org.taonity.vkforwarderbot.tg.TgBotService
import org.taonity.vkforwarderbot.vk.VkBotService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import org.taonity.vkforwarderbot.vk.selenium.SeleniumService
import org.taonity.vkforwarderbot.vk.selenium.SeleniumVkWalker
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors


private val logger = KotlinLogging.logger {}
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
) {
    fun forwardStories(vkBotGroupDetails: VkGroupDetailsEntity) {
        val stories = retrieveStories(vkBotGroupDetails)
            ?: return

        logger.debug { "${stories.size} stories are ready to forward" }
        if (stories.isEmpty()) {
            return
        }

        forwardStories(stories, vkBotGroupDetails)
    }

    private fun forwardStories(stories: MutableList<Story>, vkBotGroupDetails: VkGroupDetailsEntity) {
        val seleniumVkWalker = seleniumService.buildVkWalker()
        try {
            forwardStoriesUsingSeleniumVkWalker(seleniumVkWalker, stories, vkBotGroupDetails)
        } catch (e: Exception) {
            throw e
        } finally {
            seleniumVkWalker.quit()
        }
    }

    private fun forwardStoriesUsingSeleniumVkWalker(
        seleniumVkWalker: SeleniumVkWalker,
        stories: MutableList<Story>,
        vkBotGroupDetails: VkGroupDetailsEntity
    ) {
        loginIntoVkWith2Attempts(seleniumVkWalker)
        val storyChunks = divideStoriesOnChunks(stories)
        storyChunks.forEachIndexed { index, storyChunk ->
            forwardStoryChunk(index, storyChunk, seleniumVkWalker, vkBotGroupDetails)
        }
    }

    private fun retrieveStories(vkBotGroupDetails: VkGroupDetailsEntity): MutableList<Story>? {
        val feedItems = vkBotService.retrieveFeedItems(vkBotGroupDetails.vkGroupId)
        val availableStoriesWithVideos = getFilteredAvailableStoriesWithVideos(feedItems)
        if (availableStoriesWithVideos.isEmpty()) {
            logger.debug { "There are no available stories with videos" }
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
            logger.warn { "Timeout while downloading story videos in cache. Retry..." }
            seleniumVkWalker.loginIntoVk()
        }
    }

    private fun divideStoriesOnChunks(stories: MutableList<Story>): MutableCollection<MutableList<Story>> {
        val counter = AtomicInteger()
        return stories.stream()
            .collect(Collectors.groupingBy { counter.getAndIncrement() / STORY_CHUNK_SIZE })
            .values
    }

    private fun forwardStoryChunk(
        index: Int,
        storyChunk: MutableList<Story>,
        seleniumVkWalker: SeleniumVkWalker,
        vkBotGroupDetails: VkGroupDetailsEntity
    ) {
        logger.debug { "About to forward story chunk $index of ${storyChunk.size} elements" }
        seleniumVkWalker.downloadStoryVideosInCache(storyChunk)
        sendStoryVideosFromCacheToTg(vkBotGroupDetails.tgChannelId)

        val lastStoryChunkLocalDateTime = getLastStoryLocalDateTime(storyChunk)
        saveLastStoryLocalDateTime(lastStoryChunkLocalDateTime, vkBotGroupDetails.vkGroupId)
        logger.debug { "Story chunk forwarded" }
    }

    private fun filterStoriesAfterGivenTime(
        availableStoriesWithVideos: MutableList<Story>,
        postDateTimeToBeginFrom: LocalDateTime
    ): MutableList<Story> = availableStoriesWithVideos.stream()
        .filter { story -> epochMilliToLocalDateTime(story.date).isAfter(postDateTimeToBeginFrom) }
        .toList()

    private fun getLastStoryLocalDateTime(stories: MutableList<Story>): LocalDateTime {
        val lastStoryEpochMilli = stories.stream()
            .sorted(Comparator.comparing { story -> story.date })
            .toList().last().date
        return epochMilliToLocalDateTime(lastStoryEpochMilli)
    }

    private fun getFilteredAvailableStoriesWithVideos(feedItems: MutableList<FeedItem>): MutableList<Story> =
        feedItems.stream()
            .flatMap { storyBlock -> storyBlock.stories.stream() }
            .filter { story -> story.type == StoryType.VIDEO }
            .filter { story -> story.canSee() }
            .toList()

    private fun sendStoryVideosFromCacheToTg(tgTargetId: String) {
        val storyVideoPaths = cacheService.listFilesInCache()
        for (storyVideoPath in storyVideoPaths) {
            logger.debug { "Found item in cache $storyVideoPath" }
            val storyVideoFile = File(storyVideoPath)
            if (storyVideoFile.exists()) {
                tgService.sendVideo(storyVideoFile, null, tgTargetId)
            } else {
                println("Failed to download video")
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