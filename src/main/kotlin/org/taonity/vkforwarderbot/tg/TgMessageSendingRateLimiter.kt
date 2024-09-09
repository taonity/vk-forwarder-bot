package org.taonity.vkforwarderbot.tg

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val LOGGER = KotlinLogging.logger {}
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
            LOGGER.debug { "Rate limited, about to sleep for ${totalSleepTime / 1000.0} secs" }
            Thread.sleep(totalSleepTime)
        }
    }

    private fun calculateAndUpdateTokensLeft(messageToPermit: Int): Int {
        val tokensLeft = messageTokenBucket - messageToPermit
        messageTokenBucket = tokensLeft
        return tokensLeft
    }

    private fun calculateTotalSleepTime(timeNow: LocalDateTime, tokensLeft: Int): Long {
        val millisToWaitForSecondsCycleEnd = getMillisFromNowUntilSecondsCycleEnd(timeNow)
        val millisLeftUntilTokensRefill = getMillisFromNowUntilNexRefillTime(tokensLeft, timeNow)
        return sumTotalSleepTime(millisToWaitForSecondsCycleEnd, millisLeftUntilTokensRefill)
    }

    private fun sumTotalSleepTime(millisToWaitForSecondsCycleEnd: Long?, millisLeftUntilTokensRefill: Long?): Long {
        var totalSleepTime = 0L
        if (millisToWaitForSecondsCycleEnd != null) {
            totalSleepTime += millisToWaitForSecondsCycleEnd
        }
        if (millisLeftUntilTokensRefill != null) {
            totalSleepTime += millisLeftUntilTokensRefill
        }
        return totalSleepTime
    }

    private fun getMillisFromNowUntilNexRefillTime(tokensLeft: Int, timeNow: LocalDateTime) =
        if (tokensLeft < 0) {
            val millisLeftUntilTokensRefill = ChronoUnit.MILLIS.between(timeNow, getNextTokenRefillTime())
            LOGGER.debug { "Not enough tokens, have to sleep for ${millisLeftUntilTokensRefill / 1000.0} secs" }
            millisLeftUntilTokensRefill
        } else {
            null
        }

    private fun getMillisFromNowUntilSecondsCycleEnd(timeNow: LocalDateTime) =
        if (getSecondCycleEndTime().isAfter(timeNow)) {
            val millisToWaitForSecondsCycleEnd = ChronoUnit.MILLIS.between(timeNow, getSecondCycleEndTime())
            LOGGER.debug { "Seconds cycle is active, have to sleep for ${millisToWaitForSecondsCycleEnd / 1000.0} secs" }
            millisToWaitForSecondsCycleEnd
        } else {
            null
        }

    private fun refillTokensNow(timeNow: LocalDateTime?) {
        messageTokenBucket = MESSAGE_PER_MINUTE
        lastRefillTime = timeNow
        LOGGER.debug { "Messages tokens have been refilled" }
    }

    fun acquireTokensAndRun(runnable: Runnable) {
        acquireTokensAndRun(1, runnable)
    }

    private fun getSecondCycleEndTime(): LocalDateTime = lastAcquiringTime.plusSeconds(lastTokensUsed)

    private fun getNextTokenRefillTime(): LocalDateTime = lastRefillTime.plusMinutes(1)
}