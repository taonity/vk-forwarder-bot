package org.taonity.vkforwarderbot.vk

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@SequenceGenerator(name = "default_generator", sequenceName = "vk_group_details_seq", allocationSize = 1)
@Table(name = "vk_group_details")
open class VkGroupDetailsEntity (
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "default_generator")
    open var id: Long?,
    open var groupId: Int?,
    open var lastForwardedPostDateTime: LocalDateTime?
) {
    constructor(groupId: Int, lastForwardedPostDateTime: LocalDateTime) : this (null, groupId, lastForwardedPostDateTime) {}

    override fun toString(): String {
        return "VkGroupDetails(id=$id, groupId=$groupId, lastForwardedPostDateTime=$lastForwardedPostDateTime)"
    }

}