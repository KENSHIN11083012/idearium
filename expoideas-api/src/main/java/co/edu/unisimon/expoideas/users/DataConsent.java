package co.edu.unisimon.expoideas.users;

/**
 * La autorización de tratamiento de datos personales (Ley 1581 de 2012).
 *
 * <p>El texto que la persona acepta está en la app
 * ({@code components/forms/DataConsentField.jsx}) y, versión por versión, en
 * docs/datos-personales.md. Si el texto cambia, sube {@link #VERSION} en el
 * mismo commit: así cada cuenta dice cuál aceptó.
 */
public final class DataConsent {

    /** La versión del texto que se muestra hoy. */
    public static final String VERSION = "1";

    private DataConsent() {}
}
