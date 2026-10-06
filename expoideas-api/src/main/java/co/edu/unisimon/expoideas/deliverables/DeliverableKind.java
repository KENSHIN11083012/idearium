package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.files.FileFormat;
import java.util.EnumSet;
import java.util.Set;

/**
 * Qué archivos acepta un entregable. MacondoLab elige entre estas tres opciones
 * al configurar la cátedra, en vez de listar formatos uno por uno.
 */
public enum DeliverableKind {

    /** Documento: el póster de investigación, una carta de validación. */
    DOCUMENT(FileFormat.DOCUMENTS),

    /** Imagen: fotos del prototipo o de la sustentación. */
    IMAGE(FileFormat.IMAGES),

    /** Cualquiera de los dos, cuando la evidencia puede venir de varias formas. */
    ANY(union(FileFormat.DOCUMENTS, FileFormat.IMAGES)),

    /** Un enlace (video, prototipo en línea): no se sube archivo. */
    LINK(Set.of());

    private final Set<FileFormat> formats;

    DeliverableKind(Set<FileFormat> formats) {
        this.formats = Set.copyOf(formats);
    }

    /** Formatos que el módulo de archivos debe aceptar para este entregable. */
    public Set<FileFormat> formats() {
        return formats;
    }

    /** Si el entregable es una dirección en vez de un archivo. */
    public boolean isLink() {
        return this == LINK;
    }

    private static Set<FileFormat> union(Set<FileFormat> a, Set<FileFormat> b) {
        EnumSet<FileFormat> all = EnumSet.copyOf(a);
        all.addAll(b);
        return all;
    }
}
