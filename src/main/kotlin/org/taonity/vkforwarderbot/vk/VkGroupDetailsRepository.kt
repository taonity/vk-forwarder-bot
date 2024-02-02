package org.taonity.vkforwarderbot.vk

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface VkGroupDetailsRepository : CrudRepository<VkGroupDetailsEntity, Long> {
    fun findByVkGroupId(groupId: Long): VkGroupDetailsEntity?
}