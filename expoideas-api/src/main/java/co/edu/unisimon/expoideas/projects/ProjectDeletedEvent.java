package co.edu.unisimon.expoideas.projects;

/**
 * Se publica justo antes de borrar una inscripción, dentro de su transacción.
 * Lo escuchan los módulos que cuelgan del proyecto (hoy, los entregables) para
 * llevarse lo suyo: la base borra sus filas en cascada, pero el contenido de
 * los archivos en disco hay que quitarlo a mano.
 */
public record ProjectDeletedEvent(Integer projectId) {}
