package cl.colegio.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración CORS global para el API Gateway (WebFlux/Reactivo).
 *
 * Permite que el frontend (Vite en :5173, :3000, ALB, o cualquier
 * IP dinámica de laboratorio AWS Academy) se comunique con el gateway.
 *
 * Usa allowedOriginPatterns en lugar de allowedOrigins para poder
 * combinar wildcard con allowCredentials=true.
 */
@Configuration
public class CorsGlobalConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        var config = new CorsConfiguration();
        // Permite cualquier origen — necesario para IPs dinámicas de AWS Academy
        // y dominio del ALB que cambia en cada laboratorio
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
