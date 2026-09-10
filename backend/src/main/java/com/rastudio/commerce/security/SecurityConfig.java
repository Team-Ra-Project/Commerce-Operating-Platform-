package com.rastudio.commerce.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain chain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Qualifier("corsConfigurationSource")
            CorsConfigurationSource corsSource) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                (request, response, authException) -> {
                                    response.setStatus(401);
                                    response.setContentType(
                                            "application/json");
                                    response.getWriter().write(
                                            "{\"success\":false,"
                                                    + "\"message\":\"Authentication required\","
                                                    + "\"errorCode\":\"UNAUTHENTICATED\"}");
                                })

                        .accessDeniedHandler(
                                (request, response, accessDeniedException) -> {
                                    response.setStatus(403);
                                    response.setContentType(
                                            "application/json");
                                    response.getWriter().write(
                                            "{\"success\":false,"
                                                    + "\"message\":\"You are not authorized to perform this action\","
                                                    + "\"errorCode\":\"FORBIDDEN\"}");
                                }))

                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**")
                        .permitAll()

                        .requestMatchers("/api/health")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/uploads/**")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/login",
                                "/api/auth/refresh")
                        .permitAll()

                        .requestMatchers(
                                "/api/webhooks/whatsapp")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/email-tracking/**")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/users/invitation/*")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users/accept-invitation")
                        .permitAll()

                        .anyRequest()
                        .authenticated())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors-origins}") String origins) {

        CorsConfiguration configuration =
                new CorsConfiguration();

        List<String> patterns =
                origins == null || origins.isBlank()
                        ? List.of("*")
                        : Arrays.stream(origins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toList();

        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration);

        return source;
    }
}