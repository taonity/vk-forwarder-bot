package org.taonity.vkforwarderbot.vk

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface VkGroupDetailsRepository : CrudRepository<VkGroupDetailsEntity, Long> {
    fun findByGroupId(groupId: Long): VkGroupDetailsEntity?
}