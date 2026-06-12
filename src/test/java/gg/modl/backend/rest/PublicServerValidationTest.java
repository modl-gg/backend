package gg.modl.backend.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.proto.ProtoBinaryHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoJsonHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoValidationAdvice;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.server.controller.PublicServerController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PublicServerValidationTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PublicServerController())
            .setControllerAdvice(new GlobalExceptionHandler(), new ProtoValidationAdvice())
            .setValidator(validator)
            .setMessageConverters(new ProtoJsonHttpMessageConverter(), new ProtoBinaryHttpMessageConverter(), new JacksonJsonHttpMessageConverter())
            .build();
    }

    @Test
    void registerRejectsInvalidBodyWithCentralizedError() throws Exception {
        mockMvc.perform(post(RESTMappingV1.PUBLIC_SERVER + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Invalid data provided."))
            .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void registerRejectsMalformedJsonWithCentralizedError() throws Exception {
        mockMvc.perform(post(RESTMappingV1.PUBLIC_SERVER + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Invalid data provided."))
            .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void legacyRegisterEndpointIsGoneForValidRequests() throws Exception {
        mockMvc.perform(post(RESTMappingV1.PUBLIC_SERVER + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin@example.com",
                      "serverName": "Example Server",
                      "customDomain": "example",
                      "turnstileToken": "token"
                    }
                    """))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void legacyAvailabilityEndpointIsGone() throws Exception {
        mockMvc.perform(post(RESTMappingV1.PUBLIC_SERVER + "/check-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin@example.com",
                      "serverName": "Example Server",
                      "customDomain": "example"
                    }
                    """))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("Use /v1/public/registration/check-availability instead."));
    }
}
