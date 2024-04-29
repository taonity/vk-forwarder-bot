package automation.services

import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

private val LOGGER = KotlinLogging.logger {}

@Component
class TgATBot(
    @Value("\${forwarder.testing.tg.bot.token}") private val token: String,
    @Value("\${forwarder.testing.tg.bot.username}") private val botUsername: String,
    @Value("\${forwarder.testing.tg.channel-id}") private val expectedChannelId: Long
): TelegramLongPollingBot(token) {

    override fun getBotUsername(): String {
        return botUsername
    }

    override fun onUpdateReceived(update: Update) {
        if (update.hasChannelPost()) {
            val channelPost = update.channelPost
            if (channelPost.chat.id == expectedChannelId) {
                identifyAndAddToTgMessageCache(channelPost)
            } else {
                LOGGER.error { "Update channel id is not $expectedChannelId, but ${channelPost.chat.id}" }
            }
        } else {
            LOGGER.error { "Update doesn't contain channel post, and looks like this: $update" }
        }

    }

    private fun identifyAndAddToTgMessageCache(channelPost: Message) {
        if (channelPost.hasPhoto()) {
            TgMessageCacheService.add(channelPost.date, WallpostAttachmentType.PHOTO)
        } else if (channelPost.hasVideo()) {
            TgMessageCacheService.add(channelPost.date, WallpostAttachmentType.VIDEO)
        } else {
            LOGGER.error { "Update channel post have nor photo, nor video, and looks like this: $channelPost" }
        }
    }
}