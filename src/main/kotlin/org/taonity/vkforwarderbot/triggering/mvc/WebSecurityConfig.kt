package org.taonity.vkforwarderbot.triggering.mvc

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher


@Configuration
@EnableWebSecurity
class WebSecurityConfig(
    private val passwordFilter: PasswordFilter,
) {

    @Bean
    @Throws(Exception::class)
    protected fun securityFilterChain(http: HttpSecurity) : SecurityFilterChain {
        http
            .securityMatcher(NegatedRequestMatcher(AntPathRequestMatcher("/actuator/**")))
            .authorizeHttpRequests { requests ->
                requests.anyRequest().authenticated()
            }
            .addFilterAt(passwordFilter, BasicAuthenticationFilter::class.java)

        return http.build()
    }
}
