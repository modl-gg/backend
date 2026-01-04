package gg.modl.backend.rest.middleware;

import gg.modl.backend.auth.filter.SessionAuthenticationFilter;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RESTSecurityRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class V1SecurityConfig {
    private final SessionAuthenticationFilter sessionAuthenticationFilter;

    @Value("${modl.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c
                        .requestMatchers(RESTMappingV1.PREFIX_PUBLIC + "/**").permitAll()
                        .requestMatchers(RESTMappingV1.PANEL_AUTH + "/**").permitAll()
                        .requestMatchers(RESTMappingV1.ADMIN_AUTH + "/**").permitAll()
                        .requestMatchers(RESTMappingV1.PREFIX_ADMIN + "/**").hasAuthority(RESTSecurityRole.ADMIN)
                        .requestMatchers(RESTMappingV1.PREFIX_PANEL + "/**").hasAuthority(RESTSecurityRole.USER)
                        .requestMatchers(RESTMappingV1.PREFIX_MINECRAFT + "/**").hasAuthority(RESTSecurityRole.MINECRAFT)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Server-Domain", "X-API-Key"));
        configuration.setExposedHeaders(List.of("X-RateLimit-Remaining", "X-RateLimit-Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/**", configuration);
        return source;
    }
}
