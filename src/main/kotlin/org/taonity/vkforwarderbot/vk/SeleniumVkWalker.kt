package org.taonity.vkforwarderbot.vk

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
        val usedSrcSet = HashSet<String>()
        var storyDownloadFails = 0
        for (story in stories) {
            val storyLink = "https://vk.com/feed?w=story${story.ownerId}_${story.id}%2Ffeed"
            logger.debug { "About to go to $storyLink" }
            logger.debug { "Detail link is ${story.link}" }
            driver.get(storyLink)
            logger.debug { "Page loaded" }
            try {
                val videoUrl: String? = retrieveVideoUrl(wait, usedSrcSet)
                logger.debug { "Video URL retrieved $videoUrl" }

                downloadVideo(videoUrl, driver, usedSrcSet)
                logger.debug { "Video downloaded" }
            } catch (e: TimeoutException) {
                storyDownloadFails++
                if(storyDownloadFails == 3) {
                    throw e
                }
                logger.debug { "Failed to download story, fail $storyDownloadFails" }
            }

        }
        Thread.sleep(5000)
    }

    fun quit() {
        driver.quit();
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
        FluentWait<WebDriver>(driver)
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


    private fun retrieveVideoUrl(
        wait: Wait<WebDriver>,
        usedSrcSet: HashSet<String>
    ): String? {
        var videoUrl: String? = null
        wait.until { d: WebDriver? ->
            val shortElement = d!!.findElement(By.cssSelector("video"))
            videoUrl = shortElement.getAttribute("src")
            !usedSrcSet.contains(videoUrl)
        }
        return videoUrl
    }

    private fun downloadVideo(
        videoUrl: String?,
        driver: WebDriver,
        usedSrcSet: HashSet<String>
    ) {
        if (!videoUrl.isNullOrBlank()) {
            driver.get(videoUrl)
            Thread.sleep(5000)
            usedSrcSet.add(videoUrl)
        }
    }
}