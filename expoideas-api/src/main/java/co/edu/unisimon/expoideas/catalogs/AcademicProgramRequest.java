package co.edu.unisimon.expoideas.catalogs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta o edición de un programa académico con la facultad a la que pertenece. */
public record AcademicProgramRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @NotNull(message = "La facultad es obligatoria") Integer facultyId) {}
