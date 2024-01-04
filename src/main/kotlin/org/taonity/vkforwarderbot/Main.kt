package org.taonity.vkforwarderbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
open class Main

fun main(args: Array<String>) {
    runApplication<Main>(*args)
}