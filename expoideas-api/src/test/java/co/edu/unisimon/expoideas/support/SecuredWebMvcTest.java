package co.edu.unisimon.expoideas.support;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.security.JwtService;
import co.edu.unisimon.expoideas.security.SecurityConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Slice web con la SecurityConfig real y sin base de datos: controladores,
 * filtros de seguridad y GlobalExceptionHandler. JwtService y UserDetailsService
 * van simulados; la sesión la ponen {@code @WithMockUser} o {@code user(...)}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import(SecurityConfig.class)
@EnableConfigurationProperties(ExpoideasProperties.class)
@TestPropertySource(properties = "expoideas.jwt.secret=" + TestData.JWT_SECRET)
@MockitoBean(types = {JwtService.class, UserDetailsService.class})
public @interface SecuredWebMvcTest {

    /** Controladores del slice. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
