package co.edu.unisimon.expoideas.users;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import co.edu.unisimon.expoideas.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** La lista de profesores para inscribir un proyecto: quién la pide y cómo sale. */
@SecuredWebMvcTest(TeacherController.class)
class TeacherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "STUDENT")
    void listsOnlyTeachersAskingTheDatabaseForThatRole() throws Exception {
        User zoe = TestData.user(7, "zoe@unisimon.edu.co", Role.TEACHER);
        zoe.setFirstName("zoe");
        User ana = TestData.user(8, "ana@unisimon.edu.co", Role.TEACHER);
        ana.setFirstName("Ana");
        // La consulta ya deja fuera a las cuentas anonimizadas: no se le ofrecen a nadie como profesor.
        when(userRepository.findAllWithProfileByRoleAndAnonymizedAtIsNull(Role.TEACHER))
                .thenReturn(List.of(zoe, ana));

        mockMvc.perform(get("/api/v1/teachers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Por nombre, sin que las mayúsculas cambien el orden.
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].fullName").value("Ana Apellido"))
                .andExpect(jsonPath("$[0].faculty").value("Ingeniería"))
                .andExpect(jsonPath("$[1].id").value(7))
                // Solo lo que hace falta para elegirlo: ni correo ni rol.
                .andExpect(jsonPath("$[0].email").doesNotExist());

        // No se traen todas las cuentas para descartar en memoria las que no son de profesores.
        verify(userRepository, never()).findAllWithProfileBy();
    }

    @Test
    void withoutASessionItIsNotListed() throws Exception {
        mockMvc.perform(get("/api/v1/teachers")).andExpect(status().isUnauthorized());
    }
}
