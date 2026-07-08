package gg.modl.backend.beta;

import java.util.List;

public record BetaTesterPage(List<BetaTesterDetails> items, int page, int limit, long total, int pages) {
}
