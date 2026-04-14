package com.example.socialmedia_poc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionTokenAuthFilter sessionTokenAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(SessionTokenAuthFilter sessionTokenAuthFilter,
                          RateLimitFilter rateLimitFilter) {
        this.sessionTokenAuthFilter = sessionTokenAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                // Public auth endpoints
                .antMatchers("/v1/users/register", "/v1/users/login", "/v1/users/google-login", "/v1/users/session/validate", "/v1/users/session/**").permitAll()
                // Public config endpoint
                .antMatchers("/v1/config/public", "/v1/config/interests").permitAll()
                // Public health check and static resources
                .antMatchers("/hello", "/", "/*.html", "/css/**", "/js/**").permitAll()
                // Public legacy/utility endpoints
                .antMatchers("/v1/flux/**").permitAll()
                // Admin-only endpoints
                .antMatchers("/v1/admin/**").hasRole("ADMIN")
                .antMatchers("/v1/playground/**").hasRole("ADMIN")
                // All other API endpoints require authentication
                .antMatchers("/v1/**").authenticated()
                // Everything else (e.g. static fallback) is open
                .anyRequest().permitAll()
            .and()
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(sessionTokenAuthFilter, RateLimitFilter.class);

        return http.build();
    }
}
