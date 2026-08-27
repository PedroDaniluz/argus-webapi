package com.codaxistech.argus.common;

import com.codaxistech.argus.device.DeviceFacade;
import com.codaxistech.argus.user.UserFacade;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Two schemes on separate chains: {@code /api/ingest/**} for devices via
 * {@code X-Device-Key}, everything else for users via JWT bearer.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_PATHS = {
            "/auth/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    @Order(1)
    SecurityFilterChain ingest(HttpSecurity http, DeviceFacade devices,
                               AuthenticationEntryPoint entryPoint,
                               AccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .securityMatcher("/api/ingest/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new DeviceAuthFilter(devices), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority(DeviceAuthFilter.ROLE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http, JwtDecoder jwtDecoder,
                            AuthenticationEntryPoint entryPoint,
                            AccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtService jwtService, UserFacade users,
                          @Value("${argus.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtService.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new IssuerValidator(issuer),
                new AccessTokenValidator(users)));
        return decoder;
    }

    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authorities);
        return converter;
    }

    private static List<GrantedAuthority> authorities(Jwt jwt) {
        String role = jwt.getClaimAsString(JwtService.CLAIM_ROLE);
        return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${argus.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns, not plain origins, so a wildcard works. No cookies, so credentials stay off.
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    AuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, authException) -> write(mapper, response,
                HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "missing or invalid credentials", request.getRequestURI());
    }

    @Bean
    AccessDeniedHandler apiAccessDeniedHandler(ObjectMapper mapper) {
        return (request, response, deniedException) -> write(mapper, response,
                HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "not allowed on this resource", request.getRequestURI());
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response,
                              int status, String error, String message, String path)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiError.of(status, error, message, path));
    }

    record IssuerValidator(String issuer) implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            // Not getIssuer(): it coerces to URL and throws when the issuer is not one.
            String actual = jwt.getClaimAsString(JwtClaimNames.ISS);
            return issuer.equals(actual)
                    ? OAuth2TokenValidatorResult.success()
                    : fail("invalid_issuer", "token issuer does not match");
        }
    }

    /** Rejects a refresh token used as an access token, and any stale token version. */
    record AccessTokenValidator(UserFacade users) implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            if (!JwtService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtService.CLAIM_TYPE))) {
                return fail("invalid_token_type", "this token cannot be used to call the API");
            }
            UUID userId;
            try {
                userId = UUID.fromString(String.valueOf(jwt.getSubject()));
            } catch (IllegalArgumentException e) {
                return fail("invalid_subject", "token subject is not a user id");
            }
            Integer current = users.currentTokenVersion(userId).orElse(null);
            if (current == null) {
                return fail("user_unavailable", "user does not exist or is disabled");
            }
            Object claimed = jwt.getClaim(JwtService.CLAIM_TOKEN_VERSION);
            if (!(claimed instanceof Number number) || number.intValue() != current) {
                return fail("token_revoked", "token revoked");
            }
            return OAuth2TokenValidatorResult.success();
        }
    }

    private static OAuth2TokenValidatorResult fail(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }
}
