package gg.modl.backend.config.converter;

import gg.modl.backend.player.data.NoteEntry;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class NoteEntryReadConverter implements Converter<Document, NoteEntry> {

    @Override
    public NoteEntry convert(Document source) {
        NoteEntry entry = new NoteEntry();

        Object idObj = source.get("_id");
        if (idObj != null) {
            entry.setId(idObj.toString());
        }

        entry.setText(source.getString("text"));
        entry.setDate(source.getDate("date"));
        entry.setIssuerName(source.getString("issuerName"));
        entry.setIssuerId(source.getString("issuerId"));

        return entry;
    }
}
