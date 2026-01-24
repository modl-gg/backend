package gg.modl.backend.config.converter;

import gg.modl.backend.player.data.IPEntry;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@ReadingConverter
public class ArrayListToIPEntryConverter implements Converter<ArrayList, IPEntry> {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public IPEntry convert(ArrayList source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        Object first = source.get(0);
        if (!(first instanceof Document doc)) {
            return null;
        }

        IPEntry entry = new IPEntry();
        entry.setIpAddress(doc.getString("ipAddress"));
        entry.setCountry(doc.getString("country"));
        entry.setRegion(doc.getString("region"));
        entry.setAsn(doc.getString("asn"));
        entry.setProxy(doc.getBoolean("proxy", false));
        entry.setHosting(doc.getBoolean("hosting", false));
        entry.setFirstLogin(doc.getDate("firstLogin"));

        List<Date> logins = new ArrayList<>();
        Object loginsObj = doc.get("logins");
        if (loginsObj instanceof List<?> loginsList) {
            for (Object login : loginsList) {
                if (login instanceof Date date) {
                    logins.add(date);
                }
            }
        }
        entry.setLogins(logins);

        return entry;
    }
}
