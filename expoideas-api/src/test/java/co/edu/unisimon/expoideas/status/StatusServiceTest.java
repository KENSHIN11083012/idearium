package co.edu.unisimon.expoideas.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.audit.AuditService;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.TimeConfig;
import co.edu.unisimon.expoideas.notifications.MailService;
import co.edu.unisimon.expoideas.support.TestData;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    /** Las 15:00 UTC son las 10:00 en Colombia. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), TimeConfig.ZONE);

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Mock
    private MailService mailService;

    @Mock
    private AuditService auditService;

    @TempDir
    Path files;

    private StatusService service(Path directory, DataSize minimumFree) {
        return new StatusService(
                TestData.properties(directory), mailService, auditService, CLOCK, "211162e", minimumFree);
    }

    @Test
    void saysWhichVersionRunsAndWhatTimeThePlatformThinksItIs() {
        StatusResponse status = service(files, DataSize.ofBytes(1)).status();

        assertThat(status.version()).isEqualTo("211162e");
        assertThat(status.now()).isEqualTo(NOW);
    }

    @Test
    void measuresTheDiskWhereTheUploadedFilesLive() {
        StatusResponse.Disk disk = service(files, DataSize.ofBytes(1)).status().disk();

        assertThat(disk.totalBytes()).isPositive();
        assertThat(disk.freeBytes()).isPositive().isLessThanOrEqualTo(disk.totalBytes());
        assertThat(disk.usedPercent()).isBetween(0, 100);
        assertThat(disk.low()).isFalse();
    }

    @Test
    void warnsWhenLessThanTheMarginIsLeft() {
        // Un margen que ningún disco tiene: es el mismo con el que la salud pasaría a DOWN.
        StatusResponse.Disk disk =
                service(files, DataSize.ofTerabytes(1_000_000)).status().disk();

        assertThat(disk.low()).isTrue();
    }

    @Test
    void aFolderThatDoesNotExistYetIsMeasuredOnItsParent() {
        // La carpeta de archivos se crea con la primera subida.
        StatusResponse.Disk disk = service(files.resolve("todavia-no"), DataSize.ofBytes(1))
                .status()
                .disk();

        assertThat(disk.totalBytes()).isPositive();
    }

    @Test
    void tellsWhetherMailIsLeavingAndHowManyWereLostToday() {
        LocalDateTime lastFailure = LocalDateTime.of(2026, 11, 20, 8, 30);
        when(mailService.isConfigured()).thenReturn(true);
        when(mailService.canSendLinks()).thenReturn(false);
        when(auditService.countSince(Action.MAIL_FAILED, NOW.minusHours(24))).thenReturn(3L);
        when(auditService.lastOf(Action.MAIL_FAILED)).thenReturn(Optional.of(lastFailure));

        StatusResponse.Mail mail = service(files, DataSize.ofBytes(1)).status().mail();

        assertThat(mail.configured()).isTrue();
        assertThat(mail.linksEnabled()).isFalse();
        assertThat(mail.failedLast24Hours()).isEqualTo(3);
        assertThat(mail.lastFailureAt()).isEqualTo(lastFailure);
    }

    @Test
    void withoutLostMailThereIsNoLastFailure() {
        when(auditService.lastOf(Action.MAIL_FAILED)).thenReturn(Optional.empty());

        StatusResponse.Mail mail = service(files, DataSize.ofBytes(1)).status().mail();

        assertThat(mail.failedLast24Hours()).isZero();
        assertThat(mail.lastFailureAt()).isNull();
    }
}
