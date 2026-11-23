package in.gov.health104.crm.web;

import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.domain.AppUser;
import in.gov.health104.crm.domain.Role;
import in.gov.health104.crm.repository.AppUserRepository;
import in.gov.health104.crm.service.AuditService;
import in.gov.health104.crm.web.dto.UserDtos.CreateUserRequest;
import in.gov.health104.crm.web.dto.UserDtos.ResetPasswordRequest;
import in.gov.health104.crm.web.dto.UserDtos.UpdateUserRequest;
import in.gov.health104.crm.web.dto.UserDtos.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Business rules of Admin user management (repository, encoder and audit are mocked - no DB needed). */
@ExtendWith(MockitoExtension.class)
class UserAdminControllerTest {

    @Mock AppUserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock AuditService audit;
    @InjectMocks UserAdminController controller;

    @BeforeEach
    void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private static CreateUserRequest create(String username, String role, String district, String ext) {
        return new CreateUserRequest(username, "  Test User  ", role, "", "", district, ext, "Secret@123");
    }

    private static UpdateUserRequest update(String role, String district, String ext, boolean active) {
        return new UpdateUserRequest("Updated Name", role, "9876543210", "u@example.gov.in", district, ext, active);
    }

    private static AppUser existing(long id, String username, Role role) {
        AppUser u = new AppUser();
        u.setId(id); u.setUsername(username); u.setFullName(username); u.setRole(role); u.setPasswordHash("old-hash");
        return u;
    }

    private AppUser savedUser() {
        ArgumentCaptor<AppUser> c = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(c.capture());
        return c.getValue();
    }

    @Nested
    class Create {

        @Test
        void createsMedicalOfficerWithHashedPasswordAndAudit() {
            when(encoder.encode("Secret@123")).thenReturn("bcrypt-hash");

            UserView v = controller.create(create("Doctor02", "MEDICAL_OFFICER", "Durg", "2002"));

            AppUser saved = savedUser();
            assertThat(saved.getUsername()).isEqualTo("doctor02");          // normalised to lower case
            assertThat(saved.getFullName()).isEqualTo("Test User");          // trimmed
            assertThat(saved.getRole()).isEqualTo(Role.MEDICAL_OFFICER);
            assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");   // never the plain password
            assertThat(saved.getDistrict()).isEqualTo("Durg");
            assertThat(saved.getExtensionNo()).isEqualTo("2002");
            assertThat(saved.getPhone()).isNull();                           // blank -> null
            assertThat(saved.isActive()).isTrue();
            assertThat(v.role()).isEqualTo("MEDICAL_OFFICER");
            verify(audit).record(eq("USER_CREATE"), eq("User"), any(), argThat((Map<String, Object> d) ->
                    "doctor02".equals(d.get("username")) && "MEDICAL_OFFICER".equals(d.get("role"))));
        }

        @Test
        void createsCounsellorWithExtension() {
            controller.create(create("counsel02", "COUNSELLOR", null, "3002"));
            assertThat(savedUser().getExtensionNo()).isEqualTo("3002");
        }

        @ParameterizedTest
        @EnumSource(Role.class)
        void everyRoleCanBeCreated(Role role) {
            String district = role == Role.FACILITY_OFFICER || role == Role.DISTRICT_OFFICER ? "Raipur" : null;
            controller.create(create("user_" + role.name().toLowerCase(), role.name(), district, null));
            assertThat(savedUser().getRole()).isEqualTo(role);
        }

        @Test
        void rejectsDuplicateUsernameIgnoringCase() {
            when(users.existsByUsernameIgnoreCase("doctor01")).thenReturn(true);
            assertThatThrownBy(() -> controller.create(create("DOCTOR01", "MEDICAL_OFFICER", null, null)))
                    .isInstanceOf(ApiException.class).hasMessageContaining("already taken");
            verify(users, never()).save(any());
            verifyNoInteractions(audit);
        }

        @Test
        void rejectsUnknownRole() {
            assertThatThrownBy(() -> controller.create(create("x01", "SUPER_USER", null, null)))
                    .isInstanceOf(ApiException.class).hasMessage("Unknown role");
            verify(users, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = Role.class, names = {"FACILITY_OFFICER", "DISTRICT_OFFICER"})
        void districtRequiredForDistrictLevelOfficers(Role role) {
            assertThatThrownBy(() -> controller.create(create("officer9", role.name(), " ", null)))
                    .isInstanceOf(ApiException.class).hasMessageContaining("District is required");
            verify(users, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = Role.class, names = {"ADMIN", "SUPERVISOR", "FACILITY_OFFICER", "DISTRICT_OFFICER", "STATE_OFFICER", "MINISTER_OFFICE"})
        void extensionIsDroppedForNonCallRoles(Role role) {
            controller.create(create("nocall", role.name(), "Raipur", "4444"));
            assertThat(savedUser().getExtensionNo()).isNull();
            verify(users, never()).existsByExtensionNoAndActiveTrueAndIdNot(any(), any());
        }

        @Test
        void rejectsExtensionAlreadyUsedByActiveUser() {
            when(users.existsByExtensionNoAndActiveTrueAndIdNot("2001", -1L)).thenReturn(true);
            assertThatThrownBy(() -> controller.create(create("doctor03", "MEDICAL_OFFICER", null, "2001")))
                    .isInstanceOf(ApiException.class).hasMessageContaining("Extension 2001");
            verify(users, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void changesRoleAndRecordsPreviousRole() {
            AppUser u = existing(7, "agent05", Role.AGENT);
            when(users.findById(7L)).thenReturn(Optional.of(u));

            UserView v = controller.update(7L, update("COUNSELLOR", "Durg", "3005", true));

            assertThat(v.role()).isEqualTo("COUNSELLOR");
            assertThat(u.getExtensionNo()).isEqualTo("3005");
            assertThat(u.getPhone()).isEqualTo("9876543210");
            verify(audit).record(eq("USER_UPDATE"), eq("User"), eq(7L), argThat((Map<String, Object> d) ->
                    "AGENT".equals(d.get("previousRole")) && "COUNSELLOR".equals(d.get("role"))));
        }

        @Test
        void sameExtensionOnSameUserIsAllowed() {
            AppUser u = existing(3, "doctor01", Role.MEDICAL_OFFICER);
            u.setExtensionNo("2001");
            when(users.findById(3L)).thenReturn(Optional.of(u));
            when(users.existsByExtensionNoAndActiveTrueAndIdNot("2001", 3L)).thenReturn(false);

            controller.update(3L, update("MEDICAL_OFFICER", null, "2001", true));
            assertThat(u.getExtensionNo()).isEqualTo("2001");
        }

        @Test
        void deactivatesUser() {
            AppUser u = existing(4, "counsel01", Role.COUNSELLOR);
            when(users.findById(4L)).thenReturn(Optional.of(u));

            UserView v = controller.update(4L, update("COUNSELLOR", null, null, false));
            assertThat(v.active()).isFalse();
            assertThat(u.isActive()).isFalse();
        }

        @Test
        void adminCannotRemoveOwnAdminRole() {
            when(users.findById(1L)).thenReturn(Optional.of(existing(1, "admin", Role.ADMIN)));
            assertThatThrownBy(() -> controller.update(1L, update("AGENT", null, null, true)))
                    .isInstanceOf(ApiException.class).hasMessageContaining("cannot remove your own Admin role");
            verify(users, never()).save(any());
        }

        @Test
        void adminCannotDeactivateSelf() {
            when(users.findById(1L)).thenReturn(Optional.of(existing(1, "admin", Role.ADMIN)));
            assertThatThrownBy(() -> controller.update(1L, update("ADMIN", null, null, false)))
                    .isInstanceOf(ApiException.class);
            verify(users, never()).save(any());
        }

        @Test
        void adminCanDeactivateAnotherAdmin() {
            AppUser other = existing(9, "admin2", Role.ADMIN);
            when(users.findById(9L)).thenReturn(Optional.of(other));
            controller.update(9L, update("ADMIN", null, null, false));
            assertThat(other.isActive()).isFalse();
        }

        @Test
        void unknownUserIsNotFound() {
            when(users.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> controller.update(99L, update("AGENT", null, null, true)))
                    .isInstanceOf(ApiException.class).hasMessage("User not found");
        }
    }

    @Nested
    class ResetPassword {

        @Test
        void setsNewHashAndUnlocksAccount() {
            AppUser u = existing(5, "agent01", Role.AGENT);
            u.setFailedAttempts(5);
            u.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            when(users.findById(5L)).thenReturn(Optional.of(u));
            when(encoder.encode("NewPass@1")).thenReturn("new-hash");

            controller.resetPassword(5L, new ResetPasswordRequest("NewPass@1"));

            assertThat(u.getPasswordHash()).isEqualTo("new-hash");
            assertThat(u.getFailedAttempts()).isZero();
            assertThat(u.getLockedUntil()).isNull();
            verify(audit).record(eq("USER_PASSWORD_RESET"), eq("User"), eq(5L), argThat((Map<String, Object> d) ->
                    !d.toString().contains("NewPass@1")));                   // password never logged
        }

        @Test
        void unknownUserIsNotFound() {
            when(users.findById(42L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> controller.resetPassword(42L, new ResetPasswordRequest("NewPass@1")))
                    .isInstanceOf(ApiException.class);
            verifyNoInteractions(encoder);
        }
    }

    @Test
    void userViewReportsLockedOnlyWhileLockIsInFuture() {
        AppUser u = existing(1, "a", Role.AGENT);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        assertThat(UserView.of(u).locked()).isTrue();
        u.setLockedUntil(LocalDateTime.now().minusMinutes(5));
        assertThat(UserView.of(u).locked()).isFalse();
    }
}
