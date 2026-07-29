package gg.modl.backend.settings.service;

import gg.modl.backend.server.data.Server;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class VersionedSettingsSupport<T> {
    private final SettingsDocumentService documentService;
    private final String type;
    private final Function<Map<String, Object>, T> decoder;

    private VersionedSettingsSupport(
        SettingsDocumentService documentService,
        String type,
        Function<Map<String, Object>, T> decoder
    ) {
        this.documentService = documentService;
        this.type = type;
        this.decoder = decoder;
    }

    public static <T> VersionedSettingsSupport<T> of(
        SettingsDocumentService documentService,
        String type,
        Function<Map<String, Object>, T> decoder
    ) {
        return new VersionedSettingsSupport<>(documentService, type, decoder);
    }

    public T get(Server server) {
        return state(server).data();
    }

    public VersionedSettings<T> state(Server server) {
        return envelope(documentService.getRawState(server, type));
    }

    public VersionedSettings<T> save(Server server, long expectedVersion, Map<String, Object> data) {
        return envelope(documentService.saveRawState(server, type, expectedVersion, data));
    }

    public Map<String, Object> currentData(Server server) {
        return new LinkedHashMap<>(documentService.getRawState(server, type).data());
    }

    public long currentVersion(Server server) {
        return documentService.getRawState(server, type).version();
    }

    public void delete(Server server) {
        documentService.deleteState(server, type);
    }

    private VersionedSettings<T> envelope(SettingsDocumentService.RawSettingsState raw) {
        return new VersionedSettings<>(decoder.apply(raw.data()), raw.version(), raw.updatedAt());
    }
}
