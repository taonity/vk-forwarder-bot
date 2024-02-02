package org.taonity.vkforwarderbot.forwarding

import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.CacheService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Component
class ForwardingService (
    private val vkGroupDetailsRepository: VkGroupDetailsRepository,
    private val postForwardingService: PostForwardingService,
    private val storyForwardingService: StoryForwardingService,
    private val cacheService: CacheService,
) {

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    fun forwardGroupsContent() {
        logger.debug { "Forwarding job started" }

        cacheService.createCacheDirIfMissing()
        val vkBotGroupDetailsEntities = vkGroupDetailsRepository.findAll().toList()
        if (vkBotGroupDetailsEntities.isEmpty()) {
            logger.debug { "No groups to forward" }
            return
        }

        for (vkBotGroupDetailsEntity in vkBotGroupDetailsEntities) {
            forwardGroupContent(vkBotGroupDetailsEntity)
        }

        logger.debug { "Forwarding job finished" }
    }

    private fun forwardGroupContent(vkBotGroupDetailsEntity: VkGroupDetailsEntity) {
        logger.debug { "About to forward from vk group ${vkBotGroupDetailsEntity.vkGroupId} to tg channel ${vkBotGroupDetailsEntity.tgChannelId}" }
        try {
            postForwardingService.forwardPosts(vkBotGroupDetailsEntity)
            storyForwardingService.forwardStories(vkBotGroupDetailsEntity)
        } catch (e: Exception) {
            logger.error { "Error occurred while forwarding process" }
            cacheService.clearCache()
            e.printStackTrace()
        }
        logger.debug { "Finish forwarding for the group" }
    }
}