package gg.modl.backend.settings.service;

import java.util.Date;

public record VersionedSettings<T>(T data, long version, Date updatedAt) {
}
