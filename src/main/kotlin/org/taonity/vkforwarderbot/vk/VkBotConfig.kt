package org.taonity.vkforwarderbot.vk

import com.vk.api.sdk.client.TransportClient
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VkBotConfig(
    @Value("\${forwarder.vk.token}") private val vkToken: String,
    @Value("\${forwarder.vk.user-id}") private val vkUserId: Int
) {

    @Bean
    open fun vkApiClient(): VkApiClient {
        val transportClient: TransportClient = HttpTransportClient()
        return VkApiClient(transportClient)
    }

    @Bean
    open fun userActor(): UserActor {
        return UserActor(vkUserId, vkToken)
    }
}