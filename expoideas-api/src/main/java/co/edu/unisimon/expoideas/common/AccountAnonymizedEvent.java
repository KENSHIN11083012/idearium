package co.edu.unisimon.expoideas.common;

/**
 * Una cuenta acaba de ser anonimizada. Lo publica {@code users} dentro de su
 * transacción para que los módulos que guardan copias de su correo o de su
 * nombre (el rastro de auditoría) las quiten en esa misma transacción.
 *
 * @param userId      la cuenta, que sigue existiendo
 * @param formerEmail el correo que tenía
 * @param formerName  el nombre completo que tenía, o null si no lo había puesto
 * @param alias       el correo sin dueño que la identifica desde ahora
 */
public record AccountAnonymizedEvent(Integer userId, String formerEmail, String formerName, String alias) {

    /**
     * El dominio del correo de las cuentas anonimizadas. «.invalid» está reservado
     * (RFC 2606): no existe ni existirá, así que nadie recibe nada ahí.
     */
    public static final String ALIAS_DOMAIN = "@anonimo.invalid";
}
