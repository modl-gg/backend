package gg.modl.backend.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DeleteLogsRequest(
        @NotEmpty List<String> logIds
) {}
