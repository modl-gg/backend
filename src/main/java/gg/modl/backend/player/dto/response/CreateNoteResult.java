package gg.modl.backend.player.dto.response;

public sealed interface CreateNoteResult permits CreateNoteResult.Created, CreateNoteResult.NotFound {

    record Created(String message) implements CreateNoteResult {
    }

    record NotFound(String message) implements CreateNoteResult {
    }
}
