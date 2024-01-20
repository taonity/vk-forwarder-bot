package org.taonity.vkforwarderbot.vk

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.objects.stories.FeedItem
import com.vk.api.sdk.objects.wall.WallItem
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.exceptions.VkUnexpectedResponseException

@Component
class VkBotService (
    private val vkApiClient: VkApiClient,
    private val userActor: UserActor
) {
    fun retrieveWallItems(vkGroupId: Long): MutableList<WallItem> = vkApiClient.wall()
        .get(userActor)
        .ownerId(vkGroupId)
        .execute()
        .items
        ?: throw VkUnexpectedResponseException("Failed to retrieve post items")

    fun retrieveFeedItems(vkGroupId: Long): MutableList<FeedItem> = vkApiClient.stories()
        .getV5113(userActor)
        .ownerId(vkGroupId)
        .execute()
        .items
        ?: throw VkUnexpectedResponseException("Failed to retrieve feed items")
}