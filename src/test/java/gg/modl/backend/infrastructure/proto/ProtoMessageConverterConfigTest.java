package gg.modl.backend.infrastructure.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.proto.modl.v1.ConfigureDomainRequest;
import gg.modl.proto.modl.v1.PublicSettingsResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

@SpringJUnitWebConfig(ProtoMessageConverterConfigTest.BootAssembledMvcContext.class)
class ProtoMessageConverterConfigTest {

    private static final String DISPLAY_NAME = "Localtest";
    private static final String CUSTOM_DOMAIN = "localtest.modl.gg";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void protoConvertersAreRegisteredOnceAheadOfJackson() {
        List<HttpMessageConverter<?>> converters = handlerAdapter.getMessageConverters();

        assertEquals(1, count(converters, ProtoJsonHttpMessageConverter.class), "proto JSON converter registered once");
        assertEquals(1, count(converters, ProtoBinaryHttpMessageConverter.class), "proto binary converter registered once");
        assertEquals(1, count(converters, JacksonJsonHttpMessageConverter.class), "Jackson JSON converter still registered");
        assertTrue(indexOf(converters, ProtoJsonHttpMessageConverter.class)
            < indexOf(converters, ProtoBinaryHttpMessageConverter.class), "proto JSON converter must precede proto binary");
        assertTrue(indexOf(converters, ProtoJsonHttpMessageConverter.class)
            < indexOf(converters, JacksonJsonHttpMessageConverter.class), "proto JSON converter must precede Jackson");
    }

    @Test
    void acceptAnyNegotiatesJson() throws Exception {
        mvc.perform(get("/settings").accept(MediaType.ALL))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.serverDisplayName").value(DISPLAY_NAME));
    }

    @Test
    void acceptJsonNegotiatesJson() throws Exception {
        mvc.perform(get("/settings").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.serverDisplayName").value(DISPLAY_NAME));
    }

    @Test
    void acceptProtobufNegotiatesBinaryProto() throws Exception {
        byte[] body = mvc.perform(get("/settings").accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(ProtobufMediaTypes.APPLICATION_X_PROTOBUF))
            .andReturn().getResponse().getContentAsByteArray();

        assertEquals(DISPLAY_NAME, PublicSettingsResponse.parseFrom(body).getServerDisplayName());
    }

    @Test
    void binaryProtoRequestBodyIsDeserialized() throws Exception {
        byte[] request = ConfigureDomainRequest.newBuilder().setCustomDomain(CUSTOM_DOMAIN).build().toByteArray();

        byte[] body = mvc.perform(post("/settings/domain")
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .accept(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .content(request))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        assertEquals(CUSTOM_DOMAIN, PublicSettingsResponse.parseFrom(body).getServerDisplayName());
    }

    @Test
    void jsonProtoRequestBodyIsDeserialized() throws Exception {
        mvc.perform(post("/settings/domain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customDomain\":\"" + CUSTOM_DOMAIN + "\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.serverDisplayName").value(CUSTOM_DOMAIN));
    }

    @Test
    void nonProtoTypesStillNegotiateJacksonJson() throws Exception {
        mvc.perform(get("/plain").accept(MediaType.ALL))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value(DISPLAY_NAME));
    }

    private static long count(List<HttpMessageConverter<?>> converters, Class<?> type) {
        return converters.stream().filter(type::isInstance).count();
    }

    private static int indexOf(List<HttpMessageConverter<?>> converters, Class<?> type) {
        for (int i = 0; i < converters.size(); i++) {
            if (type.isInstance(converters.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Configuration
    @ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
    })
    @ComponentScan(basePackageClasses = ProtoMessageConverterConfig.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SettingsEndpoint.class))
    @Import(SettingsEndpoint.class)
    static class BootAssembledMvcContext {
    }

    @RestController
    static class SettingsEndpoint {

        @GetMapping("/settings")
        PublicSettingsResponse settings() {
            return PublicSettingsResponse.newBuilder().setServerDisplayName(DISPLAY_NAME).build();
        }

        @PostMapping("/settings/domain")
        PublicSettingsResponse configureDomain(@RequestBody ConfigureDomainRequest request) {
            return PublicSettingsResponse.newBuilder().setServerDisplayName(request.getCustomDomain()).build();
        }

        @GetMapping("/plain")
        PlainPayload plain() {
            return new PlainPayload(DISPLAY_NAME);
        }
    }

    record PlainPayload(String name) {
    }
}
