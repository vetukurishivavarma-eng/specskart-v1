package com.specskart.config;

import com.specskart.auth.JwtAuthFilter;
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

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AppProperties props) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource(props)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) ->
                    res.sendError(401, "Authentication required")))
            .authorizeHttpRequests(auth -> auth
                // A denied request is forwarded to /error; if that's secured too, a staff "403 not allowed"
                // comes back as "401 not signed in" and the POS app logs them out.
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers("/api/frame-finder/**").permitAll()
                .requestMatchers("/api/frame-recommendations/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/sim/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Staff work leads (inbox, notes, status, WhatsApp replies); deleting one is permanent -> owner only.
                .requestMatchers(HttpMethod.DELETE, "/api/admin/leads/**").hasRole("ADMIN")
                // The whole database -- customers' numbers included -- as one download.
                .requestMatchers("/api/admin/backup", "/api/admin/backup/**").hasRole("ADMIN")
                // Supplier invoices are buying prices; staff (AGENT) must never see cost.
                .requestMatchers("/api/admin/pos/invoices", "/api/admin/pos/invoices/**").hasRole("ADMIN")
                // Shop price overrides and the staff list (logins/emails) are the owner's call only.
                .requestMatchers("/api/admin/pos/stores/*/prices", "/api/admin/pos/stores/*/prices/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/users", "/api/admin/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyRole("ADMIN", "AGENT")
                // Staff add frames (photo, code, brand, price, shape) from the POS app; only an
                // admin removes one.
                .requestMatchers(HttpMethod.DELETE, "/api/admin/catalog/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/catalog/products", "/api/admin/catalog/products/**").hasAnyRole("ADMIN", "AGENT")
                .requestMatchers("/api/admin/campaigns/**", "/api/admin/recommendations/**", "/api/admin/users/**",
                        "/api/admin/catalog/**", "/api/admin/faqs/**", "/api/admin/lens-pricing/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "AGENT")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsSource(AppProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.corsOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
