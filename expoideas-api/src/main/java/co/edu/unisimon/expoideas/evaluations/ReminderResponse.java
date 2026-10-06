package co.edu.unisimon.expoideas.evaluations;

/**
 * Qué pasó al recordar: a cuántos jurados se les escribió y cuántos proyectos
 * tienen todavía alguna evaluación pendiente.
 */
public record ReminderResponse(int jurors, int projects) {}
