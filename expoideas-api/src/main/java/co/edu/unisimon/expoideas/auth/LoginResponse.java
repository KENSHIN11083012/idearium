package co.edu.unisimon.expoideas.auth;

import co.edu.unisimon.expoideas.users.OnboardingStep;
import co.edu.unisimon.expoideas.users.Role;
import java.util.List;

/**
 * Sesión iniciada: el token y lo que el frontend necesita para pintar la cuenta
 * sin pedir otra vez /users/me.
 *
 * @param photoId      foto de perfil (GET /api/v1/files/{photoId}), o null
 * @param pendingSteps si no está vacía, el frontend lleva al primer ingreso en vez del inicio
 */
public record LoginResponse(
        String token,
        Integer id,
        String firstName,
        String lastName,
        Role role,
        String photoId,
        List<OnboardingStep> pendingSteps) {}
