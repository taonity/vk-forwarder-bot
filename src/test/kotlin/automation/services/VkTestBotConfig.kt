package automation.services

import com.vk.api.sdk.client.TransportClient
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VkTestBotConfig(
    @Value("\${forwarder.at.vk.token}") private val vkToken: String,
    @Value("\${forwarder.at.vk.user-id}") private val vkUserId: Long
) {

    @Bean
    fun vkApiClient(): VkApiClient {
        val transportClient: TransportClient = HttpTransportClient()
        return VkApiClient(transportClient)
    }

    @Bean
    fun userActor(): UserActor {
        return UserActor(vkUserId, vkToken)
    }
}