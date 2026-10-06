package co.edu.unisimon.expoideas.notifications;

import java.time.LocalDateTime;
import java.util.List;

/**
 * La gestión programó o cambió la sustentación de un proyecto.
 *
 * @param recipients  correos del equipo aceptado y del profesor del grupo
 * @param startsAt    fecha y hora local de Bogotá
 * @param rescheduled si ya tenía una fecha y se cambió
 */
public record PresentationScheduledEvent(
        List<String> recipients,
        String projectTitle,
        LocalDateTime startsAt,
        String place,
        String notes,
        boolean rescheduled) {}
