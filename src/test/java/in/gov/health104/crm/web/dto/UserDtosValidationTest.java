package in.gov.health104.crm.web.dto;

import in.gov.health104.crm.web.dto.UserDtos.CreateUserRequest;
import in.gov.health104.crm.web.dto.UserDtos.ResetPasswordRequest;
import in.gov.health104.crm.web.dto.UserDtos.UpdateUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Input validation rules on the user-management request bodies. */
class UserDtosValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void init() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }

    private static CreateUserRequest valid() {
        return new CreateUserRequest("doctor02", "Dr. Rao", "MEDICAL_OFFICER", "9876543210", "rao@example.gov.in", "Durg", "2002", "Secret@123");
    }

    private static CreateUserRequest with(String username, String phone, String email, String ext, String password) {
        return new CreateUserRequest(username, "Name", "AGENT", phone, email, null, ext, password);
    }

    private static <T> Set<String> invalidFields(T obj) {
        return validator.validate(obj).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void validRequestPasses() {
        assertThat(invalidFields(valid())).isEmpty();
    }

    @Test
    void optionalFieldsMayBeBlank() {
        assertThat(invalidFields(new CreateUserRequest("agent9", "Name", "AGENT", "", null, null, "", "Secret@123"))).isEmpty();
    }

    @Test
    void requiredFieldsMustBePresent() {
        assertThat(invalidFields(new CreateUserRequest("", " ", "", null, null, null, null, "")))
                .contains("username", "fullName", "role", "password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "has space", "bad!char", "x@y", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" /* 51 */})
    void rejectsBadUsernames(String username) {
        assertThat(invalidFields(with(username, null, null, null, "Secret@123"))).containsExactly("username");
    }

    @ParameterizedTest
    @ValueSource(strings = {"doc", "doctor.02", "agent_9", "cmho-raipur", "Doctor02"})
    void acceptsGoodUsernames(String username) {
        assertThat(invalidFields(with(username, null, null, null, "Secret@123"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1!", "alllettersonly!", "12345678!", "NoSymbol123", "        "})
    void rejectsWeakPasswords(String password) {
        assertThat(invalidFields(with("agent9", null, null, null, password))).containsExactly("password");
        assertThat(invalidFields(new ResetPasswordRequest(password))).containsExactly("password");
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimit() {
        String tooLong = "Aa1!" + "x".repeat(69); // 73 chars
        assertThat(invalidFields(new ResetPasswordRequest(tooLong))).containsExactly("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "5876543210", "98765432101", "98765abcde"})
    void rejectsBadMobile(String phone) {
        assertThat(invalidFields(with("agent9", phone, null, null, "Secret@123"))).containsExactly("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "12345678901", "20a1"})
    void rejectsBadExtension(String ext) {
        assertThat(invalidFields(with("agent9", null, null, ext, "Secret@123"))).containsExactly("extensionNo");
    }

    @Test
    void rejectsBadEmail() {
        assertThat(invalidFields(with("agent9", null, "not-an-email", null, "Secret@123"))).containsExactly("email");
    }

    @Test
    void updateRequestValidatesSameContactRules() {
        assertThat(invalidFields(new UpdateUserRequest("", "", "123", "bad", null, "x", true)))
                .containsExactlyInAnyOrder("fullName", "role", "phone", "email", "extensionNo");
    }
}
