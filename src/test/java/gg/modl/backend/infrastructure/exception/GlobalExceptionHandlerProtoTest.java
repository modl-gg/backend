package gg.modl.backend.infrastructure.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.infrastructure.proto.ProtoBinaryHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoJsonHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoValidationAdvice;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.infrastructure.rest.RESTMappingV3;
import gg.modl.backend.settings.service.SettingsConflictException;
import gg.modl.proto.modl.v1.ApiError;
import gg.modl.proto.modl.v1.CreatePlayerRequest;
import gg.modl.proto.modl.v1.SimpleResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.validation.annotation.Validated;

class GlobalExceptionHandlerProtoTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProtoTestController())
            .setControllerAdvice(new GlobalExceptionHandler(new ProtobufErrorResponseWriter()), new ProtoValidationAdvice())
            .setMessageConverters(
                new ProtoBinaryHttpMessageConverter(),
                new ProtoJsonHttpMessageConverter(),
                new JacksonJsonHttpMessageConverter()
            )
            .build();
    }

    @Test
    void v3MalformedBinaryBodyReturnsBinaryApiError() throws Exception {
        MvcResult result = mockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/echo")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(new byte[] {0x7f, 0x00}))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertEquals("Invalid data provided.", error.getMessage());
    }

    @Test
    void v3UnsupportedContentTypeReturnsBinaryApiError() throws Exception {
        MvcResult result = mockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content("plain"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(415, error.getStatusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", error.getCode());
    }

    @Test
    void v3NotAcceptableReturnsBinaryApiError() throws Exception {
        MvcResult result = mockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/not-acceptable")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(MediaType.APPLICATION_JSON)
                .content(SimpleResponse.newBuilder().setSuccess(true).build().toByteArray()))
            .andExpect(status().isNotAcceptable())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(406, error.getStatusCode());
        assertEquals("NOT_ACCEPTABLE", error.getCode());
    }

    @Test
    void v3ProtoValidationFailureReturnsBinaryApiErrorWithFieldViolations() throws Exception {
        CreatePlayerRequest invalidRequest = CreatePlayerRequest.newBuilder()
            .setMinecraftUuid("not-a-uuid")
            .setUsername("invalid name")
            .build();

        MvcResult result = mockMvc.perform(post(RESTMappingV3.PREFIX_MINECRAFT + "/create-player")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(invalidRequest.toByteArray()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertFalse(error.getFieldViolationsList().isEmpty());
    }

    @Test
    void v3ApplicationExceptionReturnsBinaryApiError() throws Exception {
        MvcResult result = mockMvc.perform(get(RESTMappingV3.PREFIX_MINECRAFT + "/missing-resource")
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(404, error.getStatusCode());
        assertEquals("NOT_FOUND", error.getCode());
        assertEquals("Role not found", error.getMessage());
    }

    @Test
    void v3SettingsConflictReturnsBinaryApiError() throws Exception {
        MvcResult result = mockMvc.perform(get(RESTMappingV3.PREFIX_MINECRAFT + "/settings-conflict")
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(409, error.getStatusCode());
        assertEquals("CONFLICT", error.getCode());
        assertEquals("Settings version conflict", error.getMessage());
    }

    @Test
    void v3ConstraintViolationReturnsBinaryApiError() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", RESTMappingV3.PREFIX_MINECRAFT + "/validated-hours");
        request.addHeader("Accept", ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE);

        ResponseEntity<?> response = new GlobalExceptionHandler(new ProtobufErrorResponseWriter())
            .handleConstraintViolation(new ConstraintViolationException("invalid", Set.of()), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(ProtobufMediaTypes.APPLICATION_X_PROTOBUF, response.getHeaders().getContentType());
        ApiError error = (ApiError) response.getBody();
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertEquals("Invalid data provided.", error.getMessage());
    }

    @Test
    void v3HandlerMethodValidationReturnsBinaryApiError() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", RESTMappingV3.PREFIX_MINECRAFT + "/validated-hours");
        request.addHeader("Accept", ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE);

        ResponseEntity<?> response = new GlobalExceptionHandler(new ProtobufErrorResponseWriter())
            .handleHandlerMethodValidation(null, request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(ProtobufMediaTypes.APPLICATION_X_PROTOBUF, response.getHeaders().getContentType());
        ApiError error = (ApiError) response.getBody();
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertEquals("Invalid data provided.", error.getMessage());
    }

    @Test
    void v3QueryParamTypeMismatchReturnsBinaryApiError() throws Exception {
        MvcResult result = mockMvc.perform(get(RESTMappingV3.PREFIX_MINECRAFT + "/validated-hours")
                .queryParam("hours", "abc")
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn();

        ApiError error = ApiError.parseFrom(result.getResponse().getContentAsByteArray());
        assertEquals(400, error.getStatusCode());
        assertEquals("INVALID_ARGUMENT", error.getCode());
        assertEquals("Invalid value for parameter: hours", error.getMessage());
    }

    @Test
    void v3MethodNotSupportedReturnsBinaryApiError() {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", RESTMappingV3.PREFIX_MINECRAFT + "/echo");
        request.addHeader("Accept", ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE);

        ResponseEntity<?> response = new GlobalExceptionHandler(new ProtobufErrorResponseWriter()).handleMethodNotSupported(
            new HttpRequestMethodNotSupportedException("GET"),
            request
        );

        assertEquals(405, response.getStatusCode().value());
        assertEquals(ProtobufMediaTypes.APPLICATION_X_PROTOBUF, response.getHeaders().getContentType());
        ApiError error = (ApiError) response.getBody();
        assertEquals(405, error.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", error.getCode());
        assertEquals("HTTP method not supported: GET", error.getMessage());
    }

    @Test
    void v3ErrorDispatchReturnsBinaryApiError() throws Exception {
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/error");
        request.addHeader("Accept", ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VALUE);
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, RESTMappingV3.PREFIX_MINECRAFT + "/missing");

        ResponseEntity<?> response = new CustomErrorController(new ProtobufErrorResponseWriter()).handleError(request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals(ProtobufMediaTypes.APPLICATION_X_PROTOBUF, response.getHeaders().getContentType());
        ApiError error = (ApiError) response.getBody();
        assertEquals(404, error.getStatusCode());
        assertEquals("NOT_FOUND", error.getCode());
        assertEquals("The requested resource was not found.", error.getMessage());
    }

    @Test
    void malformedAcceptHeaderFallsBackToJsonErrorInsteadOfThrowing() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.addHeader("Accept", "text/plain;q=abc");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 401);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/unknown");

        ResponseEntity<?> response = new CustomErrorController(new ProtobufErrorResponseWriter()).handleError(request);

        assertEquals(401, response.getStatusCode().value());
        assertNull(response.getHeaders().getContentType());
        assertTrue(response.getBody() instanceof ErrorResponseDTO);
        ErrorResponseDTO error = (ErrorResponseDTO) response.getBody();
        assertEquals(401, error.status());
        assertEquals("Unauthorized", error.error());
    }

    @Test
    void v1ApplicationExceptionStillReturnsJsonError() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/minecraft/missing-resource")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        ErrorResponseDTO error = objectMapper
            .readValue(result.getResponse().getContentAsByteArray(), ErrorResponseDTO.class);
        assertEquals(404, error.status());
        assertEquals("Role not found", error.error());
    }

    @Test
    void v1QueryParamTypeMismatchStillReturnsJsonError() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/minecraft/validated-hours")
                .queryParam("hours", "abc")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        ErrorResponseDTO error = objectMapper
            .readValue(result.getResponse().getContentAsByteArray(), ErrorResponseDTO.class);
        assertEquals(400, error.status());
        assertEquals("Invalid value for parameter: hours", error.error());
    }

    @Controller
    @Validated
    private static class ProtoTestController {
        @PostMapping(
            value = RESTMappingV3.PREFIX_MINECRAFT + "/echo",
            consumes = "application/x-protobuf",
            produces = "application/x-protobuf"
        )
        @ResponseBody
        SimpleResponse echo(@RequestBody SimpleResponse request) {
            return request;
        }

        @PostMapping(
            value = RESTMappingV3.PREFIX_MINECRAFT + "/create-player",
            consumes = "application/x-protobuf",
            produces = "application/x-protobuf"
        )
        @ResponseBody
        SimpleResponse createPlayer(@RequestBody CreatePlayerRequest request) {
            return SimpleResponse.newBuilder().setSuccess(true).build();
        }

        @PostMapping(
            value = RESTMappingV3.PREFIX_MINECRAFT + "/not-acceptable",
            consumes = "application/x-protobuf",
            produces = "application/x-protobuf"
        )
        @ResponseBody
        SimpleResponse notAcceptable(@RequestBody SimpleResponse request) throws HttpMediaTypeNotAcceptableException {
            throw new HttpMediaTypeNotAcceptableException("Not acceptable");
        }

        @GetMapping({
            RESTMappingV3.PREFIX_MINECRAFT + "/missing-resource",
            "/v1/minecraft/missing-resource"
        })
        @ResponseBody
        SimpleResponse missingResource() {
            throw new ResourceNotFoundException("Role not found");
        }

        @GetMapping({
            RESTMappingV3.PREFIX_MINECRAFT + "/validated-hours",
            "/v1/minecraft/validated-hours"
        })
        @ResponseBody
        SimpleResponse validatedHours(@RequestParam @Min(1) @Max(8760) int hours) {
            return SimpleResponse.newBuilder().setSuccess(true).build();
        }

        @GetMapping(RESTMappingV3.PREFIX_MINECRAFT + "/settings-conflict")
        @ResponseBody
        SimpleResponse settingsConflict() {
            throw new SettingsConflictException("Settings version conflict", 7);
        }
    }
}
