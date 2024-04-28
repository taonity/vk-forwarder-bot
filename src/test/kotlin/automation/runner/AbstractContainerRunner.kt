package automation.runner

import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.lifecycle.Startables
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils
import java.io.File
import java.nio.file.Paths
import java.time.Duration

abstract class AbstractContainerRunner {
    // TODO: Without this test db beans are being created without container being riced
    @Test
    fun dummyTest() {
    }

    companion object {
        private var ENVIRONMENT: DockerComposeContainer<*>? = null
        private var log: Logger = LoggerFactory.getLogger("automation-tests")

        init {
            ENVIRONMENT = if (SystemUtils.IS_OS_WINDOWS) {
                DockerComposeContainer(composeFile)
                    .withLocalCompose(true)
                    .withOptions("--compatibility")
            } else if (SystemUtils.IS_OS_UNIX) {
                DockerComposeContainer(composeFile).withLocalCompose(true)
            } else {
                throw RuntimeException(String.format("Unknown os encountered: %s", SystemUtils.OS_NAME))
            }
            ENVIRONMENT!!
                .withLogConsumer(
                    "app", Slf4jLogConsumer(log).withPrefix("app-1").withSeparateOutputStreams()
                )
                .withLogConsumer(
                    "db", Slf4jLogConsumer(log).withPrefix("db-1").withSeparateOutputStreams()
                )
                .waitingFor("app", Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(50)))
            Startables.deepStart(ENVIRONMENT).join()
        }

        private val composeFile: File
            get() {
                val file = Paths.get("target/docker/test/docker-compose.yml").toFile()
                log.info("Trying to open compose file with path: {}", file.absolutePath)
                return file
            }
    }
}
