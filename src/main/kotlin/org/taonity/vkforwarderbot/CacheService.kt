package org.taonity.vkforwarderbot

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

@Component
class CacheService(
    @Value("\${forwarder.cache-dir-path}") val cacheDirPath: String,
    ) {

    fun createCacheDirIfMissing() {
        val cacheDir = File(cacheDirPath)
        if(!cacheDir.exists()) {
            cacheDir.mkdir()
        }
    }

    fun clearCache() {
        Files.list(Paths.get(cacheDirPath)).forEach { file ->
            file.deleteIfExists()
            logger.debug { "File deleted if existed ${file.fileName}" }
        }
    }

    fun listFilesInCache(): Set<String> {
        Files.list(Paths.get(cacheDirPath)).use {
                stream -> return stream
            .filter { file -> !Files.isDirectory(file) }
            .map { file -> file.pathString }
            .collect(Collectors.toSet())
        }
    }
}