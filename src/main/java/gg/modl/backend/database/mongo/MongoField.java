package gg.modl.backend.database.mongo;

public record MongoField<T>(String path) {
    public MongoField {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Mongo field path must not be blank");
        }
    }

    @Override
    public String toString() {
        return path;
    }
}
