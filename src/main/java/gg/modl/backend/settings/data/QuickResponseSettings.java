package gg.modl.backend.settings.data;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickResponseSettings {
    @Builder.Default
    private List<Category> categories = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Category {
        private String id;
        private String name;
        @Builder.Default
        private List<String> ticketTypes = new ArrayList<>();
        @Builder.Default
        private List<Action> actions = new ArrayList<>();
        private Integer order;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Action {
        private String id;
        private String name;
        private String message;
        private Integer order;
        private Boolean closeTicket;
        private Boolean showPunishment;
        private String appealAction;
    }
}
