package co.edu.unisimon.expoideas.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que cada persona puede cambiar de su perfil: nombre y, si su rol la lleva,
 * adscripción académica (sede y facultad obligatorias en ese caso; la gestión y
 * los jurados no la tienen y se ignora).
 */
public record ProfileUpdateRequest(
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100, message = "Los nombres no pueden exceder 100 caracteres")
        String firstName,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos no pueden exceder 100 caracteres")
        String lastName,

        Integer campusId,
        Integer facultyId,
        Integer academicProgramId) {}
