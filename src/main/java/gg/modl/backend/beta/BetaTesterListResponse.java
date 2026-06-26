package gg.modl.backend.beta;

import java.util.List;

public record BetaTesterListResponse(List<BetaTesterRecord> betaTesters, Pagination pagination) {
    public record Pagination(int page, int limit, long total, int pages) {
    }
}
