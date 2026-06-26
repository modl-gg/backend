package gg.modl.backend.infrastructure.proto;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Ensures the protobuf message converters take precedence over the framework defaults.
 *
 * <p>{@link ProtoJsonHttpMessageConverter} and {@link ProtoBinaryHttpMessageConverter} are
 * registered as {@code @Component} beans, which Spring appends to the converter list <em>after</em>
 * the default Jackson JSON converter. For a protobuf {@code @RequestBody} sent as
 * {@code application/json}, Jackson would then win {@code canRead(...)} and deserialize the body
 * into an empty/default protobuf message (protobuf builders have no Jackson-writable setters), which
 * subsequently fails buf.validate on every field and surfaces as a generic 400 "Invalid data
 * provided." for every request — without the controller ever running.
 *
 * <p>Moving the protobuf converters to the front of the list makes them the selected reader/writer
 * for {@link com.google.protobuf.Message} types (both binary and proto-JSON) ahead of Jackson, while
 * non-protobuf types still fall through to Jackson (their {@code supports(...)} returns false).
 */
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
        // Drop the auto-registered instances (appended after Jackson) and re-add ours at the front.
        converters.removeIf(c -> c instanceof ProtoJsonHttpMessageConverter
            || c instanceof ProtoBinaryHttpMessageConverter);
        converters.add(0, protoBinaryConverter);
        converters.add(0, protoJsonConverter);
    }
}
