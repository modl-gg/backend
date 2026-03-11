package gg.modl.backend.settings.data;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketLabelSettings {
    @Builder.Default
    private List<Label> labels = new ArrayList<>();
}
