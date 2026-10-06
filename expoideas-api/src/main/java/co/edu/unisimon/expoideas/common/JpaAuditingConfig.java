package co.edu.unisimon.expoideas.common;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Las fechas de creación y de modificación de las entidades ({@code @CreatedDate},
 * {@code @LastModifiedDate}) salen del reloj de {@link TimeConfig}: hora de
 * Colombia, sea cual sea la zona del servidor.
 *
 * <p>Va en su propia clase, y no en la de arranque, para que las pruebas que
 * levantan solo la capa web no intenten configurar JPA.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaAuditingConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
