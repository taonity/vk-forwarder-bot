package org.taonity.vkforwarderbot.triggering.mvc

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.http.HttpHeaders
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class PasswordFilter(
    @Value("\${forwarder.debug.manual-trigger-password}") private val manualTriggerPassword: String
) : OncePerRequestFilter() {

    //TODO: prevent this filter to fire on permitted requests
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val password = request.getHeader(HttpHeaders.AUTHORIZATION)

        if(password == manualTriggerPassword) {
            val usernamePasswordAuthenticationToken = UsernamePasswordAuthenticationToken(password, null, null)
            SecurityContextHolder.getContext().authentication = usernamePasswordAuthenticationToken
        }

        filterChain.doFilter(request, response)
    }
}