package online.wanan.xingchen.console;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
public class ConsoleSecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfTokenRepository(EffectiveConsoleConfiguration configuration) {
        var repository = new org.springframework.security.web.csrf.CookieCsrfTokenRepository();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Strict").secure(configuration.cookieSecure()));
        return repository;
    }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean AuthGenerationFilter authGenerationFilter(ConsoleSessionGenerationRegistry registry){return new AuthGenerationFilter(registry);}
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<AuthGenerationFilter> disableServletAuthFilter(AuthGenerationFilter filter){var bean=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);bean.setEnabled(false);return bean;}
    @Bean SecurityFilterChain consoleSecurity(HttpSecurity http,AuthGenerationFilter authGenerationFilter,
                                               org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository) throws Exception {
        var requestHandler = new CsrfTokenRequestAttributeHandler();
        http
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(requestHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/", "/index.html", "/assets/**", "/favicon.ico",
                    "/api/auth/login", "/api/auth/csrf", "/api/auth/initialize").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value()); response.setContentType("application/json");
                    String traceId = java.util.UUID.randomUUID().toString(); response.setHeader("X-Trace-Id", traceId);
                    response.getWriter().write("{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"请先登录\",\"traceId\":\"" + traceId + "\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value()); response.setContentType("application/json");
                    String traceId = java.util.UUID.randomUUID().toString(); response.setHeader("X-Trace-Id", traceId);
                    response.getWriter().write("{\"code\":\"REQUEST_FORBIDDEN\",\"message\":\"请求校验失败\",\"traceId\":\"" + traceId + "\"}");
                }))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .logout(logout -> logout.disable())
            .headers(headers -> headers
                .contentTypeOptions(options -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.disable()));
        http.addFilterAfter(authGenerationFilter,org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.class);
        return http.build();
    }
}
