package automation.services

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import org.springframework.stereotype.Component

@Component
class VkGroupService (
    private val vkApiClient: VkApiClient,
    private val userActor: UserActor
) {
    fun post() {
        vkApiClient.wall()
            .post(userActor)
            .ownerId(224746392)
            .message("test")
            .execute()
    }
}