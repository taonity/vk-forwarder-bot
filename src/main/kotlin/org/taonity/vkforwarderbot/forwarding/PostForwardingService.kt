package org.taonity.vkforwarderbot.forwarding

import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.DateUtils
import org.taonity.vkforwarderbot.exceptions.DbUnexpectedResponseException
import org.taonity.vkforwarderbot.exceptions.TgUnexpectedResponseException
import org.taonity.vkforwarderbot.exceptions.VkUnexpectedResponseException
import org.taonity.vkforwarderbot.tg.TgBotService
import org.taonity.vkforwarderbot.vk.VkBotService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import java.time.LocalDateTime
import java.util.*

private val LOGGER = KotlinLogging.logger {}
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

        LOGGER.debug { "${photoAndVideoPosts.size} posts are ready to forward" }

        for (post in photoAndVideoPosts) {
            try {
                forwardPost(post, vkBotGroupDetails.tgChannelId)
                LOGGER.debug { "Post have been forwarded" }
            } catch (e: TgUnexpectedResponseException) {
                LOGGER.error(e) { "Failed to forward post. Skip" }
            } finally {
                saveLastPostLocalDateTime(getPostLocalDateTime(post), vkBotGroupDetails.vkGroupId)
            }
        }
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
            else -> {
                LOGGER.warn { "Wall post attachment's type is nor photo, nor video" }
            }
        }
    }

    private fun saveLastPostLocalDateTime(lastPostLocalDateTime: LocalDateTime, vkGroupId: Long) {
        val vkBotGroupDetails = vkGroupDetailsRepository.findByVkGroupId(vkGroupId)
            ?: throw DbUnexpectedResponseException("Failed to retrieve vk group details")

        vkBotGroupDetails.lastForwardedPostDateTime = lastPostLocalDateTime
        vkGroupDetailsRepository.save(vkBotGroupDetails)
    }

    private fun filterPostsAfterGivenTimeWithPhotosOrVideos(
        posts: List<WallItem>,
        postDateTimeToBeginFrom: LocalDateTime
    ): List<WallItem> {
        val photoAndVideoPosts = posts.stream()
            .filter { item -> DateUtils.epochMilliToLocalDateTime(item.date).isAfter(postDateTimeToBeginFrom) }
            .filter { item ->
                item.attachments.stream().filter { attachment -> isOfTypePhotoOrVideo(attachment) }.findAny().isPresent
            }
            .toList()
        return photoAndVideoPosts
    }

    private fun retrieveLastGroupUnpinnedPosts(vkGroupId: Long): List<WallItem> {
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

    private fun getLastPostLocalDateTime(posts: List<WallItem>): LocalDateTime {
        return getPostLocalDateTime(posts[0])
    }

    private fun getPostLocalDateTime(post: WallItem): LocalDateTime {
        return DateUtils.epochMilliToLocalDateTime(post.date)
    }

    private fun isOfTypePhotoOrVideo(attachment: WallpostAttachment) : Boolean {
        return attachment.type == WallpostAttachmentType.PHOTO || attachment.type == WallpostAttachmentType.VIDEO
    }
}