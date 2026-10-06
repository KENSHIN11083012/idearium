package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de una cuenta desde la gestión, con contraseña temporal. El correo debe
 * ser institucional salvo para jurados, y la adscripción es obligatoria para
 * los roles que la llevan: lo valida UserManagementService, porque depende del rol.
 */
public record UserCreateRequest(
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100, message = "Los nombres no pueden exceder 100 caracteres")
        String firstName,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos no pueden exceder 100 caracteres")
        String lastName,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El formato del correo no es válido")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria") @Password
        String password,

        @NotNull(message = "El rol es obligatorio") Role role,

        Integer campusId,
        Integer facultyId,
        Integer academicProgramId) {}
