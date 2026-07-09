package gg.modl.backend.ai.external;

import org.jetbrains.annotations.NotNull;

public interface LLMProvider {
    @NotNull
    String generate(@NotNull String systemInstruction, @NotNull String userContent);

    boolean isConnected();
}
