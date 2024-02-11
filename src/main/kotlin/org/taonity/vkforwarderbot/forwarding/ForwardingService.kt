package org.taonity.vkforwarderbot.forwarding

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${forwarder.posts.enabled:true}") val postsEnabled: Boolean,
    @Value("\${forwarder.posts.whitelist:}") val postsWhitelist: Set<Long>,
    @Value("\${forwarder.stories.enabled:true}") val storiesEnabled: Boolean,
    @Value("\${forwarder.stories.whitelist:}") val storiesWhitelist: Set<Long>,
    ) {
    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    fun forwardGroupsContent() {
        logger.debug { "Forwarding job started" }

        logDisabledFeatures()

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

    private fun logDisabledFeatures() {
        if (!postsEnabled) {
            logger.debug { "Posts are disabled" }
        }
        if (!storiesEnabled) {
            logger.debug { "Stories are disabled" }
        }
    }

    private fun forwardGroupContent(vkBotGroupDetailsEntity: VkGroupDetailsEntity) {
        logger.debug { "About to forward from vk group ${vkBotGroupDetailsEntity.vkGroupId} to tg channel ${vkBotGroupDetailsEntity.tgChannelId}" }
        try {
            forwardGroupContentIfEnabled(vkBotGroupDetailsEntity)
        } catch (e: Exception) {
            logger.error { "Error occurred while forwarding process: ${e.message}" }
            cacheService.clearCache()
        }
        logger.debug { "Finish forwarding for the group" }
    }

    private fun forwardGroupContentIfEnabled(vkBotGroupDetailsEntity: VkGroupDetailsEntity) {
        if (postsEnabled) {
            if (postsWhitelist.isEmpty() || postsWhitelist.contains(vkBotGroupDetailsEntity.vkGroupId)) {
                postForwardingService.forwardPosts(vkBotGroupDetailsEntity)
            } else {
                logger.debug { "Posts for group ${vkBotGroupDetailsEntity.vkGroupId} are disabled" }
            }
        }
        if (storiesEnabled) {
            if (storiesWhitelist.isEmpty() || storiesWhitelist.contains(vkBotGroupDetailsEntity.vkGroupId)) {
                storyForwardingService.forwardStories(vkBotGroupDetailsEntity)
            } else {
                logger.debug { "Stories for group ${vkBotGroupDetailsEntity.vkGroupId} are disabled" }
            }
        }
    }
}