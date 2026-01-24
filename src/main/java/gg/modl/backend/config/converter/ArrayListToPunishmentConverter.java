package gg.modl.backend.config.converter;

import gg.modl.backend.player.data.punishment.Punishment;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.ArrayList;

@ReadingConverter
public class ArrayListToPunishmentConverter implements Converter<ArrayList, Punishment> {

    private final PunishmentReadConverter punishmentReadConverter = new PunishmentReadConverter();

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Punishment convert(ArrayList source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        Object first = source.get(0);
        if (!(first instanceof Document doc)) {
            return null;
        }

        return punishmentReadConverter.convert(doc);
    }
}
