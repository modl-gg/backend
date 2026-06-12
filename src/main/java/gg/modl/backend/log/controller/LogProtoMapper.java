package gg.modl.backend.log.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.log.dto.response.SystemLogResponse;
import gg.modl.proto.modl.v1.PanelLogResponse;
import gg.modl.proto.modl.v1.PanelLogsResponse;
import java.util.List;

final class LogProtoMapper {

    private LogProtoMapper() {
    }

    static PanelLogsResponse toPanelLogsResponse(List<SystemLogResponse> logs) {
        PanelLogsResponse.Builder builder = PanelLogsResponse.newBuilder();
        logs.forEach(log -> builder.addLogs(toPanelLogResponse(log)));
        return builder.build();
    }

    private static PanelLogResponse toPanelLogResponse(SystemLogResponse log) {
        return PanelLogResponse.newBuilder()
            .setId(stringValue(log.id()))
            .setDescription(stringValue(log.description()))
            .setLevel(stringValue(log.level()))
            .setSource(stringValue(log.source()))
            .setCreated(toTimestamp(log.created()))
            .build();
    }
}
