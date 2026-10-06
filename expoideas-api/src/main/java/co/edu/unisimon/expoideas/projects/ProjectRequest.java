package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Inscripción de un proyecto o edición de sus datos. La edición y la cátedra
 * solo cuentan al inscribir: después no cambian y se ignoran.
 */
public record ProjectRequest(
        @NotNull(message = "La edición es obligatoria") Integer editionId,
        @NotNull(message = "La cátedra es obligatoria") Track track,

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede exceder 150 caracteres")
        String title,

        @NotBlank(message = "La propuesta de valor es obligatoria")
        @Size(max = 500, message = "La propuesta de valor no puede exceder 500 caracteres")
        String summary,

        @NotNull(message = "El sector es obligatorio") Integer sectorId,

        /** Solo en INNPRENDE II: obligatorio si el catálogo tiene tipos; en I no se acepta. */
        Integer prototypeTypeId,
        @NotNull(message = "El profesor es obligatorio") Integer teacherId) {}
