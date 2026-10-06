package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectura del rastro. Escribirlo es cosa de {@link AuditListener}. */
@Service
@RequiredArgsConstructor
public class AuditService {

    static final int DEFAULT_SIZE = 50;

    /** Lo más reciente primero; el id desempata lo que pasó en el mismo segundo. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditEventRepository auditRepository;

    /** Cuántas veces pasó eso desde ese momento. */
    @Transactional(readOnly = true)
    public long countSince(Action action, LocalDateTime since) {
        return auditRepository.countByActionAndOccurredAtGreaterThanEqual(action, since);
    }

    /** Cuándo pasó eso por última vez; vacío si nunca. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastOf(Action action) {
        return auditRepository
                .findFirstByActionOrderByOccurredAtDescIdDesc(action)
                .map(AuditEvent::getOccurredAt);
    }

    /**
     * @param action solo esa acción; null, todas
     * @param from   desde ese día; null, desde el principio
     * @param to     hasta ese día, incluido; null, hasta hoy
     * @param page   la primera es 0
     * @param size   filas por página, entre 1 y {@value PageResponse#MAX_SIZE}
     * @throws IllegalArgumentException si el rango de fechas está al revés
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(Action action, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        return PageResponse.from(
                auditRepository.search(
                        action,
                        from == null ? null : from.atStartOfDay(),
                        to == null ? null : to.plusDays(1).atStartOfDay(),
                        PageResponse.request(page, size, NEWEST_FIRST)),
                AuditEventResponse::from);
    }
}
