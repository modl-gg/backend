package gg.modl.backend.replay.data;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayLabelValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvertedTimeRanges() {
        ReplayLabel label = new ReplayLabel();
        label.setUuid("11111111-2222-3333-4444-555555555555");
        label.setVerdict("cheating");
        ReplayLabel.CheatDetail cheat = new ReplayLabel.CheatDetail();
        cheat.setType("reach");
        ReplayLabel.TimeRange range = new ReplayLabel.TimeRange();
        range.setStartMs(10_000L);
        range.setEndMs(5_000L);
        cheat.setTimeRanges(List.of(range));
        label.setCheats(List.of(cheat));

        assertFalse(validator.validate(label).isEmpty());
    }
}
