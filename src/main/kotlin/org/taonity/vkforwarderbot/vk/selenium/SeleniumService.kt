package org.taonity.vkforwarderbot.vk.selenium

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.CacheService

@Component
class SeleniumService (
    private val cacheService: CacheService,
    @Value("\${forwarder.vk.username}") private val vkUsername: String,
    @Value("\${forwarder.vk.password}") private val vkPassword: String,
    @Value("\${forwarder.vk.selenium.browser-log-file-enabled}") private val browserLogFileEnabled: Boolean,
) {
    fun buildVkWalker() : SeleniumVkWalker {
        return SeleniumVkWalker(vkUsername, vkPassword, cacheService.cacheDirPath, browserLogFileEnabled)
    }
}