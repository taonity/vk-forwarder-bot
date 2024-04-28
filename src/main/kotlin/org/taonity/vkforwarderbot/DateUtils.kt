package org.taonity.vkforwarderbot

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

private val ZINE_ID = ZoneId.of("UTC")


class DateUtils {
    companion object {
        fun epochMilliToLocalDateTime(lastPostEpochMilli: Int): LocalDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochSecond(lastPostEpochMilli.toLong()), TimeZone.getTimeZone(ZINE_ID).toZoneId())

        fun getTimeNow(): LocalDateTime = LocalDateTime.now(TimeZone.getTimeZone(ZINE_ID).toZoneId())
    }
}