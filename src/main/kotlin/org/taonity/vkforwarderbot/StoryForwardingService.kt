package org.taonity.vkforwarderbot

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.objects.stories.FeedItem
import com.vk.api.sdk.objects.stories.Story
import com.vk.api.sdk.objects.stories.StoryType
import mu.KotlinLogging
import org.openqa.selenium.TimeoutException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.exceptions.DbUnexpectedResponseException
import org.taonity.vkforwarderbot.exceptions.VkUnexpectedResponseException
import org.taonity.vkforwarderbot.tg.TgBotService
import org.taonity.vkforwarderbot.vk.SeleniumService
import org.taonity.vkforwarderbot.vk.SeleniumVkWalker
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
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
    private val vkApiClient: VkApiClient,
    private val userActor: UserActor,
    private val tgService: TgBotService,
    private val seleniumService: SeleniumService,
    private val vkGroupDetailsRepository: VkGroupDetailsRepository,
    private val cacheService: CacheService,
    @Value("\${forwarder.vk.group-id}") private val vkGroupId: Long,
) {
    fun forward(vkBotGroupDetails: VkGroupDetailsEntity) {
        val stories = retrieveStories(vkBotGroupDetails)

        logger.debug { "${stories.size} stories are ready to forward" }
        if (stories.isEmpty()) {
            return
        }

        forwardStories(stories)
    }

    private fun forwardStories(stories: MutableList<Story>) {
        val seleniumVkWalker = seleniumService.buildVkWalker()
        try {
            forwardStoriesUsingSeleniumVkWalker(seleniumVkWalker, stories)
        } catch (e: Exception) {
            throw e
        } finally {
            seleniumVkWalker.quit()
        }
    }

    private fun forwardStoriesUsingSeleniumVkWalker(
        seleniumVkWalker: SeleniumVkWalker,
        stories: MutableList<Story>
    ) {
        loginIntoVkWith2Attempts(seleniumVkWalker)
        val storyChunks = divideStoriesOnChunks(stories)
        storyChunks.forEachIndexed { index, storyChunk ->
            forwardStoryChunk(index, storyChunk, seleniumVkWalker)
        }
    }

    private fun retrieveStories(vkBotGroupDetails: VkGroupDetailsEntity): MutableList<Story> {
        val feedItems = retrieveFeedItems()
        val availableStoriesWithVideos = getFilteredAvailableStoriesWithVideos(feedItems)
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
        seleniumVkWalker: SeleniumVkWalker
    ) {
        logger.debug { "About to forward story chunk $index of ${storyChunk.size} elements" }
        seleniumVkWalker.downloadStoryVideosInCache(storyChunk)
        sendStoryVideosFromCacheToTg()

        val lastStoryChunkLocalDateTime = getLastStoryLocalDateTime(storyChunk)
        saveLastStoryLocalDateTime(lastStoryChunkLocalDateTime)
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

    private fun retrieveFeedItems(): MutableList<FeedItem> {
        return vkApiClient.stories()
            .getV5113(userActor)
            .ownerId(vkGroupId)
            .execute()
            .items
            ?: throw VkUnexpectedResponseException("Failed to retrieve feed items")
    }

    private fun getFilteredAvailableStoriesWithVideos(feedItems: MutableList<FeedItem>): MutableList<Story> =
        feedItems.stream()
            .flatMap { storyBlock -> storyBlock.stories.stream() }
            .filter { story -> story.type == StoryType.VIDEO }
            .filter { story -> story.canSee() }
            .toList()

    private fun sendStoryVideosFromCacheToTg() {
        val storyVideoPaths = cacheService.listFilesInCache()
        for (storyVideoPath in storyVideoPaths) {
            logger.debug { "Found item in cache $storyVideoPath" }
            val storyVideoFile = File(storyVideoPath)
            if (storyVideoFile.exists()) {
                tgService.sendVideo(storyVideoFile, null)
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

    private fun saveLastStoryLocalDateTime(lastStoryLocalDateTime: LocalDateTime) {
        val vkBotGroupDetails = vkGroupDetailsRepository.findByGroupId(vkGroupId)
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