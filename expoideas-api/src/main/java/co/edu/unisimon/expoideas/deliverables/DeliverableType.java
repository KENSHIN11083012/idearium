package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.catalogs.PrototypeType;
import co.edu.unisimon.expoideas.editions.Edition;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.files.StoredFile;
import co.edu.unisimon.expoideas.projects.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un entregable que pide una cátedra en una edición: su nombre, qué archivos
 * acepta, si es obligatorio y cuántos archivos admite.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deliverable_types")
public class DeliverableType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Track track;

    /** Si lo tiene, solo se pide a los proyectos de ese tipo de prototipo (INNPRENDE II); si no, a todos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prototype_type_id")
    private PrototypeType prototypeType;

    @Column(nullable = false, length = 100)
    private String name;

    /** Qué se espera y en qué condiciones; lo lee el equipo antes de subir. */
    @Column(length = 300)
    private String description;

    /** Formato oficial para descargar y diligenciar (PDF, DOCX o PPTX), si lo hay. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_file_id")
    private StoredFile template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliverableKind kind;

    /** Si falta, el proyecto queda incompleto. La evaluación lo tendrá en cuenta. */
    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(name = "max_files", nullable = false)
    private int maxFiles = 1;

    /** Orden en el que se muestran los entregables de la cátedra. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Cierre propio, para lo que se entrega después de la sustentación (las fotos
     * de evidencia). Si es null, vale el cierre de entregas de la edición.
     */
    @Column(name = "closes_on")
    private LocalDate closesOn;

    /** Si este entregable se le pide a ese proyecto: los generales, a todos; los de un tipo, solo a los de ese tipo. */
    public boolean appliesTo(Project project) {
        if (prototypeType == null) {
            return true;
        }
        return project.getPrototypeType() != null
                && project.getPrototypeType().getId().equals(prototypeType.getId());
    }
}
