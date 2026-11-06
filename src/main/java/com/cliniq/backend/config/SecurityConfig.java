package com.cliniq.backend.config;

import com.cliniq.backend.service.AccessPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccessPolicy accessPolicy) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        .requestMatchers("/api/bookings/**", "/api/patients/me").authenticated()
                        .requestMatchers("/api/appointments/*/check-in", "/api/appointments/*/cancel",
                                "/api/appointments/*/queue-position").authenticated()
                        .requestMatchers("/api/appointments/walk-in", "/api/appointments/*/complete",
                                "/api/queues/**", "/api/staff/me/**")
                        .access((authentication, context) -> new AuthorizationDecision(accessPolicy.isStaff(authentication.get(), false)))
                        .requestMatchers("/api/appointments/*/check-in").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/departments", "/api/departments/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/staff", "/api/staff/**")
                        .access((authentication, context) -> new AuthorizationDecision(accessPolicy.isStaff(authentication.get(), false)))
                        .requestMatchers("/api/staff/register", "/api/staff", "/api/staff/**",
                                "/api/departments", "/api/departments/**")
                        .access((authentication, context) -> new AuthorizationDecision(accessPolicy.isStaff(authentication.get(), true)))
                        .requestMatchers("/api/appointments", "/api/appointments/**", "/api/schedules", "/api/schedules/**")
                        .access((authentication, context) -> new AuthorizationDecision(accessPolicy.isStaff(authentication.get(), false)))
                        .requestMatchers(HttpMethod.POST, "/api/patients").authenticated()
                        .requestMatchers("/api/patients", "/api/patients/**")
                        .access((authentication, context) -> new AuthorizationDecision(accessPolicy.isStaff(authentication.get(), false)))
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}