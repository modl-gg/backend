package gg.modl.backend.infrastructure.proto;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ProtoMessageConverterConfig implements WebMvcConfigurer {

    private final ProtoJsonHttpMessageConverter protoJsonConverter;
    private final ProtoBinaryHttpMessageConverter protoBinaryConverter;

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(protoJsonConverter);
        builder.addCustomConverter(protoBinaryConverter);
    }
}
