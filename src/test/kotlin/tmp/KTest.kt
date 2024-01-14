package tmp

import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths


class KTest {
    @Test
    fun t2() {
        println(String(Files.readAllBytes(Paths.get("cookies.netscape"))))
    }
}