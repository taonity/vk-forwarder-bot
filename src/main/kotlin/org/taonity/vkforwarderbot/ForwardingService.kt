package org.taonity.vkforwarderbot

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.exceptions.VkUnexpectedResponseException
import org.taonity.vkforwarderbot.tg.TgBotService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.Objects.isNull
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}
private val ZINE_ID = ZoneId.of("UTC")
private const val HOURS_TIME_PERIOD_TO_FORWARD_POSTS_IN = 1L

@Component
open class ForwardingService constructor(
    private val vkApiClient: VkApiClient,
    private val userActor: UserActor,
    private val vkGroupDetailsRepository: VkGroupDetailsRepository,
    private val tgService: TgBotService,
    @Value("\${forwarder.vk.group-id}") private val vkGroupId: Long
) {

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    fun forward() {
        logger.debug { "Forwarding job has started" }

        val posts = retrieveLastGroupUnpinnedPosts()

        val vkBotGroupDetails = vkGroupDetailsRepository.findByGroupId(vkGroupId)
        val lastPostLocalDateTime = getLastPostLocalDateTime(posts)
        val postDateTimeToBeginFrom = calculatePostDateTimeToBeginFrom(vkBotGroupDetails, lastPostLocalDateTime)
        val photoAndVideoPosts = filterLatestPostsWithPhotosOrVideos(posts, postDateTimeToBeginFrom)

        logger.debug { "${photoAndVideoPosts.size} posts are ready to forward" }

        for (post in photoAndVideoPosts) {
            val forwardingStartTime = Instant.now()
            forwardPost(post)
            val postForwardingDuration = Duration.between(forwardingStartTime, Instant.now()).toSeconds()
            logger.debug { "The post have been forwarded with time elapsed $postForwardingDuration sec" }
        }

        saveGroupDetails(vkBotGroupDetails, lastPostLocalDateTime)

        logger.debug { "All posts have been forwarded" }
    }

    private fun forwardPost(post: WallItem) {
        val photoAndVideoAttachments = post.attachments.stream()
            .filter { attachment -> isOfTypePhotoOrVideo(attachment) }
            .toList()

        when (photoAndVideoAttachments.size) {
            0 -> return
            1 -> sendPhotoOrVideo(photoAndVideoAttachments[0])
            else -> tgService.sendMediaGroup(post)
        }
    }

    private fun sendPhotoOrVideo(photoOrVideoAttachment: WallpostAttachment) {
        when (photoOrVideoAttachment.type) {
            WallpostAttachmentType.PHOTO -> tgService.sendPhoto(photoOrVideoAttachment.photo)
            WallpostAttachmentType.VIDEO -> tgService.sendVideo(photoOrVideoAttachment.video)
            else -> {}
        }
    }

    private fun saveGroupDetails(
        vkBotGroupDetails: VkGroupDetailsEntity?,
        lastPostLocalDateTime: LocalDateTime
    ) {
        if (isNull(vkBotGroupDetails)) {
            vkGroupDetailsRepository.save(VkGroupDetailsEntity(vkGroupId, lastPostLocalDateTime))
        } else {
            vkBotGroupDetails?.lastForwardedPostDateTime = lastPostLocalDateTime
            vkGroupDetailsRepository.save(vkBotGroupDetails!!)
        }
    }

    private fun filterLatestPostsWithPhotosOrVideos(
        posts: MutableList<WallItem>,
        postDateTimeToBeginFrom: LocalDateTime
    ): MutableList<WallItem> {
        val photoAndVideoPosts = posts.stream()
            .filter { item -> epochMilliToLocalDateTime(item.date).isAfter(postDateTimeToBeginFrom) }
            .filter { item ->
                item.attachments.stream().filter { attachment -> isOfTypePhotoOrVideo(attachment) }.findAny().isPresent
            }
            .toList()
        return photoAndVideoPosts
    }

    private fun retrieveLastGroupUnpinnedPosts(): MutableList<WallItem> {
        val posts = vkApiClient.wall()
            .get(userActor)
            .ownerId(vkGroupId)
            .execute()
            .items
            .stream()
            .filter { item -> !item.isPinned() }
            .toList()
            .takeIf { it.isNotEmpty() } ?: throw VkUnexpectedResponseException("There is not unpinned post requests")
        return posts
    }

    private fun calculatePostDateTimeToBeginFrom(vkBotGroupDetails: VkGroupDetailsEntity?, lastPostLocalDateTime: LocalDateTime
    ): LocalDateTime = if (isNull(vkBotGroupDetails)) {
        lastPostLocalDateTime.minusHours(HOURS_TIME_PERIOD_TO_FORWARD_POSTS_IN)
    } else {
        vkBotGroupDetails?.lastForwardedPostDateTime!!
    }

    private fun getLastPostLocalDateTime(posts: MutableList<WallItem>): LocalDateTime {
        val lastPostEpochMilli = posts[0].date
        return epochMilliToLocalDateTime(lastPostEpochMilli)
    }

    private fun epochMilliToLocalDateTime(lastPostEpochMilli: Int): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(lastPostEpochMilli.toLong()), TimeZone.getTimeZone(ZINE_ID).toZoneId())


    private fun isOfTypePhotoOrVideo(attachment: WallpostAttachment) : Boolean {
        return attachment.type == WallpostAttachmentType.PHOTO || attachment.type == WallpostAttachmentType.VIDEO
    }


}