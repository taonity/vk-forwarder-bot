package org.taonity.vkforwarderbot.tg

import com.vk.api.sdk.objects.photos.Photo
import com.vk.api.sdk.objects.video.Video
import com.vk.api.sdk.objects.wall.WallItem
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.CacheService
import org.taonity.vkforwarderbot.YtYlpService
import org.taonity.vkforwarderbot.exceptions.TgUnexpectedResponseException
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.media.InputMedia
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.net.URL

private val LOGGER = KotlinLogging.logger {}
private const val MAX_VIDEO_DURATION = 300
private const val MAX_VIDEO_SIZE = 50

@Component
class TgBotService(
    private val tgBot: TgBot,
    private val ytYlpService: YtYlpService,
    private val cacheService: CacheService,
    private val tgMessageSendingRateLimiter: TgMessageSendingRateLimiter
) {

    fun sendMediaGroup(post: WallItem, tgTargetId: String) {
        val inputMedias = post.attachments.stream()
            .map { attachment -> attachmentToInputMedia(attachment) }
            .filter { inputMedia -> inputMedia != null }
            .toList()

        val sendMediaGroup = SendMediaGroup.builder()
            .chatId(tgTargetId)
            .medias(inputMedias)
            .build()

        tgMessageSendingRateLimiter.acquireTokensAndRun(inputMedias.size) {
            try {
                tgBot.execute(sendMediaGroup)
            } catch (e: TelegramApiException) {
                throw TgUnexpectedResponseException(
                    "Failed to upload media group", e
                )
            }
        }

        cacheService.clearCache()
    }

    fun sendPhoto(photo: Photo, tgTargetId: String) {
        val vkPhotoUrl = photo.sizes.last().url.toURL()
        val sendPhoto = SendPhoto.builder()
            .chatId(tgTargetId)
            .photo(InputFile(vkPhotoUrl.toString()))
            .build()

        tgMessageSendingRateLimiter.acquireTokensAndRun {
            try {
                tgBot.execute(sendPhoto)
            } catch (e: TelegramApiException) {
                throw RuntimeException(e)
            }
        }

        LOGGER.debug { "A photo have been forwarded" }
    }

    fun sendVideo(video: Video, tgTargetId: String) {
        val file = downloadVideo(video)
            ?: return

        val vkThumbUrl = video.image?.last()?.url?.toURL()!!
        sendVideo(file, vkThumbUrl, tgTargetId)
        cacheService.clearCache()
    }

    fun sendVideo(file: File, thumbUrl: URL?, tgTargetId: String) {
        val sendVideoBuilder = SendVideo.builder()
            .chatId(tgTargetId)
            .video(InputFile(file))

        thumbUrl.let {
            sendVideoBuilder
                .thumbnail(InputFile(it.toString()))
        }

        val sendVideo = sendVideoBuilder.build()
        tgMessageSendingRateLimiter.acquireTokensAndRun {
            try {
                tgBot.execute(sendVideo)
            } catch (e: TelegramApiException) {
                throw TgUnexpectedResponseException("Failed to upload video", e)
            }
        }

        LOGGER.debug { "Video have been uploaded" }
    }

    private fun downloadVideo(video: Video) : File? {
        if (video.duration > MAX_VIDEO_DURATION) {
            LOGGER.debug { "Video with duration ${video.duration} sec is too long" }
            return null
        }

        val videoName = ytYlpService.downloadVideoInCache(video)
            ?: return null
        val file = File(videoName)

        val videoSizeInMb = bytesToMegabytes(file.length())
        if (videoSizeInMb >= MAX_VIDEO_SIZE) {
            LOGGER.debug { "Video with size $videoSizeInMb MB is too large" }
            return null
        }

        return file
    }

    private fun attachmentToInputMedia(wallpostAttachment: WallpostAttachment) : InputMedia? {
        return when (wallpostAttachment.type) {
            WallpostAttachmentType.PHOTO -> toInputMediaPhoto(wallpostAttachment.photo)
            WallpostAttachmentType.VIDEO -> toInputMediaVideo(wallpostAttachment.video)
            else -> {null}
        }
    }

    private fun toInputMediaPhoto(photo: Photo) : InputMediaPhoto {
        val vkPhotoUrl = photo.sizes.last().url.toURL()

        return InputMediaPhoto.builder()
            .media(vkPhotoUrl.toString())
            .build()
    }

    private fun toInputMediaVideo(video: Video) : InputMediaVideo? {
        val file = downloadVideo(video)
            ?: return null

        val vkThumbUrl = video.image?.last()?.url?.toURL()
        return InputMediaVideo.builder()
            .media("attach://${file.name}")
            .newMediaFile(file)
            .thumbnail(InputFile(vkThumbUrl?.toString()))
            .build()
    }

    private fun bytesToMegabytes(bytes: Long) : Long {
        return bytes / (1024 * 1024)
    }
}