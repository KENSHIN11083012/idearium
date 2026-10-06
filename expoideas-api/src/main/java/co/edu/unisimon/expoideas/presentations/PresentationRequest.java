package co.edu.unisimon.expoideas.presentations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Programar o cambiar la sustentación de un proyecto.
 *
 * @param startsAt fecha y hora local de Bogotá, p. ej. "2026-11-20T09:30"
 */
public record PresentationRequest(
        @NotNull(message = "La fecha y la hora son obligatorias")
        LocalDateTime startsAt,

        @NotBlank(message = "El lugar es obligatorio")
        @Size(max = 150, message = "El lugar no puede exceder 150 caracteres")
        String place,

        @Size(max = 500, message = "Las indicaciones no pueden exceder 500 caracteres")
        String notes) {}
