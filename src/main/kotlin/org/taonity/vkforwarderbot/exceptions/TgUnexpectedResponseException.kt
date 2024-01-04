package org.taonity.vkforwarderbot.exceptions

class TgUnexpectedResponseException (message: String, throwable: Throwable) : RuntimeException(message, throwable) {
}