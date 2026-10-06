package co.edu.unisimon.expoideas.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditRepository;

    @InjectMocks
    private AuditService service;

    @Test
    void theLastDayOfTheRangeCountsWholeAndTheNewestComesFirst() {
        when(auditRepository.search(any(), any(), any(), any())).thenReturn(Page.empty());

        service.search(Action.GRADES_PUBLISHED, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 2, 20);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository)
                .search(
                        eq(Action.GRADES_PUBLISHED),
                        eq(LocalDateTime.of(2026, 11, 1, 0, 0)),
                        eq(LocalDateTime.of(2026, 12, 1, 0, 0)),
                        page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "-5, 1", "50, 50", "200, 200", "5000, 200"})
    void thePageSizeStaysWithinBounds(int asked, int used) {
        when(auditRepository.search(any(), any(), any(), any())).thenReturn(Page.empty());

        service.search(null, null, null, -3, asked);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).search(any(), any(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(used);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> service.search(null, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), 0, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha final");

        verifyNoInteractions(auditRepository);
    }
}
