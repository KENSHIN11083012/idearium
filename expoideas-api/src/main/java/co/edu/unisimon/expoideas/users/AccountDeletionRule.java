package co.edu.unisimon.expoideas.users;

import java.util.Optional;

/**
 * Motivo por el que una cuenta no se puede eliminar, que aporta otro módulo.
 *
 * <p>La gestión de cuentas no sabe de proyectos ni de jurados: cada módulo
 * responde por lo suyo y UserManagementService explica el primero que aparezca,
 * en vez de dejar que falle una restricción de la base con un mensaje que no
 * dice nada.
 */
public interface AccountDeletionRule {

    /** Mensaje para quien intenta eliminar la cuenta, o vacío si no hay impedimento. */
    Optional<String> reasonToKeep(User user);
}
