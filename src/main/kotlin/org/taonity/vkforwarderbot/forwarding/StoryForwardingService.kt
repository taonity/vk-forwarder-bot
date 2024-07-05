package org.taonity.vkforwarderbot.forwarding

import com.vk.api.sdk.objects.stories.FeedItem
import com.vk.api.sdk.objects.stories.Story
import com.vk.api.sdk.objects.stories.StoryType
import mu.KotlinLogging
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
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.Objects.nonNull


private val LOGGER = KotlinLogging.logger {}
private val ZINE_ID = ZoneId.of("UTC")
private const val HOURS_TIME_PERIOD_TO_FORWARD_POSTS_IN = 24L

@Component
class StoryForwardingService (
    private val vkBotService: VkBotService,
    private val tgService: TgBotService,
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
        // TODO redundant check?
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