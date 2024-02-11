package org.taonity.vkforwarderbot

import org.junit.jupiter.api.Test
import org.openqa.selenium.support.ui.FluentWait
import java.time.Duration


class CacheServiceTest {

    @Test
    fun listMp4FilesInCache() {
        val cacheService = CacheService("cache")
        println(cacheService.listMp4FilesInCache())
    }

    @Test
    fun pollingTest() {
        val fluentWait = FluentWait("tmp")
            .withTimeout(Duration.ofSeconds(5))
            .pollingEvery(Duration.ofSeconds(1))

        fluentWait.until { false }
    }
}