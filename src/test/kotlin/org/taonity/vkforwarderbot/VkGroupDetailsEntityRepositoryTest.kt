package org.taonity.vkforwarderbot

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.annotation.Rollback
import org.taonity.vkforwarderbot.vk.VkGroupDetailsRepository
import java.time.LocalDateTime

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@Rollback(false)
class VkGroupDetailsEntityRepositoryTest constructor(
   @Autowired  private val vkGroupDetailsRepository: VkGroupDetailsRepository
) {
    @Test
    fun test() {
        val e = vkGroupDetailsRepository.findByGroupId(123)
        e?.lastForwardedPostDateTime = LocalDateTime.now()
        vkGroupDetailsRepository.save(e!!)
//        println(vkGroupDetailsRepository.findByGroupId(123L))
    }
}