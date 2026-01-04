package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickResponseSettings {
    private List<Category> categories;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Category {
        private String id;
        private String name;
        private List<Action> actions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Action {
        private String id;
        private String name;
        private String message;
        private Boolean issuePunishment;
        private String appealAction;
        private Boolean closeTicket;
    }
}
