package gg.modl.backend.infrastructure.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.beta.SubdomainValidator;
import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.backend.registration.PublicRegistrationController;
import gg.modl.backend.registration.RegistrationService;
import gg.modl.backend.server.ServerService;
import gg.modl.proto.modl.v1.PublicRegistrationRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Guards the message-converter ordering fix: a protobuf {@code @RequestBody} sent as
 * {@code application/json} must be read by {@link ProtoJsonHttpMessageConverter}, not by the default
 * Jackson converter (which would yield an empty message and a spurious 400 for every request).
 */
class ProtoMessageConverterConfigTest {

    private static final String FORM_PAYLOAD =
        "{\"email\":\"info@byteful.me\",\"serverName\":\"test\",\"customDomain\":\"test1\","
            + "\"agreeTerms\":true,\"turnstileToken\":\"dummy\",\"plan\":\"free\"}";

    /** Mirrors Spring's assembly: the default Jackson converter ahead of the @Component proto beans. */
    private static List<HttpMessageConverter<?>> jacksonFirstList() {
        List<HttpMessageConverter<?>> list = new ArrayList<>();
        list.add(new JacksonJsonHttpMessageConverter());
        list.add(new ProtoJsonHttpMessageConverter());
        list.add(new ProtoBinaryHttpMessageConverter());
        return list;
    }

    private static void applyConfig(List<HttpMessageConverter<?>> converters) {
        new ProtoMessageConverterConfig(new ProtoJsonHttpMessageConverter(), new ProtoBinaryHttpMessageConverter())
            .extendMessageConverters(converters);
    }

    private static int indexOfType(List<HttpMessageConverter<?>> list, Class<?> type) {
        for (int i = 0; i < list.size(); i++) {
            if (type.isInstance(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void extendMovesProtoJsonAheadOfJacksonWithoutDuplicating() {
        List<HttpMessageConverter<?>> list = jacksonFirstList();
        applyConfig(list);

        int protoIdx = indexOfType(list, ProtoJsonHttpMessageConverter.class);
        int jacksonIdx = indexOfType(list, JacksonJsonHttpMessageConverter.class);
        assertTrue(protoIdx >= 0 && jacksonIdx >= 0, "both converters present");
        assertTrue(protoIdx < jacksonIdx, "proto JSON converter must precede Jackson");

        // The first converter able to read a protobuf type as JSON must be the proto one.
        HttpMessageConverter<?> firstReader = list.stream()
            .filter(c -> c.canRead(PublicRegistrationRequest.class, MediaType.APPLICATION_JSON))
            .findFirst().orElseThrow();
        assertInstanceOf(ProtoJsonHttpMessageConverter.class, firstReader);

        // No duplicate converters left behind.
        assertEquals(1, list.stream().filter(c -> c instanceof ProtoJsonHttpMessageConverter).count());
        assertEquals(1, list.stream().filter(c -> c instanceof ProtoBinaryHttpMessageConverter).count());
    }

    @Test
    void registrationJsonBodyIsParsedWhenConvertersAreReordered() throws Exception {
        List<HttpMessageConverter<?>> converters = jacksonFirstList();
        applyConfig(converters);

        MockMvc mvc = buildMockMvc(converters);
        // Body parsing must succeed so the controller runs; turnstile is mocked to fail, proving the
        // request reached the controller (rather than dying at proto validation with empty fields).
        mvc.perform(post(RESTMappingV1.PUBLIC_REGISTRATION)
                .contentType(MediaType.APPLICATION_JSON).content(FORM_PAYLOAD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Security verification")));
    }

    @Test
    void jacksonFirstWithoutReorderingFailsToParse() throws Exception {
        // Documents the bug: without the reordering, Jackson consumes the protobuf body and the
        // request dies at validation with the generic message, never reaching the controller.
        MockMvc mvc = buildMockMvc(jacksonFirstList());
        mvc.perform(post(RESTMappingV1.PUBLIC_REGISTRATION)
                .contentType(MediaType.APPLICATION_JSON).content(FORM_PAYLOAD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid data provided."));
    }

    private static MockMvc buildMockMvc(List<HttpMessageConverter<?>> converters) {
        ServerService serverService = mock(ServerService.class);
        RegistrationService registrationService = mock(RegistrationService.class);
        when(registrationService.performRegistration(any()))
            .thenReturn(new RegistrationService.RegistrationOutcome.Rejected(
                new RegistrationService.RegistrationRejection(
                    HttpStatus.BAD_REQUEST, "Security verification failed. Please try again.")));

        PublicRegistrationController controller = new PublicRegistrationController(
            serverService, mock(SessionService.class), registrationService, mock(CookieUtil.class),
            new SubdomainValidator());

        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler(), new ProtoValidationAdvice())
            .setMessageConverters(converters.toArray(new HttpMessageConverter<?>[0]))
            .build();
    }
}
