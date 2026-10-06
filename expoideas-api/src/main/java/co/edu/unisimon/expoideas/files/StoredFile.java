package co.edu.unisimon.expoideas.files;

import co.edu.unisimon.expoideas.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Metadatos de un archivo subido. El contenido vive en el almacenamiento
 * ({@link FileStorage}), en {@link #storagePath}.
 *
 * <p>Sin @Data a propósito: equals/hashCode/toString sobre relaciones lazy
 * cargarían al propietario (y su foto) por accidente.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Identificador público: es lo único que sale por la API y aparece en las URL. */
    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    /** Nombre con el que se subió, ya saneado; solo se usa al descargar. */
    @Column(nullable = false)
    private String originalName;

    /** Tipo detectado por el contenido del archivo, no el que declaró el navegador. */
    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    /** Ubicación dentro del almacenamiento. Nunca sale por la API. */
    @Column(nullable = false, unique = true)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileVisibility visibility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User owner;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
