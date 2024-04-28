package automation.services

import com.vk.api.sdk.objects.wall.WallpostAttachmentType
import java.time.LocalDateTime

class TgTestMessage (
    val type: WallpostAttachmentType,
    val timestamp: LocalDateTime
) {
    override fun toString(): String {
        return "TgTestMessage(type=$type, timestamp=$timestamp)"
    }
}
