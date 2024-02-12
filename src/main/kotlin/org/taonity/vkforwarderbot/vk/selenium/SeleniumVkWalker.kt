package org.taonity.vkforwarderbot.vk.selenium

import com.vk.api.sdk.objects.stories.Story
import mu.KotlinLogging
import org.openqa.selenium.*
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.FirefoxProfile
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.Wait
import java.io.File
import java.time.Duration

private val logger = KotlinLogging.logger {}

class SeleniumVkWalker (
    private val vkUsername: String,
    private val vkPassword: String,
    private val cacheDirPath: String,
    private val browserLogFileEnabled: Boolean
) {
    private lateinit var driver: WebDriver
    private lateinit var wait: Wait<WebDriver>
    fun loginIntoVk() {
        driver = buildFirefoxDriver()
        wait = buildFluentWait()

        logger.debug { "Start selenium vk session" }
        driver.get("https://vk.com/")
        enterPhoneNumber()
        pressSignInButton()
        enterPassword()
        enterVkUiButton()
        waitForTopProfileLinkElementToLoad()
    }

    fun downloadStoryVideosInCache(stories: MutableList<Story>) {
        StoryVideoDownloader(driver, wait, cacheDirPath).downloadStoryVideosInCache(stories)
    }

    fun quit() {
        driver.quit()
    }

    private fun buildFirefoxDriver(): WebDriver {
        val firefoxProfile = FirefoxProfile()
        firefoxProfile.setPreference("browser.download.dir", File(cacheDirPath).absolutePath)
        firefoxProfile.setPreference("browser.download.folderList", 2)
        val firefoxOptions = FirefoxOptions()
        firefoxOptions.setPageLoadStrategy(PageLoadStrategy.NONE)
        firefoxOptions.setProfile(firefoxProfile)
        firefoxOptions.addArguments("-headless", "--window-size=1920,1080")
        if (!browserLogFileEnabled) {
            System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null")
        }
        return FirefoxDriver(firefoxOptions)
    }

    private fun buildFluentWait(): FluentWait<WebDriver> =
        FluentWait(driver)
            .withTimeout(Duration.ofSeconds(20))
            .pollingEvery(Duration.ofSeconds(2))
            .ignoring(NoSuchElementException::class.java, ElementNotInteractableException::class.java)

    private fun enterPhoneNumber() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.id("index_email"))
                .sendKeys(vkUsername)
            true
        }
        logger.debug { "Phone number entered" }
    }

    private fun pressSignInButton() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.cssSelector(".VkIdForm__signInButton"))
                .click()
            true
        }
        logger.debug { "Sign in button pressed" }
    }

    private fun enterPassword() {
        wait.until { d: WebDriver? ->
            val passwordInput = d!!.findElement(By.name("password"))
            passwordInput.click()
            passwordInput.sendKeys(vkPassword)
            true
        }
        logger.debug { "Password entered" }
    }

    private fun enterVkUiButton() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.cssSelector(".vkuiButton"))
                .click()
            true
        }
        logger.debug { "Vk UI button pressed" }
    }

    private fun waitForTopProfileLinkElementToLoad() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.id("top_profile_link"))
            true
        }
        logger.debug { "Top profile link loaded" }
    }
}