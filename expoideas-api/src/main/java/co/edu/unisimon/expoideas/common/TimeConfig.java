package co.edu.unisimon.expoideas.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación. Los plazos (inscripciones, entregas) se comparan
 * siempre contra el día en Colombia, no contra la zona horaria del servidor, que
 * la fija TI. Va como bean para que las pruebas puedan fijar el día.
 */
@Configuration
public class TimeConfig {

    /** Colombia no cambia de hora, así que el día es el mismo todo el año. */
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
