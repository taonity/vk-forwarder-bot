package org.taonity.vkforwarderbot.tg

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

@Component
open class TgBot(
    @Value("\${forwarder.tg.token}") private val tgToken: String,
    @Value("\${forwarder.tg.bot-username}") private val tgBotUsername: String
): TelegramLongPollingBot() {
    override fun getBotToken(): String {
        return tgToken
    }

    override fun getBotUsername(): String {
        return tgBotUsername
    }

    override fun onUpdateReceived(update: Update?) {
    }
}