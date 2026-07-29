package gg.modl.backend.infrastructure.proto;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ProtoMessageConverterConfig implements WebMvcConfigurer {

    private final ProtoJsonHttpMessageConverter protoJsonConverter = new ProtoJsonHttpMessageConverter();
    private final ProtoBinaryHttpMessageConverter protoBinaryConverter = new ProtoBinaryHttpMessageConverter();

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(protoJsonConverter);
        builder.addCustomConverter(protoBinaryConverter);
    }
}
