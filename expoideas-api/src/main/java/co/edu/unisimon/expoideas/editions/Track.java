package co.edu.unisimon.expoideas.editions;

/**
 * Las dos cátedras de la Cátedra UNISIMÓN INNPRENDE. Un proyecto se inscribe en
 * una de ellas y, si el grupo vuelve a participar, se inscribe otra vez por
 * separado: no es el mismo proyecto que avanza de una a la otra.
 */
public enum Track {

    /** Innovación e investigación: el entregable final es un póster. */
    INNPRENDE_I,

    /** Emprendimiento y prototipado: prototipo y pitch comercial ante jurados. */
    INNPRENDE_II;

    /**
     * La cátedra con el nombre de su muestra, para los correos y el CSV: Despegue
     * (antes Expoideas 1) y Aterrizaje (antes Expoideas 2). La app tiene el suyo en
     * lib/tracks.js.
     */
    public String label() {
        return switch (this) {
            case INNPRENDE_I -> "INNPRENDE I · Despegue";
            case INNPRENDE_II -> "INNPRENDE II · Aterrizaje";
        };
    }
}
