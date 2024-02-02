package org.taonity.vkforwarderbot.tg

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}
private const val MESSAGE_PER_MINUTE = 20

@Component
class TgMessageSendingRateLimiter {
    private var messageTokenBucket = 0
    private var lastRefillTime = LocalDateTime.MIN
    private var lastAcquiringTime = LocalDateTime.MIN
    private var lastTokensUsed = 0L

    fun acquireTokensAndRun(tokensToUse: Int, sendingMessagesRunnable: Runnable) {
        if(tokensToUse > MESSAGE_PER_MINUTE) {
            throw RuntimeException()
        }

        val timeNow = LocalDateTime.now()
        if(timeNow.isAfter(getNextTokenRefillTime())) {
            refillTokensNow(timeNow)
        }

        sleepIfRequired(tokensToUse, timeNow)

        sendingMessagesRunnable.run()

        lastAcquiringTime = LocalDateTime.now()
        lastTokensUsed = tokensToUse.toLong()
    }

    private fun sleepIfRequired(tokensToUse: Int, timeNow: LocalDateTime) {
        val tokensLeft = calculateAndUpdateTokensLeft(tokensToUse)
        val totalSleepTime = calculateTotalSleepTime(timeNow, tokensLeft)

        if (totalSleepTime != 0L) {
            logger.debug { "Rate limited, about to sleep for ${totalSleepTime / 1000} secs" }
            Thread.sleep(totalSleepTime)
        }
    }

    private fun calculateAndUpdateTokensLeft(messageToPermit: Int): Int {
        val tokensLeft = messageTokenBucket - messageToPermit
        messageTokenBucket = tokensLeft
        return tokensLeft
    }

    private fun calculateTotalSleepTime(timeNow: LocalDateTime, tokensLeft: Int): Long {
        val millisToWaitForSecondsCircleEnd = getMillisFromNowUntilSecondsCircleEnd(timeNow)
        val millisLeftUntilTokensRefill = getMillisFromNowUntilNexRefillTime(tokensLeft, timeNow)
        return sumTotalSleepTime(millisToWaitForSecondsCircleEnd, millisLeftUntilTokensRefill)
    }

    private fun sumTotalSleepTime(millisToWaitForSecondsCircleEnd: Long?, millisLeftUntilTokensRefill: Long?): Long {
        var totalSleepTime = 0L
        if (millisToWaitForSecondsCircleEnd != null) {
            totalSleepTime += millisToWaitForSecondsCircleEnd
        }
        if (millisLeftUntilTokensRefill != null) {
            totalSleepTime += millisLeftUntilTokensRefill
        }
        return totalSleepTime
    }

    private fun getMillisFromNowUntilNexRefillTime(tokensLeft: Int, timeNow: LocalDateTime) =
        if (tokensLeft < 0) {
            val millisLeftUntilTokensRefill = ChronoUnit.MILLIS.between(timeNow, getNextTokenRefillTime())
            logger.debug { "Not enough tokens, have to sleep for ${millisLeftUntilTokensRefill / 1000} secs" }
            millisLeftUntilTokensRefill
        } else {
            null
        }

    private fun getMillisFromNowUntilSecondsCircleEnd(timeNow: LocalDateTime) =
        if (getSecondCircleEndTime().isAfter(timeNow)) {
            val millisToWaitForSecondsCircleEnd = ChronoUnit.MILLIS.between(timeNow, getSecondCircleEndTime())
            logger.debug { "Seconds circle is active, have to sleep for ${millisToWaitForSecondsCircleEnd / 1000} secs" }
            millisToWaitForSecondsCircleEnd
        } else {
            null
        }

    private fun refillTokensNow(timeNow: LocalDateTime?) {
        messageTokenBucket = MESSAGE_PER_MINUTE
        lastRefillTime = timeNow
        logger.debug { "Messages tokens have been refilled" }
    }

    fun acquireTokensAndRun(runnable: Runnable) {
        acquireTokensAndRun(1, runnable)
    }

    private fun getSecondCircleEndTime(): LocalDateTime = lastAcquiringTime.plusSeconds(lastTokensUsed)

    private fun getNextTokenRefillTime(): LocalDateTime = lastRefillTime.plusMinutes(1)
}