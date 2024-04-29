package automation.steps

import automation.services.TgMessageCacheService
import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import io.cucumber.datatable.DataTable
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Then
import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.opentest4j.AssertionFailedError
import java.time.Duration

private val LOGGER = KotlinLogging.logger {}

class TgStepDefinition {

    @Before
    @After
    fun clearClearTgMessageCache() {
        TgMessageCacheService.clear()
    }

    @Then("Expected {int} messages are present in TG channel")
    fun insertVkGroupDetailsIntoDB(totalMessageQuantity: Int, table: DataTable) {
        val rows = table.asMaps(String::class.java, String::class.java)
        val retryPolicy = RetryPolicy.builder<Void>()
            .handle(AssertionFailedError::class.java)
            .withMaxRetries(60)
            .withDelay(Duration.ofSeconds(1))
            .build()

        Failsafe.with(retryPolicy).run { _ ->
            LOGGER.debug { "Try to assert TG message cache size" }
            assertThat(TgMessageCacheService.size()).isEqualTo(totalMessageQuantity)
        }

        rows.forEach { row ->
            val messageQuantity = row["messageQuantity"]!!.toLong()
            val messageType = WallpostAttachmentType.valueOf(row["messageType"]!!)

            assertThat(TgMessageCacheService.hasAdded(messageQuantity, messageType))
                .overridingErrorMessage { "Existing TG messages: ${TgMessageCacheService.getAsString()}" }
                .isTrue()

        }

    }
}