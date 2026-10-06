package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.catalogs.CatalogLookup;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.editions.Edition;
import co.edu.unisimon.expoideas.editions.EditionRepository;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.files.FileFormat;
import co.edu.unisimon.expoideas.files.FileService;
import co.edu.unisimon.expoideas.files.FileVisibility;
import co.edu.unisimon.expoideas.files.StoredFile;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Los entregables que pide cada cátedra. Los escribe MacondoLab; leerlos pide
 * sesión, porque los consulta el equipo al subir sus archivos.
 *
 * <p>Un entregable con archivos subidos no se borra: se perderían las evidencias
 * de los proyectos que ya lo entregaron. Su plantilla (el formato oficial que el
 * equipo descarga y diligencia) se sube y se quita aparte de sus datos.
 */
@Service
@RequiredArgsConstructor
public class DeliverableTypeService {

    private final DeliverableTypeRepository typeRepository;
    private final DeliverableRepository deliverableRepository;
    private final EditionRepository editionRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final CatalogLookup catalogLookup;

    @Transactional(readOnly = true)
    public List<DeliverableTypeResponse> list(Integer editionId, Track track) {
        return typeRepository.findByEditionIdAndTrackOrderBySortOrderAscIdAsc(editionId, track).stream()
                .map(DeliverableTypeResponse::from)
                .toList();
    }

    @Transactional
    public DeliverableTypeResponse create(DeliverableTypeRequest request) {
        Edition edition = editionRepository
                .findWithTracksById(request.editionId())
                .orElseThrow(() -> new NoSuchElementException("No existe una edición con ID: " + request.editionId()));
        if (edition.track(request.track()).isEmpty()) {
            throw new InvalidFieldsException("track", "Esa cátedra no está configurada en la edición");
        }

        DeliverableType type = new DeliverableType();
        type.setEdition(edition);
        type.setTrack(request.track());
        return save(type, request);
    }

    /** La edición y la cátedra no cambian; el resto sí. */
    @Transactional
    public DeliverableTypeResponse update(Integer id, DeliverableTypeRequest request) {
        return save(find(id), request);
    }

    /** @throws ConflictException si algún proyecto ya subió archivos para ese entregable */
    @Transactional
    public void delete(Integer id) {
        DeliverableType type = find(id);
        if (deliverableRepository.existsByTypeId(id)) {
            throw new ConflictException(
                    "No puedes eliminar \"" + type.getName() + "\": ya hay proyectos que lo entregaron");
        }
        StoredFile template = type.getTemplate();
        type.setTemplate(null);
        typeRepository.delete(type);
        typeRepository.flush();
        if (template != null) {
            fileService.delete(template);
        }
    }

    /**
     * Sube o reemplaza la plantilla. Es un archivo privado que cualquier sesión
     * puede leer (ver {@link DeliverableTemplateAccessRule}); la anterior se borra
     * cuando la nueva queda guardada.
     *
     * @throws InvalidFieldsException si no es PDF, DOCX o PPTX, está vacío o pasa de 5 MB
     */
    @Transactional
    public DeliverableTypeResponse uploadTemplate(Integer id, MultipartFile upload, String actorEmail) {
        DeliverableType type = find(id);
        User actor = userRepository
                .findByEmail(actorEmail)
                .orElseThrow(() -> new NoSuchElementException("No existe una cuenta con el correo: " + actorEmail));
        StoredFile previous = type.getTemplate();
        type.setTemplate(fileService.store(upload, FileFormat.TEMPLATES, FileVisibility.PRIVATE, actor));
        typeRepository.flush();
        if (previous != null) {
            fileService.delete(previous);
        }
        return DeliverableTypeResponse.from(type);
    }

    /** Quita la plantilla, si la hay. */
    @Transactional
    public DeliverableTypeResponse deleteTemplate(Integer id) {
        DeliverableType type = find(id);
        StoredFile template = type.getTemplate();
        if (template != null) {
            type.setTemplate(null);
            typeRepository.flush();
            fileService.delete(template);
        }
        return DeliverableTypeResponse.from(type);
    }

    private DeliverableType find(Integer id) {
        return typeRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe un entregable con ID: " + id));
    }

    private DeliverableTypeResponse save(DeliverableType type, DeliverableTypeRequest request) {
        type.setName(request.name().strip());
        type.setDescription(
                request.description() == null ? null : request.description().strip());
        type.setKind(request.kind());
        type.setRequired(request.required());
        type.setMaxFiles(request.maxFiles());
        type.setSortOrder(request.sortOrder());
        type.setClosesOn(request.closesOn());
        if (request.prototypeTypeId() != null && type.getTrack() != Track.INNPRENDE_II) {
            throw new InvalidFieldsException("prototypeTypeId", "El tipo de prototipo es solo para INNPRENDE II");
        }
        type.setPrototypeType(
                request.prototypeTypeId() == null ? null : catalogLookup.prototypeType(request.prototypeTypeId()));
        // saveAndFlush: un nombre repetido en la misma cátedra falla aquí, con 409.
        return DeliverableTypeResponse.from(typeRepository.saveAndFlush(type));
    }
}
