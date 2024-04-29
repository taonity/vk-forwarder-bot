package automation.steps

import automation.utils.DbTablePrinter
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

class DbStepDefinitions (
    private val jdbcTemplate: JdbcTemplate,
    private val dbTablePrinter: DbTablePrinter,
    @Value("\${forwarder.testing.vk.group-id}") private val vkGroupId: Int,
    @Value("\${forwarder.testing.tg.channel-id}") private val tgChannelId: String,
) {
    @Before
    @After
    fun clearClearTgMessageCache() {
        jdbcTemplate.update("TRUNCATE TABLE  vk_group_details")
    }

    @Then("User inserts VK group details into DB")
    fun insertVkGroupDetailsIntoDB() {
        jdbcTemplate.update(
            "INSERT INTO vk_group_details (vk_group_id, tg_channel_id) VALUES (?, ?)",
            vkGroupId,
            tgChannelId,
        )
    }

    @Then("VK group timestamps have been updated")
    fun userChannelDataPresentInDb() {
        val quantityOfUserData: Int = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vk_group_details "
                    + "WHERE vk_group_id = ? "
                    + "AND last_forwarded_post_date_time = ? "
                    + "AND last_forwarded_story_date_time is null "
                    + "AND tg_channel_id = ?",
            Int::class.java,
            vkGroupId,
            parseDate("2024-04-27T17:23:11.0"),
            tgChannelId
        )

        Assertions.assertThat(quantityOfUserData)
            .overridingErrorMessage(makeErrorMessage("vk_group_details", 1))
            .isEqualTo(1)
    }

    private fun parseDate(date: String) : LocalDateTime? {
        return if (date == "NULL") {
            null
        } else {
            LocalDateTime.parse(date)
        }
    }

    private fun makeErrorMessage(tableName: String, expectedResult: Int): String {
        val tableString: String = dbTablePrinter.print(tableName)
        return String.format("Expected %s. Table looks like:\n%s", expectedResult, tableString)
    }
}
