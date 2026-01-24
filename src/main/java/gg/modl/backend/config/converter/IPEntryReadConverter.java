package gg.modl.backend.config.converter;

import gg.modl.backend.player.data.IPEntry;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@ReadingConverter
public class IPEntryReadConverter implements Converter<Document, IPEntry> {

    @Override
    public IPEntry convert(Document source) {
        IPEntry entry = new IPEntry();
        entry.setIpAddress(source.getString("ipAddress"));
        entry.setCountry(source.getString("country"));
        entry.setRegion(source.getString("region"));
        entry.setAsn(source.getString("asn"));
        entry.setProxy(source.getBoolean("proxy", false));
        entry.setHosting(source.getBoolean("hosting", false));
        entry.setFirstLogin(source.getDate("firstLogin"));

        List<Date> logins = new ArrayList<>();
        Object loginsObj = source.get("logins");
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
