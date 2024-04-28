package automation.services

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@Configuration
class TgATBotConfig (private val tgATBot: TgATBot): CommandLineRunner {

    override fun run(vararg args: String?) {
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
        botsApi.registerBot(tgATBot)
    }

}