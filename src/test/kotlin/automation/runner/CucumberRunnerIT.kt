package automation.runner

import automation.services.TgATBot
import automation.services.TgATBotConfig
import automation.utils.DbTablePrinter
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("automation/features")
@ConfigurationParameter(
    key = GLUE_PROPERTY_NAME,
    value = "automation"
)
// Is crucial for Junit 4 to 5 migration, for testcontainers
@CucumberContextConfiguration
@ExtendWith(SpringExtension::class)
@ActiveProfiles("at")
@ContextConfiguration(
    initializers = [ConfigDataApplicationContextInitializer::class],
    classes = [
        DataSourceAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class,
        DbTablePrinter::class,
        TgATBot::class,
        TgATBotConfig::class
    ]
)
@SpringBootTest
class CucumberRunnerIT : AbstractContainerRunner()