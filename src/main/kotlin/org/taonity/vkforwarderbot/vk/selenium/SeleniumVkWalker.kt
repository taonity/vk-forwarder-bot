package org.taonity.vkforwarderbot.vk.selenium

import com.vk.api.sdk.objects.stories.Story
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.openqa.selenium.*
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.FirefoxProfile
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.Wait
import java.io.File
import java.time.Duration

private val LOGGER = KotlinLogging.logger {}

class SeleniumVkWalker (
    private val vkUsername: String,
    private val vkPassword: String,
    private val cacheDirPath: String,
    private val browserLogFileEnabled: Boolean,
    private val tmpDirCleaningEnabled: Boolean
) {
    private lateinit var driver: WebDriver
    private lateinit var wait: Wait<WebDriver>
    fun loginIntoVk() {
        driver = buildFirefoxDriver()
        wait = buildFluentWait()

        LOGGER.debug { "Start selenium vk session" }
        driver.get("https://vk.com/")
        enterPhoneNumber()
        pressSignInButton()
        enterPassword()
        enterVkUiButton()
        waitForTopProfileLinkElementToLoad()
    }

    fun downloadStoryVideosInCache(stories: List<Story>) {
        StoryVideoDownloader(driver, wait, cacheDirPath).downloadStoryVideosInCache(stories)
    }

    fun quit() {
        driver.quit()
        LOGGER.debug { "Driver have been quit" }
        cleanTmpDirIfEnabled()

    }

    private fun cleanTmpDirIfEnabled() {
        if (tmpDirCleaningEnabled) {
            FileUtils.cleanDirectory(File("/tmp"))
            LOGGER.debug { "/tmp dir have been cleaned" }
        } else {
            LOGGER.debug { "/tmp dir cleaning disabled" }
        }
    }

    private fun buildFirefoxDriver(): WebDriver {
        val firefoxProfile = FirefoxProfile()
        firefoxProfile.setPreference("browser.download.dir", File(cacheDirPath).absolutePath)
        firefoxProfile.setPreference("browser.download.folderList", 2)
        val firefoxOptions = FirefoxOptions()
        firefoxOptions.setPageLoadStrategy(PageLoadStrategy.NONE)
        firefoxOptions.setProfile(firefoxProfile)
        firefoxOptions.addArguments("-headless", "--window-size=1280,720")
        firefoxOptions.addArguments("--start-maximized")
        firefoxOptions.addArguments("--disable-infobars")
        firefoxOptions.addArguments("--disable-extensions")
        firefoxOptions.addArguments("--no-sandbox")
        firefoxOptions.addArguments("--disable-application-cache")
        firefoxOptions.addArguments("--disable-gpu")
        firefoxOptions.addArguments("--disable-dev-shm-usage")
        if (!browserLogFileEnabled) {
            System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null")
        }
        return FirefoxDriver(firefoxOptions)
    }

    private fun buildFluentWait(): FluentWait<WebDriver> =
        FluentWait(driver)
            .withTimeout(Duration.ofSeconds(120))
            .pollingEvery(Duration.ofSeconds(3))
            .ignoring(NoSuchElementException::class.java, ElementNotInteractableException::class.java)

    private fun enterPhoneNumber() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.id("index_email"))
                .sendKeys(vkUsername)
            true
        }
        LOGGER.debug { "Phone number entered" }
    }

    private fun pressSignInButton() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.cssSelector(".VkIdForm__signInButton"))
                .click()
            true
        }
        LOGGER.debug { "Sign in button pressed" }
    }

    private fun enterPassword() {
        wait.until { d: WebDriver? ->
            val passwordInput = d!!.findElement(By.name("password"))
            passwordInput.click()
            passwordInput.sendKeys(vkPassword)
            true
        }
        LOGGER.debug { "Password entered" }
    }

    private fun enterVkUiButton() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.cssSelector(".vkuiButton"))
                .click()
            true
        }
        LOGGER.debug { "Vk UI button pressed" }
    }

    private fun waitForTopProfileLinkElementToLoad() {
        wait.until { d: WebDriver? ->
            d!!.findElement(By.id("top_profile_link"))
            true
        }
        LOGGER.debug { "Top profile link loaded" }
    }


}