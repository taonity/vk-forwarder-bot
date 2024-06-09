package org.taonity.vkforwarderbot

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.io.File

private val LOGGER = KotlinLogging.logger {}

@Component
class TmpDirService {
    fun removeSeleniumManagerDirs() {
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            val tmpDir = File("/tmp")

            if (tmpDir.isDirectory) {
                val files = tmpDir.listFiles() ?: return

                files.filter { it.isDirectory && it.name.startsWith("selenium-manager") }
                    .forEach { dir ->
                        LOGGER.debug { "Deleting directory: ${dir.name}" }
                        deleteRecursively(dir)
                    }
            }
        } else {
            LOGGER.debug { "Skip tmp folder cleanup on non-linux OS" }
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        return file.delete()
    }
}