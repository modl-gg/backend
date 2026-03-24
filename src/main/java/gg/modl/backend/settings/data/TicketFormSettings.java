package gg.modl.backend.settings.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketFormSettings {
    private TicketForm bug;
    private TicketForm support;
    private TicketForm application;
    private TicketForm player;
    private TicketForm chat;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketForm {
        @Builder.Default
        private boolean requireEmail = false;
        @Builder.Default
        private boolean requireEmailAuth = false;
        @Builder.Default
        private boolean allowEmailNotifications = true;
        @Builder.Default
        private List<FormField> fields = new ArrayList<>();
        @Builder.Default
        private List<FormSection> sections = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormField {
        private String id;
        private String type;
        private String label;
        private String description;
        private boolean required;
        @Builder.Default
        private List<String> options = new ArrayList<>();
        private int order;
        private String sectionId;
        private String goToSection;
        @Builder.Default
        private Map<String, String> optionSectionMapping = new HashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormSection {
        private String id;
        private String title;
        private String description;
        private int order;
        private String showIfFieldId;
        private String showIfValue;
        @Builder.Default
        private List<String> showIfValues = new ArrayList<>();
        private boolean hideByDefault;
    }
}
