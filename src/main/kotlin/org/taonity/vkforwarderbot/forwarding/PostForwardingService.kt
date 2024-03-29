package org.taonity.vkforwarderbot.forwarding

import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.exceptions.DbUnexpectedResponseException
import org.taonity.vkforwarderbot.exceptions.VkUnexpectedResponseException
import org.taonity.vkforwarderbot.tg.TgBotService
import org.taonity.vkforwarderbot.vk.VkBotService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

private val logger = KotlinLogging.logger {}
private val ZINE_ID = ZoneId.of("UTC")
private const val HOURS_TIME_PERIOD_TO_FORWARD_POSTS_IN = 1L

@Component
class PostForwardingService (
    private val vkBotService: VkBotService,
    private val vkGroupDetailsRepository: VkGroupDetailsRepository,
    private val tgService: TgBotService,
) {
    fun forwardPosts(vkBotGroupDetails: VkGroupDetailsEntity) {
        val posts = retrieveLastGroupUnpinnedPosts(vkBotGroupDetails.vkGroupId)

        val lastPostLocalDateTime = getLastPostLocalDateTime(posts)
        val postDateTimeToBeginFrom =
            calculatePostDateTimeToBeginFrom(vkBotGroupDetails.lastForwardedPostDateTime, lastPostLocalDateTime)
        val photoAndVideoPosts = filterPostsAfterGivenTimeWithPhotosOrVideos(posts, postDateTimeToBeginFrom)

        logger.debug { "${photoAndVideoPosts.size} posts are ready to forward" }

        for (post in photoAndVideoPosts) {
            forwardPost(post, vkBotGroupDetails.tgChannelId)
            logger.debug { "Post have been forwarded" }
        }

        saveLastPostLocalDateTime(lastPostLocalDateTime, vkBotGroupDetails.vkGroupId)
    }

    private fun forwardPost(post: WallItem, tgTargetId: String) {
        val photoAndVideoAttachments = post.attachments.stream()
            .filter { attachment -> isOfTypePhotoOrVideo(attachment) }
            .toList()

        when (photoAndVideoAttachments.size) {
            0 -> return
            1 -> sendPhotoOrVideo(photoAndVideoAttachments[0], tgTargetId)
            else -> tgService.sendMediaGroup(post, tgTargetId)
        }
    }

    private fun sendPhotoOrVideo(photoOrVideoAttachment: WallpostAttachment, tgTargetId: String) {
        when (photoOrVideoAttachment.type) {
            WallpostAttachmentType.PHOTO -> tgService.sendPhoto(photoOrVideoAttachment.photo, tgTargetId)
            WallpostAttachmentType.VIDEO -> tgService.sendVideo(photoOrVideoAttachment.video, tgTargetId)
            else -> {}
        }
    }

    private fun saveLastPostLocalDateTime(lastPostLocalDateTime: LocalDateTime, vkGroupId: Long) {
        val vkBotGroupDetails = vkGroupDetailsRepository.findByVkGroupId(vkGroupId)
            ?: throw DbUnexpectedResponseException("Failed to retrieve vk group details")

        vkBotGroupDetails.lastForwardedPostDateTime = lastPostLocalDateTime
        vkGroupDetailsRepository.save(vkBotGroupDetails)
    }

    private fun filterPostsAfterGivenTimeWithPhotosOrVideos(
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

    private fun retrieveLastGroupUnpinnedPosts(vkGroupId: Long): MutableList<WallItem> {
        return vkBotService.retrieveWallItems(vkGroupId)
            .stream()
            .filter { item -> !item.isPinned() }
            .toList()
            .takeIf { it.isNotEmpty() } ?: throw VkUnexpectedResponseException("There is not unpinned post requests")
    }

    private fun calculatePostDateTimeToBeginFrom(lastForwardedPostDateTime: LocalDateTime?, lastPostLocalDateTime: LocalDateTime
    ): LocalDateTime = if (Objects.isNull(lastForwardedPostDateTime)) {
        lastPostLocalDateTime.minusHours(HOURS_TIME_PERIOD_TO_FORWARD_POSTS_IN)
    } else {
        lastForwardedPostDateTime!!
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