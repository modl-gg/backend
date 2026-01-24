package gg.modl.backend.config.converter;

import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@ReadingConverter
public class PunishmentReadConverter implements Converter<Document, Punishment> {

    @Override
    @SuppressWarnings("unchecked")
    public Punishment convert(Document source) {
        Punishment punishment = new Punishment();

        Object idObj = source.get("_id");
        if (idObj != null) {
            punishment.setId(idObj.toString());
        }

        punishment.setType_ordinal(source.getInteger("type_ordinal", 0));
        punishment.setIssuerName(source.getString("issuerName"));
        punishment.setIssued(source.getDate("issued"));
        punishment.setStarted(source.getDate("started"));

        List<PunishmentModification> modifications = new ArrayList<>();
        List<Document> modDocs = source.getList("modifications", Document.class);
        if (modDocs != null) {
            for (Document modDoc : modDocs) {
                modifications.add(convertModification(modDoc));
            }
        }
        punishment.setModifications(modifications);

        List<PunishmentNote> notes = new ArrayList<>();
        List<Document> noteDocs = source.getList("notes", Document.class);
        if (noteDocs != null) {
            for (Document noteDoc : noteDocs) {
                notes.add(convertNote(noteDoc));
            }
        }
        punishment.setNotes(notes);

        List<PunishmentEvidence> evidence = new ArrayList<>();
        List<Document> evidenceDocs = source.getList("evidence", Document.class);
        if (evidenceDocs != null) {
            for (Document evidenceDoc : evidenceDocs) {
                evidence.add(convertEvidence(evidenceDoc));
            }
        }
        punishment.setEvidence(evidence);

        List<String> attachedTicketIds = source.getList("attachedTicketIds", String.class);
        punishment.setAttachedTicketIds(attachedTicketIds != null ? attachedTicketIds : new ArrayList<>());

        Document dataDoc = source.get("data", Document.class);
        if (dataDoc != null) {
            punishment.setData((Map<String, Object>) new java.util.HashMap<>(dataDoc));
        }

        return punishment;
    }

    private PunishmentModification convertModification(Document doc) {
        Object idObj = doc.get("_id");
        String id = idObj != null ? idObj.toString() : null;

        return new PunishmentModification(
                id,
                doc.getString("type"),
                doc.getDate("date"),
                doc.getString("issuerName"),
                doc.getString("reason") != null ? doc.getString("reason") : "",
                doc.getLong("effectiveDuration"),
                doc.getString("appealTicketId"),
                doc.get("data", Document.class) != null ? new java.util.HashMap<>(doc.get("data", Document.class)) : null
        );
    }

    private PunishmentNote convertNote(Document doc) {
        Object idObj = doc.get("_id");
        String id = idObj != null ? idObj.toString() : null;

        return new PunishmentNote(
                id,
                doc.getString("text"),
                doc.getDate("date"),
                doc.getString("issuerName")
        );
    }

    private PunishmentEvidence convertEvidence(Document doc) {
        return new PunishmentEvidence(
                doc.getString("text"),
                doc.getString("url"),
                doc.getString("type") != null ? doc.getString("type") : "text",
                doc.getString("uploadedBy") != null ? doc.getString("uploadedBy") : "System",
                doc.getDate("uploadedAt") != null ? doc.getDate("uploadedAt") : new Date(),
                doc.getString("fileName"),
                doc.getString("fileType"),
                doc.getLong("fileSize")
        );
    }
}
