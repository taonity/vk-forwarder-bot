package org.taonity.vkforwarderbot

import org.junit.jupiter.api.Test


class CacheServiceTest {

    @Test
    fun listMp4FilesInCache() {
        val cacheService = CacheService("cache")
        println(cacheService.listMp4FilesInCache())
    }
}