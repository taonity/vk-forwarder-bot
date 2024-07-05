package org.taonity.vkforwarderbot.forwarding

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.CacheService
import org.taonity.vkforwarderbot.vk.VkGroupDetailsEntity
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository

private val LOGGER = KotlinLogging.logger {}

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

    companion object {
        private var forwardingLocked = false

        private fun runForwardingWithLock(runnable: Runnable): Boolean {
            if (forwardingLocked) {
                LOGGER.debug { "Forwarding is locked due to it's execution now. The new forwarding is skipped" }
                return false
            }

            try {
                forwardingLocked = true
                runnable.run()
            } finally {
                forwardingLocked = false
            }

            return true
        }
    }

    fun forwardAsync(): Boolean {
        return runForwardingWithLock {
            Thread { forward() }.start()
        }
    }

    fun forwardSync(): Boolean {
        return runForwardingWithLock {
            forward()
        }
    }


    private fun forward() {
        LOGGER.debug { "Forwarding started" }

        logDisabledFeatures()

        cacheService.createCacheDirIfMissing()
        val vkBotGroupDetailsEntities = vkGroupDetailsRepository.findAll().toList()
        if (vkBotGroupDetailsEntities.isEmpty()) {
            LOGGER.debug { "No groups to forward" }
            return
        }

        for (vkBotGroupDetailsEntity in vkBotGroupDetailsEntities) {
            forwardGroupContent(vkBotGroupDetailsEntity)
        }

        LOGGER.debug { "Forwarding finished" }
    }

    private fun logDisabledFeatures() {
        if (!postsEnabled) {
            LOGGER.debug { "Posts are disabled" }
        }
        if (!storiesEnabled) {
            LOGGER.debug { "Stories are disabled" }
        }
    }

    private fun forwardGroupContent(vkBotGroupDetailsEntity: VkGroupDetailsEntity) {
        LOGGER.debug { "About to forward from vk group ${vkBotGroupDetailsEntity.vkGroupId} to tg channel ${vkBotGroupDetailsEntity.tgChannelId}" }
        try {
            forwardGroupContentIfEnabled(vkBotGroupDetailsEntity)
        } catch (e: Exception) {
            LOGGER.error(e) { "Error occurred while forwarding process" }
            cacheService.clearCache()
        }
        LOGGER.debug { "Finish forwarding for the group" }
    }

    private fun forwardGroupContentIfEnabled(vkBotGroupDetailsEntity: VkGroupDetailsEntity) {
        if (postsEnabled) {
            if (postsWhitelist.isEmpty() || postsWhitelist.contains(vkBotGroupDetailsEntity.vkGroupId)) {
                postForwardingService.forwardPosts(vkBotGroupDetailsEntity)
            } else {
                LOGGER.debug { "Posts for group ${vkBotGroupDetailsEntity.vkGroupId} are disabled" }
            }
        }
        if (storiesEnabled) {
            if (storiesWhitelist.isEmpty() || storiesWhitelist.contains(vkBotGroupDetailsEntity.vkGroupId)) {
                storyForwardingService.forwardStories(vkBotGroupDetailsEntity)
            } else {
                LOGGER.debug { "Stories for group ${vkBotGroupDetailsEntity.vkGroupId} are disabled" }
            }
        }
    }
}