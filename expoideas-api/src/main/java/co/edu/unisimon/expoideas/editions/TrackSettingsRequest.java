package co.edu.unisimon.expoideas.editions;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Configuración de una cátedra dentro de una edición. */
public record TrackSettingsRequest(
        @NotNull(message = "La cátedra es obligatoria") Track track,

        @NotNull(message = "El mínimo de integrantes es obligatorio")
        @Min(value = 1, message = "El mínimo de integrantes es 1")
        @Max(value = 20, message = "El máximo de integrantes es 20")
        Integer minMembers,

        @NotNull(message = "El máximo de integrantes es obligatorio")
        @Min(value = 1, message = "El mínimo de integrantes es 1")
        @Max(value = 20, message = "El máximo de integrantes es 20")
        Integer maxMembers) {}
