package automation.runner

import automation.services.VkGroupService
import automation.services.VkTestBotConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ActiveProfiles("at")
@ContextConfiguration(
initializers = [ConfigDataApplicationContextInitializer::class],
classes = [
    VkGroupService::class,
    VkTestBotConfig::class
]
)
class CucumberRunnerIT (
   @Autowired private val vkGroupService: VkGroupService
) {
    @Test
    fun test1() {
        vkGroupService.post()
    }
}