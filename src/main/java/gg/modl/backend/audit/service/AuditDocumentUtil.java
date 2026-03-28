package gg.modl.backend.audit.service;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.bson.Document;

@UtilityClass
class AuditDocumentUtil {

    String extractPlayerNameFromDoc(Document doc) {
        List<Document> usernames = doc.getList("usernames", Document.class);
        if (usernames != null && !usernames.isEmpty()) {
            String name = usernames.get(0).getString("username");
            if (name != null) {
                return name;
            }
        }
        return "Unknown";
    }

    boolean hasModificationType(Document doc, String... types) {
        List<?> modifications = doc.getList("modifications", Document.class);
        if (modifications == null) {
            return false;
        }
        for (Object mod : modifications) {
            if (mod instanceof Document modDoc) {
                String modType = modDoc.getString("type");
                for (String type : types) {
                    if (type.equalsIgnoreCase(modType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
