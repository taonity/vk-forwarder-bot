package automation.services

import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import org.taonity.vkforwarderbot.DateUtils


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
        fun hasAdded(messageQuantity: Long, messageType: WallpostAttachmentType) : Boolean {
            return messages.stream()
                .filter { message -> message.type == messageType }
                .count() == messageQuantity
        }

        fun getAsString(): String {
            return messages.toString()
        }
    }
}