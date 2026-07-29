package gg.modl.backend.punishment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import gg.modl.backend.player.dto.response.AppealInfoView;
import gg.modl.proto.modl.v1.PublicPunishmentAppealInfoResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicPunishmentProtoMapperTest {

    @Test
    void mapsAppealFormMapIntoStruct() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", "reason");
        field.put("type", "textarea");
        field.put("label", "Reason");
        field.put("required", true);
        field.put("order", 0);

        AppealInfoView appealInfo = new AppealInfoView(
            "p-1", null, null, null, false, false, null, null,
            Map.of("fields", List.of(field), "sections", List.of()));

        PublicPunishmentAppealInfoResponse response = PublicPunishmentProtoMapper.toAppealInfo(appealInfo);

        assertTrue(response.hasAppealForm());
        Struct form = response.getAppealForm();
        Value fieldsValue = form.getFieldsOrThrow("fields");
        Struct firstField = fieldsValue.getListValue().getValues(0).getStructValue();
        assertEquals("reason", firstField.getFieldsOrThrow("id").getStringValue());
        assertTrue(firstField.getFieldsOrThrow("required").getBoolValue());
    }

    @Test
    void absentAppealFormLeavesFieldUnset() {
        AppealInfoView appealInfo = new AppealInfoView(
            "p-1", null, null, null, false, false, null, null, null);

        PublicPunishmentAppealInfoResponse response = PublicPunishmentProtoMapper.toAppealInfo(appealInfo);

        assertFalse(response.hasAppealForm());
    }
}
