package co.edu.unisimon.expoideas.editions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Alta o edición de una edición de la Expo. Las reglas que cruzan campos (el
 * orden de las fechas, que estén las dos cátedras, que no se pise con otra
 * edición) las verifica EditionService.
 */
public record EditionRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String name,

        @NotNull(message = "La fecha de apertura de inscripciones es obligatoria")
        LocalDate registrationOpensOn,

        @NotNull(message = "La fecha de cierre de inscripciones es obligatoria")
        LocalDate registrationClosesOn,

        @NotNull(message = "La fecha de cierre de entregas es obligatoria")
        LocalDate submissionClosesOn,

        @NotEmpty(message = "Configura las dos cátedras") @Valid
        List<@NotNull(message = "Configura las dos cátedras") TrackSettingsRequest> tracks) {}
