package dev.nerviz.bankapp.infrastructure.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.usecase.customer.CreateCustomerUseCase;
import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.CustomerId;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice: HTTP contract only. Business rules have their own test at the domain and
 * application levels; here a domain exception only has to come out as the right status
 * and {@code errorCode}. Idempotent replay and reused-key cases run end to end in
 * {@code CreateCustomerIdempotencyIT} — a double here proves nothing about a replay.
 */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final String VALID_KEY = "0f2b6c1e-4d3a-4f5b-9c8d-7e6f5a4b3c2d";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCustomerUseCase createCustomer;

    /** ApiExceptionHandler injects Tracer. Without this double the slice doesn't start. */
    @MockitoBean
    private Tracer tracer;

    @Test
    void createsCustomerReturningLocationAndIdOnly() throws Exception {
        CustomerId id = CustomerId.of(UUID.randomUUID());
        given(createCustomer.create(any())).willReturn(id);

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", CustomerController.BASE_PATH + "/" + id.value()))
                .andExpect(jsonPath("$.id").value(id.value().toString()));
    }

    @Test
    void rejectsUnderage() throws Exception {
        willThrow(new BusinessRuleViolationException("CUSTOMER_UNDERAGE", "customer must be over 18 years old"))
                .given(createCustomer)
                .create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_UNDERAGE"));
    }

    @Test
    void rejectsDuplicateSecurityNumber() throws Exception {
        willThrow(new SecurityNumberAlreadyRegisteredException("security number already registered"))
                .given(createCustomer)
                .create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SECURITY_NUMBER_ALREADY_REGISTERED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "123.456.789-01"})
    void rejectsInvalidSecurityNumber(String invalid) throws Exception {
        willThrow(new ValidationException("SECURITY_NUMBER_INVALID", "security number must have exactly 11 digits"))
                .given(createCustomer)
                .create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria Silva","securityNumber":"%s","birthDate":"1990-05-17"}
                                """.formatted(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SECURITY_NUMBER_INVALID"));
    }

    @Test
    void rejectsBlankName() throws Exception {
        willThrow(new ValidationException("CUSTOMER_NAME_REQUIRED", "name is required"))
                .given(createCustomer)
                .create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   ","securityNumber":"12345678901","birthDate":"1990-05-17"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NAME_REQUIRED"));
    }

    @ParameterizedTest
    @MethodSource("outOfLengthNames")
    void rejectsNameOutsideLength(String name) throws Exception {
        willThrow(new ValidationException("CUSTOMER_NAME_LENGTH", "name must be between 2 and 120 characters"))
                .given(createCustomer)
                .create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","securityNumber":"12345678901","birthDate":"1990-05-17"}
                                """.formatted(name)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NAME_LENGTH"));
    }

    private static Stream<Arguments> outOfLengthNames() {
        return Stream.of(Arguments.of("a"), Arguments.of("a".repeat(121)));
    }

    @Test
    void rejectsFutureBirthDate() throws Exception {
        willThrow(new ValidationException("BIRTH_DATE_IN_FUTURE", "birth date cannot be in the future"))
                .given(createCustomer)
                .create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria Silva","securityNumber":"12345678901","birthDate":"2099-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BIRTH_DATE_IN_FUTURE"));
    }

    @Test
    void rejectsAgeOver130() throws Exception {
        willThrow(new ValidationException("BIRTH_DATE_TOO_OLD", "birth date exceeds the maximum age"))
                .given(createCustomer)
                .create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria Silva","securityNumber":"12345678901","birthDate":"1800-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BIRTH_DATE_TOO_OLD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "securityNumber", "birthDate"})
    void rejectsMissingField(String missingField) throws Exception {
        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissing(missingField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value(missingField))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    private static String bodyMissing(String field) {
        var fields = new java.util.LinkedHashMap<String, String>();
        fields.put("name", "\"Maria Silva\"");
        fields.put("securityNumber", "\"12345678901\"");
        fields.put("birthDate", "\"1990-05-17\"");
        fields.remove(field);
        return fields.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .reduce((a, b) -> a + "," + b)
                .map(body -> "{" + body + "}")
                .orElse("{}");
    }

    @Test
    void rejectsUnparseableBirthDate() throws Exception {
        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria Silva","securityNumber":"12345678901","birthDate":"17/05/1990"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hidesInternalFailureBehindTraceId() throws Exception {
        willThrow(new IllegalStateException("boom")).given(createCustomer).create(any());

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.detail").value("Internal error"));
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    void neverEchoesPersonalDataInErrors(RuntimeException thrown) throws Exception {
        willThrow(thrown).given(createCustomer).create(any());

        String body = mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain(CustomerFixtures.SECURITY_NUMBER)
                .doesNotContain(CustomerFixtures.BIRTH_DATE.toString());
    }

    private static Stream<Arguments> errorResponses() {
        return Stream.of(
                Arguments.of(new BusinessRuleViolationException("CUSTOMER_UNDERAGE", "customer must be over 18")),
                Arguments.of(new SecurityNumberAlreadyRegisteredException("already registered")),
                Arguments.of(new ValidationException("SECURITY_NUMBER_INVALID", "invalid")),
                Arguments.of(new IllegalStateException("boom")));
    }
}
