package co.edu.unisimon.expoideas.status;

import co.edu.unisimon.expoideas.audit.AuditService;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.notifications.MailService;
import co.edu.unisimon.expoideas.status.StatusResponse.Disk;
import co.edu.unisimon.expoideas.status.StatusResponse.Mail;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

/**
 * El estado de la instalación para quien la administra: qué versión corre,
 * cuánto disco queda para los archivos y si el correo está saliendo. Lo que el
 * monitoreo de TI necesita (arriba o abajo) lo da /actuator/health; esto es el
 * detalle para saber por qué.
 */
@Service
public class StatusService {

    private final ExpoideasProperties properties;
    private final MailService mailService;
    private final AuditService auditService;
    private final Clock clock;
    private final String version;
    private final DataSize minimumFree;

    public StatusService(
            ExpoideasProperties properties,
            MailService mailService,
            AuditService auditService,
            Clock clock,
            @Value("${info.app.version}") String version,
            @Value("${management.health.diskspace.threshold}") DataSize minimumFree) {
        this.properties = properties;
        this.mailService = mailService;
        this.auditService = auditService;
        this.clock = clock;
        this.version = version;
        this.minimumFree = minimumFree;
    }

    @Transactional(readOnly = true)
    public StatusResponse status() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new StatusResponse(version, now, disk(), mail(now));
    }

    private Disk disk() {
        Path directory = properties.files().directory().toAbsolutePath();
        try {
            // La carpeta puede no existir todavía: se crea con la primera subida.
            FileStore store = Files.getFileStore(Files.exists(directory) ? directory : directory.getParent());
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            int used = total == 0 ? 0 : (int) Math.round(100.0 * (total - free) / total);
            return new Disk(free, total, used, free < minimumFree.toBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el disco de los archivos", e);
        }
    }

    private Mail mail(LocalDateTime now) {
        return new Mail(
                mailService.isConfigured(),
                mailService.canSendLinks(),
                auditService.countSince(Action.MAIL_FAILED, now.minusHours(24)),
                auditService.lastOf(Action.MAIL_FAILED).orElse(null));
    }
}
