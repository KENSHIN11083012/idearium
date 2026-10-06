package co.edu.unisimon.expoideas.users;

/**
 * Cambios que la gestión hace sobre una cuenta: rol y adscripción académica.
 * Todos los campos son opcionales: solo se aplica lo que llegue. Los datos
 * personales y la foto los cambia cada persona desde su perfil, y la contraseña
 * se restablece por su propia ruta (queda como temporal).
 *
 * <p>Adscripción: si llega {@code facultyId}, se reemplaza completa (facultad y
 * programa; {@code academicProgramId} en null significa "sin programa"). Si
 * solo llega {@code academicProgramId}, la facultad se toma de ese programa.
 */
public record UserUpdateRequest(Role role, Integer campusId, Integer facultyId, Integer academicProgramId) {}
