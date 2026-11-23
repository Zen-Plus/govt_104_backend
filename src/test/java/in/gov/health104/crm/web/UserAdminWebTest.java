package in.gov.health104.crm.web;

import in.gov.health104.crm.domain.AppUser;
import in.gov.health104.crm.domain.Role;
import in.gov.health104.crm.mongo.ApiLogRepository;
import in.gov.health104.crm.repository.AppUserRepository;
import in.gov.health104.crm.security.JwtAuthFilter;
import in.gov.health104.crm.security.JwtService;
import in.gov.health104.crm.security.SecurityConfig;
import in.gov.health104.crm.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP layer of /api/admin/users with the real security configuration:
 * Admin-only access, request validation (400 + fieldErrors), status codes and response shape.
 */
@WebMvcTest(UserAdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class UserAdminWebTest {

    private static final String URL = "/api/admin/users";
    private static final String VALID_BODY = """
            {"username":"doctor02","fullName":"Dr. Rao","role":"MEDICAL_OFFICER","district":"Durg",
             "extensionNo":"2002","password":"Secret@123"}""";

    @Autowired MockMvc mvc;
    @MockBean AppUserRepository users;
    @MockBean AuditService audit;
    @MockBean JwtService jwtService;
    @MockBean ApiLogRepository apiLogs;

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON);
    }

    private static AppUser doctor() {
        AppUser u = new AppUser();
        u.setId(3L); u.setUsername("doctor01"); u.setFullName("Dr. Meera"); u.setRole(Role.MEDICAL_OFFICER);
        u.setPasswordHash("$2a$12$secret-hash"); u.setExtensionNo("2001");
        return u;
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY)).andExpect(status().isUnauthorized());
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ADMIN")
    void nonAdminRolesAreForbidden(Role role) throws Exception {
        mvc.perform(get(URL).with(user("u").roles(role.name()))).andExpect(status().isForbidden());
        mvc.perform(post(URL).with(user("u").roles(role.name())).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(users, never()).save(any());
    }

    @Test
    void listNeverExposesPasswordHash() throws Exception {
        when(users.findAllByOrderByRoleAscUsernameAsc()).thenReturn(List.of(doctor()));
        mvc.perform(asAdmin(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("doctor01"))
                .andExpect(jsonPath("$[0].role").value("MEDICAL_OFFICER"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("secret-hash"))));
    }

    @Test
    void createReturns201() throws Exception {
        mvc.perform(asAdmin(post(URL)).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("doctor02"))
                .andExpect(jsonPath("$.role").value("MEDICAL_OFFICER"))
                .andExpect(jsonPath("$.extensionNo").value("2002"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(users).save(any(AppUser.class));
    }

    @Test
    void createWithInvalidFieldsReturns400WithFieldErrors() throws Exception {
        String body = """
                {"username":"a b","fullName":"","role":"AGENT","phone":"123","password":"weak"}""";
        mvc.perform(asAdmin(post(URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        verify(users, never()).save(any());
    }

    @Test
    void businessRuleViolationReturns400WithMessage() throws Exception {
        when(users.existsByUsernameIgnoreCase("doctor02")).thenReturn(true);
        mvc.perform(asAdmin(post(URL)).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("already taken")));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(asAdmin(post(URL)).content("{not json")).andExpect(status().isBadRequest());
    }

    @Test
    void updateUnknownUserReturns404() throws Exception {
        when(users.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(asAdmin(put(URL + "/99")).content("""
                        {"fullName":"X","role":"AGENT","active":true}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChangesRole() throws Exception {
        when(users.findById(3L)).thenReturn(Optional.of(doctor()));
        mvc.perform(asAdmin(put(URL + "/3")).content("""
                        {"fullName":"Dr. Meera","role":"COUNSELLOR","extensionNo":"3009","active":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COUNSELLOR"))
                .andExpect(jsonPath("$.extensionNo").value("3009"));
    }

    @Test
    void resetPasswordRequiresStrongPassword() throws Exception {
        mvc.perform(asAdmin(post(URL + "/3/reset-password")).content("{\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void resetPasswordSucceeds() throws Exception {
        when(users.findById(3L)).thenReturn(Optional.of(doctor()));
        mvc.perform(asAdmin(post(URL + "/3/reset-password")).content("{\"password\":\"NewPass@1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
