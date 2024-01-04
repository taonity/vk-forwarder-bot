package org.taonity.vkforwarderbot.tg

import com.vk.api.sdk.objects.photos.Photo
import com.vk.api.sdk.objects.video.Video
import com.vk.api.sdk.objects.wall.WallpostAttachment
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import com.vk.api.sdk.objects.wall.WallpostFull
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
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
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Component
class TgBotService(
    private val tgBot: TgBot,
    private val ytYlpService: YtYlpService,
    @Value("\${forwarder.tg.target-user-id}") private val tgTargetUserId: String
) {
    private val MAX_VIDEO_DURATION = 300
    private val MAX_VIDEO_SIZE = 50

    fun sendMediaGroup(post: WallpostFull) {
        val inputMedias = post.attachments.stream()
            .map { attachment -> attachmentToInputMedia(attachment) }
            .filter { inputMedia -> inputMedia != null }
            .toList()
        val sendMediaGroup = SendMediaGroup.builder()
            .chatId(tgTargetUserId)
            .medias(inputMedias)
            .build()

        val uploadingStartTime = Instant.now()
        try {
            tgBot.execute(sendMediaGroup)
        } catch (e: TelegramApiException) {
            val videoDownloadingDuration = Duration.between(uploadingStartTime, Instant.now()).toSeconds()
            throw TgUnexpectedResponseException(
                "Failed to upload media group with uploading duration of $videoDownloadingDuration sec", e
            )
        }
        val videoDownloadingDuration = Duration.between(uploadingStartTime, Instant.now()).toSeconds()
        logger.debug { "The media group have been uploaded with uploading duration of $videoDownloadingDuration sec" }
        ytYlpService.clearCache()
    }

    fun sendPhoto(photo: Photo) {
        val vkPhotoUrl = photo.sizes.last().url.toURL();
        val sendPhoto = SendPhoto.builder()
            .chatId(tgTargetUserId)
            .photo(InputFile(vkPhotoUrl.toString()))
            .build()

        try {
            tgBot.execute(sendPhoto)
        } catch (e: TelegramApiException) {
            throw RuntimeException(e)
        }
        logger.debug { "A photo have been forwarded" }
    }

    fun sendVideo(video: Video) {
        val file = validateVideo(video)
            ?: return

        val vkThumbUrl = video.image?.last()?.url?.toURL()
        val sendVideo = SendVideo.builder()
            .chatId(tgTargetUserId)
            .video(InputFile(file))
            .thumb(InputFile(vkThumbUrl?.toString()))
            .build()

        val uploadingStartTime = Instant.now()
        try {
            tgBot.execute(sendVideo)
        } catch (e: TelegramApiException) {
            val videoDownloadingDuration = Duration.between(uploadingStartTime, Instant.now()).toSeconds()
            throw TgUnexpectedResponseException(
                "Failed to upload video with uploading duration of $videoDownloadingDuration sec",
                e
            )
        }
        val videoDownloadingDuration = Duration.between(uploadingStartTime, Instant.now()).toSeconds()
        logger.debug { "The video have been uploaded with uploading duration of $videoDownloadingDuration sec" }
        ytYlpService.clearCache()
    }

    private fun validateVideo(video: Video) : File? {
        if (video.duration > MAX_VIDEO_DURATION) {
            logger.debug { "The video with duration ${video.duration} sec is too long" }
            return null
        }

        val videoName = ytYlpService.downloadVideoInCache(video)
            ?: return null
        val file = File(videoName)

        val videoSizeInMb = bytesToMegabytes(file.length())
        if (videoSizeInMb >= MAX_VIDEO_SIZE) {
            logger.debug { "The video with size $videoSizeInMb MB is too large" }
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
        val file = validateVideo(video)
            ?: return null

        val vkThumbUrl = video.image?.last()?.url?.toURL()
        return InputMediaVideo.builder()
            .media("attach://${file.name}")
            .newMediaFile(file)
            .thumb(InputFile(vkThumbUrl?.toString()))
            .build()
    }

    private fun bytesToMegabytes(bytes: Long) : Long {
        return bytes / (1024 * 1024)
    }
}