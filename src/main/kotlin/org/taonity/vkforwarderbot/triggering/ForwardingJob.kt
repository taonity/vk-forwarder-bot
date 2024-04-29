package org.taonity.vkforwarderbot.triggering

import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.taonity.vkforwarderbot.forwarding.ForwardingService
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

@Component
class ForwardingJob (
    private val forwardingService: ForwardingService
) {
    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    fun startJob() {
        LOGGER.debug { "Job started" }
        forwardingService.forwardSync()
    }
}
