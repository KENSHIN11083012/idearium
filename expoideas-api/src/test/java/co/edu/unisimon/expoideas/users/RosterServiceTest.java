package co.edu.unisimon.expoideas.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.users.RosterImportResponse.RejectedRow;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** Qué filas entran al listado, cuáles se rechazan y qué se cuenta. */
@ExtendWith(MockitoExtension.class)
class RosterServiceTest {

    @Mock
    private RosterRepository rosterRepository;

    @Mock
    private UserRepository userRepository;

    private RosterService service;

    @BeforeEach
    void setUp() {
        service = new RosterService(rosterRepository, userRepository);
        lenient().when(rosterRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        lenient().when(rosterRepository.save(any(RosterEntry.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(rosterRepository.count()).thenReturn(2L);
        lenient().when(userRepository.findEmailsIn(any())).thenReturn(List.of());
    }

    @Test
    void validRowsEnterTheRestComeBackWithTheirReason() {
        String csv = "correo;rol;nombres;apellidos\n"
                + "Carlos.Mendoza@unisimon.edu.co;Docente;Carlos;Mendoza\n"
                + "ana@unisimon.edu.co;estudiante;;\n"
                + "ana@gmail.com;estudiante;Ana;Pérez\n"
                + "luis@unisimon.edu.co;jurado;Luis;Gómez\n"
                + "ANA@unisimon.edu.co;estudiante;Ana;Pérez\n"
                + ";estudiante;Sin;Correo\n";

        RosterImportResponse result = service.importCsv(file(csv));

        assertThat(result.added()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.rejected())
                .extracting(RejectedRow::line, RejectedRow::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(4, "El correo debe terminar en @unisimon.edu.co"),
                        org.assertj.core.groups.Tuple.tuple(5, "El rol debe ser «estudiante» o «profesor»"),
                        org.assertj.core.groups.Tuple.tuple(6, "El correo se repite en el archivo"),
                        org.assertj.core.groups.Tuple.tuple(7, "El correo debe terminar en @unisimon.edu.co"));

        ArgumentCaptor<RosterEntry> saved = ArgumentCaptor.forClass(RosterEntry.class);
        verify(rosterRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        RosterEntry carlos = saved.getAllValues().getFirst();
        // El correo se guarda en minúsculas; «Docente» vale como profesor.
        assertThat(carlos.getEmail()).isEqualTo("carlos.mendoza@unisimon.edu.co");
        assertThat(carlos.getRole()).isEqualTo(Role.TEACHER);
        assertThat(carlos.getFirstName()).isEqualTo("Carlos");
        assertThat(saved.getAllValues().get(1).getFirstName()).isNull();
    }

    @Test
    void aNewUploadUpdatesWhoWasAlreadyThereAndCountsWhoAlreadyRegistered() {
        RosterEntry existing = RosterEntry.of("ana@unisimon.edu.co");
        existing.setRole(Role.STUDENT);
        when(rosterRepository.findByEmail("ana@unisimon.edu.co")).thenReturn(Optional.of(existing));
        when(userRepository.findEmailsIn(List.of("ana@unisimon.edu.co"))).thenReturn(List.of("Ana@unisimon.edu.co"));

        RosterImportResponse result = service.importCsv(file("correo;rol\nana@unisimon.edu.co;profesor\n"));

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.registered()).isEqualTo(1);
        assertThat(existing.getRole()).isEqualTo(Role.TEACHER);
    }

    @Test
    void aFileWithoutTheColumnsIsAFieldError() {
        assertThatThrownBy(() -> service.importCsv(file("nombre;apellido\nAna;Pérez\n")))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        error -> assertThat(error.getFields()).containsKey("file"));
        verify(rosterRepository, never()).save(any());
    }

    @Test
    void theListSaysWhoAlreadyHasAnAccount() {
        RosterEntry ana = RosterEntry.of("ana@unisimon.edu.co");
        ana.setRole(Role.STUDENT);
        ana.setFirstName("Ana");
        ana.setLastName("Pérez");
        RosterEntry luis = RosterEntry.of("luis@unisimon.edu.co");
        luis.setRole(Role.STUDENT);
        RosterEntry carlos = RosterEntry.of("carlos@unisimon.edu.co");
        carlos.setRole(Role.TEACHER);
        carlos.setFirstName("Carlos");
        carlos.setLastName("Ávila");
        when(rosterRepository.findAll()).thenReturn(List.of(luis, ana, carlos));
        when(userRepository.findEmailsIn(
                        List.of("carlos@unisimon.edu.co", "ana@unisimon.edu.co", "luis@unisimon.edu.co")))
                .thenReturn(List.of("ana@unisimon.edu.co"));

        List<RosterEntryResponse> list = service.list();

        // Por apellido en español (Ávila antes que Pérez); sin nombre, al final.
        assertThat(list)
                .extracting(RosterEntryResponse::email, RosterEntryResponse::registered)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("carlos@unisimon.edu.co", false),
                        org.assertj.core.groups.Tuple.tuple("ana@unisimon.edu.co", true),
                        org.assertj.core.groups.Tuple.tuple("luis@unisimon.edu.co", false));
    }

    private static MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "listado.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
