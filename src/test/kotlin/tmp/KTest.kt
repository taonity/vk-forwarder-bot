package tmp

import mu.KotlinLogging
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths


private val logger = KotlinLogging.logger {}

class KTest {
    @Test
    fun t2() {
        println(String(Files.readAllBytes(Paths.get("cookies.netscape"))))
    }

    @Test
    fun t3() {
        logger.info { RuntimeException("bruh").printStackTrace() }
    }
}