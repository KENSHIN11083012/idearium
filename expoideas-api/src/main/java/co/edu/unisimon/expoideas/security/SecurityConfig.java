package co.edu.unisimon.expoideas.security;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Quién puede llamar a qué. La API es stateless: cada petición trae su token y
 * los permisos se leen de la BD. Las reglas finas (a quién puede gestionar cada
 * rol) las aplican los servicios.
 */
@Configuration
public class SecurityConfig {

    /** Estructura académica: la escribe solo el administrador. */
    private static final String[] INSTITUTIONAL_CATALOGS = {
        "/api/v1/campuses/**", "/api/v1/faculties/**", "/api/v1/academic-programs/**"
    };

    /** Clasificación de proyectos: la escriben MacondoLab y el administrador. */
    private static final String[] CLASSIFICATION_CATALOGS = {
        "/api/v1/sectors/**", "/api/v1/keywords/**", "/api/v1/prototype-types/**"
    };

    /** Ediciones de la Expo: las escribe MacondoLab; las fechas son públicas. */
    private static final String EDITIONS = "/api/v1/editions/**";

    /** Entregables que pide cada cátedra: los escribe MacondoLab; leerlos pide sesión. */
    private static final String DELIVERABLE_TYPES = "/api/v1/deliverable-types/**";

    /** Descarga de archivos: los públicos no piden sesión; los privados los autoriza FileService. */
    private static final String FILES = "/api/v1/files/**";

    /** Sustentaciones: la gestión programa y consulta la agenda; cada equipo ve la suya por su proyecto. */
    private static final String PRESENTATION = "/api/v1/projects/*/presentation";

    private static final String PRESENTATIONS = "/api/v1/presentations";

    /** Jurados de un proyecto: los asigna y consulta la gestión. Cada jurado ve los suyos en /jury/projects. */
    private static final String JURORS = "/api/v1/projects/*/jurors/**";

    /** Recordatorio por correo a los jurados con evaluaciones pendientes: lo manda la gestión. */
    private static final String EVALUATION_REMINDERS = "/api/v1/evaluations/reminders";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final HandlerExceptionResolver exceptionResolver;
    private final ExpoideasProperties properties;

    public SecurityConfig(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            ExpoideasProperties properties) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.exceptionResolver = exceptionResolver;
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Token en la cabecera, sin cookies ni sesión de servidor: CSRF no aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Gestión de cuentas: MacondoLab y administradores; eliminar, solo el administrador.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/users/**")
                        .hasRole("ADMIN")
                        // El rastro de auditoría recoge también lo que hace MacondoLab, y el estado de la
                        // instalación es cosa de quien la opera: solo los lee el administrador.
                        .requestMatchers("/api/v1/admin/audit/**", "/api/v1/admin/status/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("MACONDOLAB")

                        // Escritura de catálogos.
                        .requestMatchers(HttpMethod.POST, INSTITUTIONAL_CATALOGS)
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, INSTITUTIONAL_CATALOGS)
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, CLASSIFICATION_CATALOGS)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.PUT, CLASSIFICATION_CATALOGS)
                        .hasRole("MACONDOLAB")

                        // Ediciones de la Expo y su configuración.
                        .requestMatchers(HttpMethod.POST, EDITIONS)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.PUT, EDITIONS)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.DELETE, EDITIONS)
                        .hasRole("MACONDOLAB")

                        // Jurados: los asigna la gestión, y ella les recuerda lo pendiente.
                        .requestMatchers(JURORS)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.POST, EVALUATION_REMINDERS)
                        .hasRole("MACONDOLAB")

                        // Sustentaciones: las programa la gestión.
                        .requestMatchers(HttpMethod.PUT, PRESENTATION)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.DELETE, PRESENTATION)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.GET, PRESENTATIONS)
                        .hasRole("MACONDOLAB")

                        // Entregables que pide cada cátedra.
                        .requestMatchers(HttpMethod.POST, DELIVERABLE_TYPES)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.PUT, DELIVERABLE_TYPES)
                        .hasRole("MACONDOLAB")
                        .requestMatchers(HttpMethod.DELETE, DELIVERABLE_TYPES)
                        .hasRole("MACONDOLAB")

                        // Público: login, registro, documentación, salud y lecturas sin sesión.
                        .requestMatchers("/api/v1/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Salud para el orquestador: solo dice UP/DOWN, sin detalles.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Qué versión corre: solo eso, y Nginx no lo publica hacia fuera.
                        .requestMatchers(HttpMethod.GET, "/actuator/info")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, publicReads())
                        .permitAll()

                        // Todo lo demás, incluida la propia cuenta (/api/v1/users/me), pide sesión.
                        .anyRequest()
                        .authenticated())
                // Los rechazos ocurren en los filtros, antes de llegar a un controlador. Se
                // delegan al HandlerExceptionResolver para que GlobalExceptionHandler responda
                // con el mismo Problem Details que el resto de la API: 401 sin sesión válida,
                // 403 sin permiso.
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e))
                        .accessDeniedHandler((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e)))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userDetailsService, exceptionResolver),
                        UsernamePasswordAuthenticationFilter.class)
                // Con la sesión ya resuelta: bloquea todo menos el primer ingreso si está pendiente.
                .addFilterAfter(new OnboardingFilter(exceptionResolver, publicReads()), JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * GET que no piden sesión: los catálogos (los usa el registro), las ediciones
     * (las anuncia la portada) y los archivos públicos.
     */
    private static String[] publicReads() {
        return Stream.of(
                        Stream.of(INSTITUTIONAL_CATALOGS),
                        Stream.of(CLASSIFICATION_CATALOGS),
                        Stream.of(EDITIONS),
                        Stream.of(FILES))
                .flatMap(paths -> paths)
                .toArray(String[]::new);
    }

    /**
     * ROLE_ADMIN incluye todo lo de ROLE_MACONDOLAB: cada regla se escribe con el
     * rol mínimo que la permite. authorizeHttpRequests toma este bean solo.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN")
                .implies("MACONDOLAB")
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** El frontend en otro origen (desarrollo) o en el mismo detrás de Nginx (producción, lista vacía). */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins().stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
