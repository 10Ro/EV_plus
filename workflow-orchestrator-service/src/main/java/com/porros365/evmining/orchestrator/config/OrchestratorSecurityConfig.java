package com.porros365.evmining.orchestrator.config;

import com.porros365.evmining.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
public class OrchestratorSecurityConfig {

    @Value("${security.jwt.secret:Porros365EvPlusClvMiningDistributedPlatformSecretKey2026_HMAC_256!}")
    private String jwtSecret;

    @Value("${security.jwt.enforce:false}")
    private boolean enforceJwt;

    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(jwtSecret);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public OncePerRequestFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {

                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7).trim();
                    try {
                        Claims claims = tokenProvider.validateAndParseClaims(token);
                        String username = claims.getSubject();
                        String role = claims.get("role", String.class);
                        if (username != null && role != null) {
                            String formattedRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,
                                    List.of(new SimpleGrantedAuthority(formattedRole))
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    } catch (Exception ex) {
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"INVALID_JWT_TOKEN\",\"message\":\"Invalid or expired JWT token\"}");
                        return;
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OncePerRequestFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/**", "/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll();
                    auth.requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN");
                    if (enforceJwt) {
                        auth.anyRequest().authenticated();
                    } else {
                        auth.anyRequest().permitAll();
                    }
                });
        return http.build();
    }
}
