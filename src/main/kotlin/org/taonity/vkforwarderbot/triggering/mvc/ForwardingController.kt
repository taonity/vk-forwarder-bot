package org.taonity.vkforwarderbot.triggering.mvc

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.taonity.vkforwarderbot.forwarding.ForwardingService

private val LOGGER = KotlinLogging.logger {}

@CrossOrigin(origins = ["http://localhost:9015"])
@RestController
class ForwardingController (
    private val forwardingService: ForwardingService
) {
    @GetMapping("/forward")
    fun forward() : ResponseEntity<Unit> {
        LOGGER.debug { "/forward request received and approved" }

        val forwardingHaveBeenStarted = forwardingService.forwardAsync()

        if (forwardingHaveBeenStarted) {
            return ResponseEntity(HttpStatus.OK)
        }

        return ResponseEntity(HttpStatus.ACCEPTED)
    }

}