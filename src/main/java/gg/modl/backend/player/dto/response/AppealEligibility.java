package gg.modl.backend.player.dto.response;

public sealed interface AppealEligibility permits AppealEligibility.Eligible, AppealEligibility.NotStarted {

    record Eligible(AppealInfoView info) implements AppealEligibility {
    }

    record NotStarted(String message) implements AppealEligibility {
    }
}
