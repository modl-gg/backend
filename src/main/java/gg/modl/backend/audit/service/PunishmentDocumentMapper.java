package gg.modl.backend.audit.service;

import gg.modl.backend.audit.dto.response.ActivePunishmentResponse;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.bson.Document;

final class PunishmentDocumentMapper {
    private PunishmentDocumentMapper() {
    }

    static Punishment reconstructPunishment(Document doc) {
        Punishment punishment = new Punishment();
        String reconstructedId = doc.getString(AuditProjectionKeys.PUNISHMENT_ID);
        if (reconstructedId == null) {
            reconstructedId = doc.getString("id");
        }
        punishment.setId(reconstructedId);
        punishment.setTypeOrdinal(doc.getInteger(AuditProjectionKeys.TYPE_ORDINAL, 0));
        punishment.setIssuerName(
            doc.getString(AuditProjectionKeys.ISSUER_NAME) != null
                ? doc.getString(AuditProjectionKeys.ISSUER_NAME) : "Unknown");
        punishment.setIssuerId(doc.getString(AuditProjectionKeys.ISSUER_ID));
        punishment.setIssued(
            doc.getDate("issued") != null ? doc.getDate("issued") : new Date());
        punishment.setStarted(doc.getDate("started"));

        Document data = doc.get(AuditProjectionKeys.DATA, Document.class);
        if (data != null) {
            punishment.replaceData(new HashMap<>(data));
        }

        punishment.setModifications(extractModifications(doc));
        punishment.setNotes(Collections.emptyList());
        punishment.setEvidence(Collections.emptyList());
        punishment.setAttachedTicketIds(Collections.emptyList());

        return punishment;
    }

    static List<PunishmentModification> extractModifications(Document doc) {
        List<Document> modDocs = doc.getList(AuditProjectionKeys.MODIFICATIONS, Document.class);
        if (modDocs == null) {
            return new ArrayList<>();
        }

        List<PunishmentModification> mods = new ArrayList<>();
        for (Document modDoc : modDocs) {
            Long effectiveDuration = null;
            Object edObj = modDoc.get("effectiveDuration");
            if (edObj instanceof Number num) {
                effectiveDuration = num.longValue();
            }
            mods.add(new PunishmentModification(
                modDoc.getString("id"),
                modDoc.getString("type"),
                modDoc.getDate("date"),
                modDoc.getString(AuditProjectionKeys.ISSUER_NAME),
                modDoc.getString(AuditProjectionKeys.ISSUER_ID),
                modDoc.getString(AuditProjectionKeys.REASON),
                effectiveDuration,
                modDoc.getString("appealTicketId"),
                null
            ));
        }
        return mods;
    }

    static List<ActivePunishmentResponse.EvidenceItem> extractEvidenceItems(Document row) {
        List<Document> evidenceDocs = row.getList(AuditProjectionKeys.EVIDENCE, Document.class);
        if (evidenceDocs == null) {
            return Collections.emptyList();
        }

        List<ActivePunishmentResponse.EvidenceItem> items = new ArrayList<>();
        for (Document evidenceDoc : evidenceDocs) {
            items.add(new ActivePunishmentResponse.EvidenceItem(
                evidenceDoc.getString("text"),
                evidenceDoc.getString("url"),
                evidenceDoc.getString("type"),
                evidenceDoc.getString("fileName")
            ));
        }
        return items;
    }
}
