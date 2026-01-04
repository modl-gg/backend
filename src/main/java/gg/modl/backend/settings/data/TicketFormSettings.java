package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
        private List<FormField> fields;
        private List<FormSection> sections;
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
        private List<String> options;
        private int order;
        private String sectionId;
        private String goToSection;
        private Map<String, String> optionSectionMapping;
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
        private List<String> showIfValues;
        private boolean hideByDefault;
    }
}
