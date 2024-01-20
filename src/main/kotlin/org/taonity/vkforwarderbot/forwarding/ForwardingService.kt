package org.taonity.vkforwarderbot.forwarding

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.CacheService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import java.util.Objects.isNull
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Component
class ForwardingService (
    private val vkGroupDetailsRepository: VkGroupDetailsRepository,
    private val postForwardingService: PostForwardingService,
    private val storyForwardingService: StoryForwardingService,
    private val cacheService: CacheService,
    @Value("\${forwarder.vk.group-id}") private val vkGroupId: Long
) {

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    fun forward() {
        logger.debug { "Forwarding job started" }

        val vkBotGroupDetails = saveGroupDetailsWithGroupIdIfNull()
        cacheService.createCacheDirIfMissing()

        try {
            postForwardingService.forwardPosts(vkBotGroupDetails)
            storyForwardingService.forward(vkBotGroupDetails)
        } catch (e: Exception) {
            logger.error { "Error occurred while forwarding process" }
            cacheService.clearCache()
            throw e
        }

        logger.debug { "Forwarding job finished" }
    }

    private fun saveGroupDetailsWithGroupIdIfNull() : VkGroupDetailsEntity {
        val vkBotGroupDetails = vkGroupDetailsRepository.findByGroupId(vkGroupId)
        if (isNull(vkBotGroupDetails)) {
            return vkGroupDetailsRepository.save(VkGroupDetailsEntity(groupId = vkGroupId))
        }
        return vkBotGroupDetails!!;
    }
}