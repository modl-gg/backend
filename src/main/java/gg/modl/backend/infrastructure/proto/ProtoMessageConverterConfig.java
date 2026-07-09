package gg.modl.backend.infrastructure.proto;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProtoMessageConverterConfig implements WebMvcConfigurer {

    private final ProtoJsonHttpMessageConverter protoJsonConverter;
    private final ProtoBinaryHttpMessageConverter protoBinaryConverter;

    public ProtoMessageConverterConfig(ProtoJsonHttpMessageConverter protoJsonConverter,
                                       ProtoBinaryHttpMessageConverter protoBinaryConverter) {
        this.protoJsonConverter = protoJsonConverter;
        this.protoBinaryConverter = protoBinaryConverter;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(c -> c instanceof ProtoJsonHttpMessageConverter
            || c instanceof ProtoBinaryHttpMessageConverter);
        converters.add(0, protoBinaryConverter);
        converters.add(0, protoJsonConverter);
    }
}
