package automation.services

import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import org.taonity.vkforwarderbot.DateUtils
import java.time.LocalDateTime

private const val RECENTLY_DEFINITION_IN_SECONDS = 10

class TgMessageCacheService {
    companion object {
        private val messages = ArrayList<TgTestMessage>()

        fun clear() {
            messages.clear()
        }
        fun add(date: Int, messageType: WallpostAttachmentType) {
            val localDateTime = DateUtils.epochMilliToLocalDateTime(date)
            messages.add(TgTestMessage(messageType, localDateTime))
        }
        fun size() : Int {
            return messages.size
        }
        fun hasAddedRecently(messageQuantity: Long, messageType: WallpostAttachmentType) : Boolean {
            return messages.stream()
                .filter { message -> message.type == messageType }
                .filter { messages -> messages.timestamp.isAfter(buildRecentlyDateBorder()) }
                .count() == messageQuantity
        }

        fun getAsString(): String {
            return messages.toString()
        }
        private fun buildRecentlyDateBorder(): LocalDateTime? =
            DateUtils.getTimeNow().minusSeconds(RECENTLY_DEFINITION_IN_SECONDS.toLong())
    }
}