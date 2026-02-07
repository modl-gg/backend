package gg.modl.backend.config.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.time.Instant;
import java.util.Date;

@ReadingConverter
public class StringToDateReadConverter implements Converter<String, Date> {

    @Override
    public Date convert(String source) {
        return Date.from(Instant.parse(source));
    }
}
