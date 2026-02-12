package gg.modl.backend.config.converter;

import gg.modl.backend.server.data.CustomDomainStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class StringToCustomDomainStatusConverter implements Converter<String, CustomDomainStatus> {

    @Override
    public CustomDomainStatus convert(String source) {
        if (source.isEmpty()) {
            return null;
        }
        return CustomDomainStatus.valueOf(source);
    }
}
