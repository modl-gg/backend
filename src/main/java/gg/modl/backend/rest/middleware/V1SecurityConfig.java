package gg.modl.backend.rest.middleware;

import gg.modl.backend.auth.filter.SessionAuthenticationFilter;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RESTSecurityRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class V1SecurityConfig {
    private final SessionAuthenticationFilter sessionAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c
                        .requestMatchers(RESTMappingV1.PREFIX_PUBLIC + "/**").permitAll()
                        .requestMatchers(RESTMappingV1.PANEL_AUTH + "/**").permitAll()
                        .requestMatchers(RESTMappingV1.PREFIX_ADMIN + "/**").hasRole(RESTSecurityRole.ADMIN)
                        .requestMatchers(RESTMappingV1.PREFIX_PANEL + "/**").hasRole(RESTSecurityRole.USER)
                        .requestMatchers(RESTMappingV1.PREFIX_MINECRAFT + "/**").hasRole(RESTSecurityRole.MINECRAFT)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }
}
