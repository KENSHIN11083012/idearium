package co.edu.unisimon.expoideas.files;

/** Quién puede descargar un archivo. */
public enum FileVisibility {
    /** Cualquiera que tenga el enlace, sin sesión (p. ej. fotos de perfil). */
    PUBLIC,
    /** Solo el propietario y la gestión; cada módulo puede ampliar quién más. */
    PRIVATE
}
