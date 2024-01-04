package tmp;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

public class TTest {

    @Test
    public void test() {
        System.out.println(LocalDateTime.ofInstant(Instant.ofEpochSecond(1703535120L), TimeZone.getTimeZone(ZoneId.of("Europe/Chisinau")).toZoneId()));
    }
}
