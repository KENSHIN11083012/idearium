package co.edu.unisimon.expoideas.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API en /swagger-ui.html (apagada en el perfil prod). Por
 * defecto las rutas piden el token; las públicas lo quitan con
 * {@code @SecurityRequirements} vacío.
 */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Idearium API",
                        version = "v1",
                        description =
                                "Cuentas, perfiles, catálogos y archivos de Idearium, la plataforma de la Cátedra INNPRENDE"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
}
