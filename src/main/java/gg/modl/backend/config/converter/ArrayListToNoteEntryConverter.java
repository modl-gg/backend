package gg.modl.backend.config.converter;

import gg.modl.backend.player.data.NoteEntry;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.ArrayList;

@ReadingConverter
public class ArrayListToNoteEntryConverter implements Converter<ArrayList, NoteEntry> {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public NoteEntry convert(ArrayList source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        Object first = source.get(0);
        if (!(first instanceof Document doc)) {
            return null;
        }

        NoteEntry entry = new NoteEntry();

        Object idObj = doc.get("_id");
        if (idObj != null) {
            entry.setId(idObj.toString());
        }

        entry.setText(doc.getString("text"));
        entry.setDate(doc.getDate("date"));
        entry.setIssuerName(doc.getString("issuerName"));
        entry.setIssuerId(doc.getString("issuerId"));

        return entry;
    }
}
