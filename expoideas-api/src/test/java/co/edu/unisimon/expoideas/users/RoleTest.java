package co.edu.unisimon.expoideas.users;

import static co.edu.unisimon.expoideas.users.Role.ADMIN;
import static co.edu.unisimon.expoideas.users.Role.JUDGE;
import static co.edu.unisimon.expoideas.users.Role.MACONDOLAB;
import static co.edu.unisimon.expoideas.users.Role.STUDENT;
import static co.edu.unisimon.expoideas.users.Role.TEACHER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void onlyTeachersAndStudentsDeclareAffiliation() {
        assertThat(EnumSet.allOf(Role.class).stream().filter(Role::requiresAffiliation))
                .containsExactlyInAnyOrder(TEACHER, STUDENT);
    }

    @Test
    void managementRolesAreAdminAndMacondoLab() {
        assertThat(EnumSet.allOf(Role.class).stream().filter(Role::isManagement))
                .containsExactlyInAnyOrder(ADMIN, MACONDOLAB);
    }

    @Test
    void adminManagesEveryRole() {
        assertThat(EnumSet.allOf(Role.class)).allMatch(ADMIN::canManage);
    }

    @Test
    void macondoLabManagesOnlyNonManagementAccounts() {
        assertThat(EnumSet.allOf(Role.class).stream().filter(MACONDOLAB::canManage))
                .containsExactlyInAnyOrder(TEACHER, JUDGE, STUDENT);
    }

    @Test
    void otherRolesManageNoAccounts() {
        for (Role role : EnumSet.of(TEACHER, JUDGE, STUDENT)) {
            assertThat(EnumSet.allOf(Role.class)).noneMatch(role::canManage);
        }
    }

    @Test
    void authorityHasTheSpringSecurityPrefix() {
        assertThat(JUDGE.authority()).isEqualTo("ROLE_JUDGE");
    }
}
