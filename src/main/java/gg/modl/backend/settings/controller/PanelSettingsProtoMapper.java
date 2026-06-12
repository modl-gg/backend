package gg.modl.backend.settings.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.booleanValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.intValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalBoolean;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalInt;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.AppealForm;
import gg.modl.backend.settings.data.AppealFormField;
import gg.modl.backend.settings.data.AppealFormSection;
import gg.modl.backend.settings.data.DomainSettings;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.Label;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.OffenseLevelDurations;
import gg.modl.backend.settings.data.PunishmentDurations;
import gg.modl.backend.settings.data.PunishmentPoints;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import gg.modl.backend.settings.data.TicketFormSettings;
import gg.modl.backend.settings.data.TicketLabelSettings;
import gg.modl.backend.settings.data.WebhookSettings;
import gg.modl.backend.settings.dto.response.PublicSettingsResponse;
import gg.modl.backend.settings.service.VersionedSettings;
import gg.modl.proto.modl.v1.ApiKeyDeleteResponse;
import gg.modl.proto.modl.v1.ApiKeyExistsResponse;
import gg.modl.proto.modl.v1.ApiKeyGenerateResponse;
import gg.modl.proto.modl.v1.ApiKeyRevealResponse;
import gg.modl.proto.modl.v1.AISuggestionActionResponse;
import gg.modl.proto.modl.v1.GeneralSettingsEnvelope;
import gg.modl.proto.modl.v1.OffenderThresholdSettingsEnvelope;
import gg.modl.proto.modl.v1.PanelPunishmentTypesResponse;
import gg.modl.proto.modl.v1.QuickResponseSettingsEnvelope;
import gg.modl.proto.modl.v1.RemoveDomainResponse;
import gg.modl.proto.modl.v1.ReplayRetentionSettingsEnvelope;
import gg.modl.proto.modl.v1.SettingsMeta;
import gg.modl.proto.modl.v1.TicketFormSettingsEnvelope;
import gg.modl.proto.modl.v1.TicketLabelSettingsEnvelope;
import gg.modl.proto.modl.v1.VerifyDomainResponse;
import gg.modl.proto.modl.v1.WebhookTestResponse;
import java.util.List;
import java.util.Map;

/**
 * Bridges the settings domain/service models to their generated {@code modl.v1} proto-JSON message types and back.
 * Outbound {@code to*} methods transcribe the {@code @Data} settings beans; inbound {@code from*} methods rebuild the
 * domain commands from request messages. Mirrors the conversion rules established by the minecraft V3 mappers.
 */
final class PanelSettingsProtoMapper {

    private PanelSettingsProtoMapper() {
    }

    // --- Envelopes ---

    static GeneralSettingsEnvelope toGeneralSettingsEnvelope(VersionedSettings<GeneralSettings> settings) {
        return GeneralSettingsEnvelope.newBuilder()
            .setData(toGeneralSettings(settings.data()))
            .setMeta(toSettingsMeta(settings))
            .build();
    }

    static TicketLabelSettingsEnvelope toTicketLabelSettingsEnvelope(VersionedSettings<TicketLabelSettings> settings) {
        return TicketLabelSettingsEnvelope.newBuilder()
            .setData(toTicketLabelSettings(settings.data()))
            .setMeta(toSettingsMeta(settings))
            .build();
    }

    static OffenderThresholdSettingsEnvelope toOffenderThresholdSettingsEnvelope(
        VersionedSettings<OffenderThresholdSettings> settings) {
        return OffenderThresholdSettingsEnvelope.newBuilder()
            .setData(toOffenderThresholdSettings(settings.data()))
            .setMeta(toSettingsMeta(settings))
            .build();
    }

    static ReplayRetentionSettingsEnvelope toReplayRetentionSettingsEnvelope(
        VersionedSettings<ReplayRetentionSettings> settings) {
        return ReplayRetentionSettingsEnvelope.newBuilder()
            .setData(toReplayRetentionSettings(settings.data()))
            .setMeta(toSettingsMeta(settings))
            .build();
    }

    static TicketFormSettingsEnvelope toTicketFormSettingsEnvelope(VersionedSettings<TicketFormSettings> settings) {
        return TicketFormSettingsEnvelope.newBuilder()
            .setData(toTicketFormSettings(settings.data()))
            .setMeta(toSettingsMeta(settings))
            .build();
    }

    static QuickResponseSettingsEnvelope toQuickResponseSettingsEnvelope(
        VersionedSettings<QuickResponseSettings> settings) {
        return QuickResponseSettingsEnvelope.newBuilder()
            .setData(toQuickResponseSettings(settings.data()))
            .setMeta(toSettingsMeta(settings))
            .build();
    }

    private static SettingsMeta toSettingsMeta(VersionedSettings<?> settings) {
        SettingsMeta.Builder builder = SettingsMeta.newBuilder()
            .setVersion(settings.version());
        if (settings.updatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(settings.updatedAt()));
        }
        return builder.build();
    }

    // --- Settings data types ---

    static gg.modl.proto.modl.v1.GeneralSettings toGeneralSettings(GeneralSettings settings) {
        if (settings == null) {
            return gg.modl.proto.modl.v1.GeneralSettings.getDefaultInstance();
        }
        return gg.modl.proto.modl.v1.GeneralSettings.newBuilder()
            .setServerDisplayName(stringValue(settings.getServerDisplayName()))
            .setDiscordWebhookUrl(stringValue(settings.getDiscordWebhookUrl()))
            .setHomepageIconUrl(stringValue(settings.getHomepageIconUrl()))
            .setPanelIconUrl(stringValue(settings.getPanelIconUrl()))
            .build();
    }

    static gg.modl.proto.modl.v1.TicketLabelSettings toTicketLabelSettings(TicketLabelSettings settings) {
        gg.modl.proto.modl.v1.TicketLabelSettings.Builder builder =
            gg.modl.proto.modl.v1.TicketLabelSettings.newBuilder();
        if (settings != null && settings.getLabels() != null) {
            settings.getLabels().stream()
                .map(PanelSettingsProtoMapper::toLabel)
                .forEach(builder::addLabels);
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.Label toLabel(Label label) {
        return gg.modl.proto.modl.v1.Label.newBuilder()
            .setId(stringValue(label.getId()))
            .setName(stringValue(label.getName()))
            .setColor(stringValue(label.getColor()))
            .setDescription(stringValue(label.getDescription()))
            .build();
    }

    static gg.modl.proto.modl.v1.OffenderThresholdSettings toOffenderThresholdSettings(
        OffenderThresholdSettings settings) {
        gg.modl.proto.modl.v1.OffenderThresholdSettings.Builder builder =
            gg.modl.proto.modl.v1.OffenderThresholdSettings.newBuilder();
        if (settings == null) {
            return builder.build();
        }
        if (settings.getSocial() != null) {
            builder.setSocial(toCategoryThresholds(settings.getSocial()));
        }
        if (settings.getGameplay() != null) {
            builder.setGameplay(toCategoryThresholds(settings.getGameplay()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.CategoryThresholds toCategoryThresholds(
        OffenderThresholdSettings.CategoryThresholds thresholds) {
        return gg.modl.proto.modl.v1.CategoryThresholds.newBuilder()
            .setMedium(thresholds.getMedium())
            .setHabitual(thresholds.getHabitual())
            .setPointExpiryMonths(thresholds.getPointExpiryMonths())
            .build();
    }

    static gg.modl.proto.modl.v1.ReplayRetentionSettings toReplayRetentionSettings(ReplayRetentionSettings settings) {
        if (settings == null) {
            return gg.modl.proto.modl.v1.ReplayRetentionSettings.getDefaultInstance();
        }
        return gg.modl.proto.modl.v1.ReplayRetentionSettings.newBuilder()
            .setEnabled(settings.isEnabled())
            .setDays(settings.getDays())
            .build();
    }

    static gg.modl.proto.modl.v1.TicketFormSettings toTicketFormSettings(TicketFormSettings settings) {
        gg.modl.proto.modl.v1.TicketFormSettings.Builder builder =
            gg.modl.proto.modl.v1.TicketFormSettings.newBuilder();
        if (settings == null) {
            return builder.build();
        }
        if (settings.getBug() != null) {
            builder.setBug(toTicketForm(settings.getBug()));
        }
        if (settings.getSupport() != null) {
            builder.setSupport(toTicketForm(settings.getSupport()));
        }
        if (settings.getApplication() != null) {
            builder.setApplication(toTicketForm(settings.getApplication()));
        }
        if (settings.getPlayer() != null) {
            builder.setPlayer(toTicketForm(settings.getPlayer()));
        }
        if (settings.getChat() != null) {
            builder.setChat(toTicketForm(settings.getChat()));
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.TicketForm toTicketForm(TicketFormSettings.TicketForm form) {
        gg.modl.proto.modl.v1.TicketForm.Builder builder = gg.modl.proto.modl.v1.TicketForm.newBuilder()
            .setRequireEmail(form.isRequireEmail())
            .setRequireEmailAuth(form.isRequireEmailAuth())
            .setAllowEmailNotifications(form.isAllowEmailNotifications());
        if (form.getFields() != null) {
            form.getFields().stream().map(PanelSettingsProtoMapper::toFormField).forEach(builder::addFields);
        }
        if (form.getSections() != null) {
            form.getSections().stream().map(PanelSettingsProtoMapper::toFormSection).forEach(builder::addSections);
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.FormField toFormField(TicketFormSettings.FormField field) {
        gg.modl.proto.modl.v1.FormField.Builder builder = gg.modl.proto.modl.v1.FormField.newBuilder()
            .setId(stringValue(field.getId()))
            .setType(stringValue(field.getType()))
            .setLabel(stringValue(field.getLabel()))
            .setDescription(stringValue(field.getDescription()))
            .setRequired(field.isRequired())
            .setOrder(field.getOrder())
            .setSectionId(stringValue(field.getSectionId()))
            .setGoToSection(stringValue(field.getGoToSection()));
        if (field.getOptions() != null) {
            builder.addAllOptions(field.getOptions());
        }
        if (field.getOptionSectionMapping() != null) {
            builder.putAllOptionSectionMapping(field.getOptionSectionMapping());
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.FormSection toFormSection(TicketFormSettings.FormSection section) {
        gg.modl.proto.modl.v1.FormSection.Builder builder = gg.modl.proto.modl.v1.FormSection.newBuilder()
            .setId(stringValue(section.getId()))
            .setTitle(stringValue(section.getTitle()))
            .setDescription(stringValue(section.getDescription()))
            .setOrder(section.getOrder())
            .setShowIfFieldId(stringValue(section.getShowIfFieldId()))
            .setShowIfValue(stringValue(section.getShowIfValue()))
            .setHideByDefault(section.isHideByDefault());
        if (section.getShowIfValues() != null) {
            builder.addAllShowIfValues(section.getShowIfValues());
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.QuickResponseSettings toQuickResponseSettings(QuickResponseSettings settings) {
        gg.modl.proto.modl.v1.QuickResponseSettings.Builder builder =
            gg.modl.proto.modl.v1.QuickResponseSettings.newBuilder();
        if (settings != null && settings.getCategories() != null) {
            settings.getCategories().stream()
                .map(PanelSettingsProtoMapper::toQuickResponseCategory)
                .forEach(builder::addCategories);
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.QuickResponseCategory toQuickResponseCategory(
        QuickResponseSettings.Category category) {
        gg.modl.proto.modl.v1.QuickResponseCategory.Builder builder =
            gg.modl.proto.modl.v1.QuickResponseCategory.newBuilder()
                .setId(stringValue(category.getId()))
                .setName(stringValue(category.getName()));
        if (category.getTicketTypes() != null) {
            builder.addAllTicketTypes(category.getTicketTypes());
        }
        if (category.getActions() != null) {
            category.getActions().stream()
                .map(PanelSettingsProtoMapper::toQuickResponseAction)
                .forEach(builder::addActions);
        }
        setOptionalInt(builder::setOrder, category.getOrder());
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.QuickResponseAction toQuickResponseAction(
        QuickResponseSettings.Action action) {
        gg.modl.proto.modl.v1.QuickResponseAction.Builder builder =
            gg.modl.proto.modl.v1.QuickResponseAction.newBuilder()
                .setId(stringValue(action.getId()))
                .setName(stringValue(action.getName()))
                .setMessage(stringValue(action.getMessage()))
                .setAppealAction(stringValue(action.getAppealAction()));
        setOptionalInt(builder::setOrder, action.getOrder());
        setOptionalBoolean(builder::setCloseTicket, action.getCloseTicket());
        setOptionalBoolean(builder::setShowPunishment, action.getShowPunishment());
        return builder.build();
    }

    static gg.modl.proto.modl.v1.AIModerationSettings toAIModerationSettings(AIModerationSettings settings) {
        gg.modl.proto.modl.v1.AIModerationSettings.Builder builder =
            gg.modl.proto.modl.v1.AIModerationSettings.newBuilder()
                .setEnableAiReview(settings.isEnableAIReview())
                .setEnableAutomatedActions(settings.isEnableAutomatedActions());
        if (settings.getAiPunishmentConfigs() != null) {
            settings.getAiPunishmentConfigs().forEach((key, config) ->
                builder.putAiPunishmentConfigs(key, toAIPunishmentConfig(config)));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.AIPunishmentConfig toAIPunishmentConfig(
        AIModerationSettings.AIPunishmentConfig config) {
        return gg.modl.proto.modl.v1.AIPunishmentConfig.newBuilder()
            .setId(stringValue(config.getId()))
            .setName(stringValue(config.getName()))
            .setAiDescription(stringValue(config.getAiDescription()))
            .setEnabled(config.isEnabled())
            .build();
    }

    static gg.modl.proto.modl.v1.WebhookSettings toWebhookSettings(WebhookSettings settings) {
        gg.modl.proto.modl.v1.WebhookSettings.Builder builder = gg.modl.proto.modl.v1.WebhookSettings.newBuilder()
            .setDiscordWebhookUrl(stringValue(settings.getDiscordWebhookUrl()))
            .setDiscordAdminRoleId(stringValue(settings.getDiscordAdminRoleId()))
            .setBotName(stringValue(settings.getBotName()))
            .setAvatarUrl(stringValue(settings.getAvatarUrl()))
            .setEnabled(settings.isEnabled());
        if (settings.getNotifications() != null) {
            builder.setNotifications(toWebhookNotifications(settings.getNotifications()));
        }
        if (settings.getEmbedTemplates() != null) {
            builder.setEmbedTemplates(toWebhookEmbedTemplates(settings.getEmbedTemplates()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.WebhookNotificationSettings toWebhookNotifications(
        WebhookSettings.NotificationSettings notifications) {
        return gg.modl.proto.modl.v1.WebhookNotificationSettings.newBuilder()
            .setNewTickets(notifications.isNewTickets())
            .setNewPunishments(notifications.isNewPunishments())
            .setAuditLogs(notifications.isAuditLogs())
            .build();
    }

    private static gg.modl.proto.modl.v1.WebhookEmbedTemplates toWebhookEmbedTemplates(
        WebhookSettings.EmbedTemplates templates) {
        gg.modl.proto.modl.v1.WebhookEmbedTemplates.Builder builder =
            gg.modl.proto.modl.v1.WebhookEmbedTemplates.newBuilder();
        if (templates.getNewTickets() != null) {
            builder.setNewTickets(toWebhookEmbedTemplate(templates.getNewTickets()));
        }
        if (templates.getNewPunishments() != null) {
            builder.setNewPunishments(toWebhookEmbedTemplate(templates.getNewPunishments()));
        }
        if (templates.getAuditLogs() != null) {
            builder.setAuditLogs(toWebhookEmbedTemplate(templates.getAuditLogs()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.WebhookEmbedTemplate toWebhookEmbedTemplate(
        WebhookSettings.EmbedTemplate template) {
        gg.modl.proto.modl.v1.WebhookEmbedTemplate.Builder builder =
            gg.modl.proto.modl.v1.WebhookEmbedTemplate.newBuilder()
                .setTitle(stringValue(template.getTitle()))
                .setDescription(stringValue(template.getDescription()))
                .setColor(stringValue(template.getColor()));
        if (template.getFields() != null) {
            template.getFields().stream()
                .map(PanelSettingsProtoMapper::toWebhookEmbedField)
                .forEach(builder::addFields);
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.WebhookEmbedField toWebhookEmbedField(WebhookSettings.EmbedField field) {
        return gg.modl.proto.modl.v1.WebhookEmbedField.newBuilder()
            .setName(stringValue(field.getName()))
            .setValue(stringValue(field.getValue()))
            .setInline(field.isInline())
            .build();
    }

    // --- Punishment types ---

    static PanelPunishmentTypesResponse toPunishmentTypesResponse(List<PunishmentType> types) {
        PanelPunishmentTypesResponse.Builder builder = PanelPunishmentTypesResponse.newBuilder();
        if (types != null) {
            types.stream().map(PanelSettingsProtoMapper::toPunishmentType).forEach(builder::addPunishmentTypes);
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PunishmentType toPunishmentType(PunishmentType type) {
        gg.modl.proto.modl.v1.PunishmentType.Builder builder = gg.modl.proto.modl.v1.PunishmentType.newBuilder()
            .setName(stringValue(type.getName()))
            .setCategory(stringValue(type.getCategory()))
            .setStaffDescription(stringValue(type.getStaffDescription()))
            .setPlayerDescription(stringValue(type.getPlayerDescription()));
        setOptionalInt(builder::setId, type.getId());
        setOptionalBoolean(builder::setCustomizable, type.getCustomizable());
        setOptionalInt(builder::setOrdinal, type.getOrdinal());
        if (type.getDurations() != null) {
            builder.setDurations(toPunishmentDurations(type.getDurations()));
        }
        setOptionalBoolean(builder::setSingleSeverityPunishment, type.getSingleSeverityPunishment());
        if (type.getSingleSeverityDurations() != null) {
            builder.setSingleSeverityDurations(toOffenseLevelDurations(type.getSingleSeverityDurations()));
        }
        setOptionalInt(builder::setSingleSeverityPoints, type.getSingleSeverityPoints());
        if (type.getPoints() != null) {
            builder.setPoints(toPunishmentPoints(type.getPoints()));
        }
        setOptionalInt(builder::setCustomPoints, type.getCustomPoints());
        setOptionalBoolean(builder::setCanBeAltBlocking, type.getCanBeAltBlocking());
        setOptionalBoolean(builder::setCanBeStatWiping, type.getCanBeStatWiping());
        setOptionalBoolean(builder::setAppealable, type.getAppealable());
        if (type.getAppealForm() != null) {
            builder.setAppealForm(toAppealForm(type.getAppealForm()));
        }
        setOptionalBoolean(builder::setPermanentUntilSkinChange, type.getPermanentUntilSkinChange());
        setOptionalBoolean(builder::setPermanentUntilUsernameChange, type.getPermanentUntilUsernameChange());
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.PunishmentDurations toPunishmentDurations(PunishmentDurations durations) {
        gg.modl.proto.modl.v1.PunishmentDurations.Builder builder =
            gg.modl.proto.modl.v1.PunishmentDurations.newBuilder();
        if (durations.low() != null) {
            builder.setLow(toOffenseLevelDurations(durations.low()));
        }
        if (durations.regular() != null) {
            builder.setRegular(toOffenseLevelDurations(durations.regular()));
        }
        if (durations.severe() != null) {
            builder.setSevere(toOffenseLevelDurations(durations.severe()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.OffenseLevelDurations toOffenseLevelDurations(
        OffenseLevelDurations durations) {
        gg.modl.proto.modl.v1.OffenseLevelDurations.Builder builder =
            gg.modl.proto.modl.v1.OffenseLevelDurations.newBuilder();
        if (durations.first() != null) {
            builder.setFirst(toDurationDetail(durations.first()));
        }
        if (durations.medium() != null) {
            builder.setMedium(toDurationDetail(durations.medium()));
        }
        if (durations.habitual() != null) {
            builder.setHabitual(toDurationDetail(durations.habitual()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.DurationDetail toDurationDetail(DurationDetail detail) {
        return gg.modl.proto.modl.v1.DurationDetail.newBuilder()
            .setValue(detail.value())
            .setUnit(stringValue(detail.unit()))
            .setType(stringValue(detail.type()))
            .build();
    }

    private static gg.modl.proto.modl.v1.PunishmentPoints toPunishmentPoints(PunishmentPoints points) {
        return gg.modl.proto.modl.v1.PunishmentPoints.newBuilder()
            .setLow(points.low())
            .setRegular(points.regular())
            .setSevere(points.severe())
            .build();
    }

    private static gg.modl.proto.modl.v1.AppealForm toAppealForm(AppealForm form) {
        gg.modl.proto.modl.v1.AppealForm.Builder builder = gg.modl.proto.modl.v1.AppealForm.newBuilder();
        if (form.getFields() != null) {
            form.getFields().stream().map(PanelSettingsProtoMapper::toAppealFormField).forEach(builder::addFields);
        }
        if (form.getSections() != null) {
            form.getSections().stream()
                .map(PanelSettingsProtoMapper::toAppealFormSection)
                .forEach(builder::addSections);
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.AppealFormField toAppealFormField(AppealFormField field) {
        gg.modl.proto.modl.v1.AppealFormField.Builder builder = gg.modl.proto.modl.v1.AppealFormField.newBuilder()
            .setId(stringValue(field.getId()))
            .setType(stringValue(field.getType()))
            .setLabel(stringValue(field.getLabel()))
            .setDescription(stringValue(field.getDescription()))
            .setSectionId(stringValue(field.getSectionId()));
        setOptionalBoolean(builder::setRequired, field.getRequired());
        setOptionalInt(builder::setOrder, field.getOrder());
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.AppealFormSection toAppealFormSection(AppealFormSection section) {
        gg.modl.proto.modl.v1.AppealFormSection.Builder builder =
            gg.modl.proto.modl.v1.AppealFormSection.newBuilder()
                .setId(stringValue(section.getId()))
                .setTitle(stringValue(section.getTitle()))
                .setDescription(stringValue(section.getDescription()));
        setOptionalInt(builder::setOrder, section.getOrder());
        return builder.build();
    }

    // --- Domain settings ---

    static gg.modl.proto.modl.v1.DomainSettings toDomainSettings(DomainSettings settings) {
        gg.modl.proto.modl.v1.DomainSettings.Builder builder = gg.modl.proto.modl.v1.DomainSettings.newBuilder()
            .setCustomDomain(stringValue(settings.getCustomDomain()))
            .setAccessingFromCustomDomain(settings.isAccessingFromCustomDomain())
            .setModlSubdomainUrl(stringValue(settings.getModlSubdomainUrl()))
            .setCanManageCustomDomain(settings.isCanManageCustomDomain());
        if (settings.getStatus() != null) {
            builder.setStatus(toDomainStatus(settings.getStatus()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.DomainStatus toDomainStatus(DomainSettings.DomainStatus status) {
        return gg.modl.proto.modl.v1.DomainStatus.newBuilder()
            .setDomain(stringValue(status.getDomain()))
            .setStatus(stringValue(status.getStatus()))
            .setCnameConfigured(status.isCnameConfigured())
            .setSslStatus(stringValue(status.getSslStatus()))
            .setLastChecked(stringValue(status.getLastChecked()))
            .setError(stringValue(status.getError()))
            .build();
    }

    static VerifyDomainResponse toVerifyDomainResponse(DomainSettings settings, String message) {
        VerifyDomainResponse.Builder builder = VerifyDomainResponse.newBuilder()
            .setMessage(stringValue(message));
        if (settings.getStatus() != null) {
            builder.setStatus(toDomainStatus(settings.getStatus()));
        }
        return builder.build();
    }

    static RemoveDomainResponse toRemoveDomainResponse(String message) {
        return RemoveDomainResponse.newBuilder().setMessage(stringValue(message)).build();
    }

    // --- API keys / webhook test / AI suggestion ---

    static ApiKeyGenerateResponse toApiKeyGenerateResponse(String message, String apiKey) {
        return ApiKeyGenerateResponse.newBuilder()
            .setMessage(stringValue(message))
            .setApiKey(stringValue(apiKey))
            .build();
    }

    static ApiKeyRevealResponse toApiKeyRevealResponse(String apiKey) {
        return ApiKeyRevealResponse.newBuilder().setApiKey(stringValue(apiKey)).build();
    }

    static ApiKeyDeleteResponse toApiKeyDeleteResponse(String message) {
        return ApiKeyDeleteResponse.newBuilder().setMessage(stringValue(message)).build();
    }

    static ApiKeyExistsResponse toApiKeyExistsResponse(boolean exists) {
        return ApiKeyExistsResponse.newBuilder().setExists(exists).build();
    }

    static WebhookTestResponse toWebhookTestResponse(String message) {
        return WebhookTestResponse.newBuilder().setMessage(stringValue(message)).build();
    }

    static AISuggestionActionResponse toAISuggestionActionResponse(boolean success, String error, String message) {
        return AISuggestionActionResponse.newBuilder()
            .setSuccess(success)
            .setError(stringValue(error))
            .setMessage(stringValue(message))
            .build();
    }

    // --- Public settings ---

    static gg.modl.proto.modl.v1.PublicSettingsResponse toPublicSettingsResponse(PublicSettingsResponse response) {
        gg.modl.proto.modl.v1.PublicSettingsResponse.Builder builder =
            gg.modl.proto.modl.v1.PublicSettingsResponse.newBuilder()
                .setServerExists(response.serverExists())
                .setServerDisplayName(stringValue(response.serverDisplayName()))
                .setPanelIconUrl(stringValue(response.panelIconUrl()))
                .setHomepageIconUrl(stringValue(response.homepageIconUrl()))
                .setMaintenanceMode(response.maintenanceMode())
                .setMaintenanceMessage(stringValue(response.maintenanceMessage()));
        if (response.ticketForms() != null) {
            builder.setTicketForms(toStruct(response.ticketForms()));
        }
        return builder.build();
    }

    // --- Inbound: request messages -> domain models ---

    static GeneralSettings fromPatchGeneralSettingsRequest(
        gg.modl.proto.modl.v1.PatchGeneralSettingsRequest request) {
        GeneralSettings.GeneralSettingsBuilder builder = GeneralSettings.builder();
        if (request.hasServerDisplayName()) {
            builder.serverDisplayName(request.getServerDisplayName());
        }
        if (request.hasDiscordWebhookUrl()) {
            builder.discordWebhookUrl(request.getDiscordWebhookUrl());
        }
        if (request.hasHomepageIconUrl()) {
            builder.homepageIconUrl(request.getHomepageIconUrl());
        }
        if (request.hasPanelIconUrl()) {
            builder.panelIconUrl(request.getPanelIconUrl());
        }
        return builder.build();
    }

    static List<Label> fromPatchTicketLabelSettingsRequest(
        gg.modl.proto.modl.v1.PatchTicketLabelSettingsRequest request) {
        return request.getLabelsList().stream().map(PanelSettingsProtoMapper::fromLabel).toList();
    }

    private static Label fromLabel(gg.modl.proto.modl.v1.Label label) {
        return Label.builder()
            .id(label.getId())
            .name(label.getName())
            .color(label.getColor())
            .description(label.getDescription())
            .build();
    }

    static OffenderThresholdSettings fromOffenderThresholdSettings(
        gg.modl.proto.modl.v1.OffenderThresholdSettings settings) {
        OffenderThresholdSettings.OffenderThresholdSettingsBuilder builder = OffenderThresholdSettings.builder();
        if (settings.hasSocial()) {
            builder.social(fromCategoryThresholds(settings.getSocial()));
        }
        if (settings.hasGameplay()) {
            builder.gameplay(fromCategoryThresholds(settings.getGameplay()));
        }
        return builder.build();
    }

    private static OffenderThresholdSettings.CategoryThresholds fromCategoryThresholds(
        gg.modl.proto.modl.v1.CategoryThresholds thresholds) {
        return new OffenderThresholdSettings.CategoryThresholds(
            thresholds.getMedium(),
            thresholds.getHabitual(),
            thresholds.getPointExpiryMonths()
        );
    }

    static TicketFormSettings fromTicketFormSettings(gg.modl.proto.modl.v1.TicketFormSettings settings) {
        TicketFormSettings.TicketFormSettingsBuilder builder = TicketFormSettings.builder();
        if (settings.hasBug()) {
            builder.bug(fromTicketForm(settings.getBug()));
        }
        if (settings.hasSupport()) {
            builder.support(fromTicketForm(settings.getSupport()));
        }
        if (settings.hasApplication()) {
            builder.application(fromTicketForm(settings.getApplication()));
        }
        if (settings.hasPlayer()) {
            builder.player(fromTicketForm(settings.getPlayer()));
        }
        if (settings.hasChat()) {
            builder.chat(fromTicketForm(settings.getChat()));
        }
        return builder.build();
    }

    private static TicketFormSettings.TicketForm fromTicketForm(gg.modl.proto.modl.v1.TicketForm form) {
        return TicketFormSettings.TicketForm.builder()
            .requireEmail(form.getRequireEmail())
            .requireEmailAuth(form.getRequireEmailAuth())
            .allowEmailNotifications(form.getAllowEmailNotifications())
            .fields(form.getFieldsList().stream().map(PanelSettingsProtoMapper::fromFormField).toList())
            .sections(form.getSectionsList().stream().map(PanelSettingsProtoMapper::fromFormSection).toList())
            .build();
    }

    private static TicketFormSettings.FormField fromFormField(gg.modl.proto.modl.v1.FormField field) {
        return TicketFormSettings.FormField.builder()
            .id(field.getId())
            .type(field.getType())
            .label(field.getLabel())
            .description(field.getDescription())
            .required(field.getRequired())
            .options(field.getOptionsList().stream().toList())
            .order(field.getOrder())
            .sectionId(field.getSectionId())
            .goToSection(field.getGoToSection())
            .optionSectionMapping(field.getOptionSectionMappingMap())
            .build();
    }

    private static TicketFormSettings.FormSection fromFormSection(gg.modl.proto.modl.v1.FormSection section) {
        return TicketFormSettings.FormSection.builder()
            .id(section.getId())
            .title(section.getTitle())
            .description(section.getDescription())
            .order(section.getOrder())
            .showIfFieldId(section.getShowIfFieldId())
            .showIfValue(section.getShowIfValue())
            .showIfValues(section.getShowIfValuesList().stream().toList())
            .hideByDefault(section.getHideByDefault())
            .build();
    }

    static List<QuickResponseSettings.Category> fromPatchQuickResponsesRequest(
        gg.modl.proto.modl.v1.PatchQuickResponsesRequest request) {
        return request.getCategoriesList().stream()
            .map(PanelSettingsProtoMapper::fromQuickResponseCategory)
            .toList();
    }

    private static QuickResponseSettings.Category fromQuickResponseCategory(
        gg.modl.proto.modl.v1.QuickResponseCategory category) {
        return QuickResponseSettings.Category.builder()
            .id(category.getId())
            .name(category.getName())
            .ticketTypes(category.getTicketTypesList().stream().toList())
            .actions(category.getActionsList().stream()
                .map(PanelSettingsProtoMapper::fromQuickResponseAction)
                .toList())
            .order(category.hasOrder() ? category.getOrder() : null)
            .build();
    }

    private static QuickResponseSettings.Action fromQuickResponseAction(
        gg.modl.proto.modl.v1.QuickResponseAction action) {
        return QuickResponseSettings.Action.builder()
            .id(action.getId())
            .name(action.getName())
            .message(action.getMessage())
            .order(action.hasOrder() ? action.getOrder() : null)
            .closeTicket(action.hasCloseTicket() ? action.getCloseTicket() : null)
            .showPunishment(action.hasShowPunishment() ? action.getShowPunishment() : null)
            .appealAction(action.getAppealAction())
            .build();
    }

    static AIModerationSettings fromUpdateAIModerationSettingsRequest(
        gg.modl.proto.modl.v1.UpdateAIModerationSettingsRequest request) {
        AIModerationSettings.AIModerationSettingsBuilder builder = AIModerationSettings.builder()
            .enableAIReview(request.hasEnableAiReview() && request.getEnableAiReview())
            .enableAutomatedActions(request.hasEnableAutomatedActions() && request.getEnableAutomatedActions());
        Map<String, AIModerationSettings.AIPunishmentConfig> configs =
            request.getAiPunishmentConfigsMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> fromAIPunishmentConfigRequest(entry.getValue()),
                    (left, right) -> right,
                    java.util.LinkedHashMap::new
                ));
        return builder.aiPunishmentConfigs(configs).build();
    }

    private static AIModerationSettings.AIPunishmentConfig fromAIPunishmentConfigRequest(
        gg.modl.proto.modl.v1.AIPunishmentConfigRequest request) {
        return AIModerationSettings.AIPunishmentConfig.builder()
            .id(request.getId())
            .name(request.getName())
            .aiDescription(request.getAiDescription())
            .enabled(request.hasEnabled() && request.getEnabled())
            .build();
    }

    static WebhookSettings fromUpdateWebhookSettingsRequest(
        gg.modl.proto.modl.v1.UpdateWebhookSettingsRequest request) {
        WebhookSettings.WebhookSettingsBuilder builder = WebhookSettings.builder()
            .discordWebhookUrl(request.getDiscordWebhookUrl())
            .discordAdminRoleId(request.getDiscordAdminRoleId())
            .botName(request.getBotName())
            .avatarUrl(request.getAvatarUrl())
            .enabled(request.hasEnabled() && request.getEnabled());
        if (request.hasNotifications()) {
            builder.notifications(fromWebhookNotifications(request.getNotifications()));
        }
        if (request.hasEmbedTemplates()) {
            builder.embedTemplates(fromWebhookEmbedTemplates(request.getEmbedTemplates()));
        }
        return builder.build();
    }

    private static WebhookSettings.NotificationSettings fromWebhookNotifications(
        gg.modl.proto.modl.v1.WebhookNotificationSettingsRequest request) {
        return WebhookSettings.NotificationSettings.builder()
            .newTickets(request.hasNewTickets() && request.getNewTickets())
            .newPunishments(request.hasNewPunishments() && request.getNewPunishments())
            .auditLogs(request.hasAuditLogs() && request.getAuditLogs())
            .build();
    }

    private static WebhookSettings.EmbedTemplates fromWebhookEmbedTemplates(
        gg.modl.proto.modl.v1.WebhookEmbedTemplatesRequest request) {
        WebhookSettings.EmbedTemplates.EmbedTemplatesBuilder builder = WebhookSettings.EmbedTemplates.builder();
        if (request.hasNewTickets()) {
            builder.newTickets(fromWebhookEmbedTemplate(request.getNewTickets()));
        }
        if (request.hasNewPunishments()) {
            builder.newPunishments(fromWebhookEmbedTemplate(request.getNewPunishments()));
        }
        if (request.hasAuditLogs()) {
            builder.auditLogs(fromWebhookEmbedTemplate(request.getAuditLogs()));
        }
        return builder.build();
    }

    private static WebhookSettings.EmbedTemplate fromWebhookEmbedTemplate(
        gg.modl.proto.modl.v1.WebhookEmbedTemplateRequest request) {
        return WebhookSettings.EmbedTemplate.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .color(request.getColor())
            .fields(request.getFieldsList().stream()
                .map(PanelSettingsProtoMapper::fromWebhookEmbedField)
                .toList())
            .build();
    }

    private static WebhookSettings.EmbedField fromWebhookEmbedField(
        gg.modl.proto.modl.v1.WebhookEmbedFieldRequest request) {
        return WebhookSettings.EmbedField.builder()
            .name(request.getName())
            .value(request.getValue())
            .inline(request.hasInline() && request.getInline())
            .build();
    }

    static PunishmentType fromPunishmentTypeRequest(gg.modl.proto.modl.v1.PunishmentTypeRequest request) {
        PunishmentType type = new PunishmentType();
        type.setName(request.getName());
        type.setCategory(request.getCategory());
        type.setStaffDescription(request.getStaffDescription());
        type.setPlayerDescription(request.getPlayerDescription());
        if (request.hasDurations()) {
            type.setDurations(fromPunishmentDurations(request.getDurations()));
        }
        if (request.hasSingleSeverityDurations()) {
            type.setSingleSeverityDurations(fromOffenseLevelDurations(request.getSingleSeverityDurations()));
        }
        if (request.hasSingleSeverityPoints()) {
            type.setSingleSeverityPoints(request.getSingleSeverityPoints());
        }
        if (request.hasPoints()) {
            type.setPoints(fromPunishmentPoints(request.getPoints()));
        }
        if (request.hasCustomPoints()) {
            type.setCustomPoints(request.getCustomPoints());
        }
        if (request.hasSingleSeverityPunishment()) {
            type.setSingleSeverityPunishment(request.getSingleSeverityPunishment());
        }
        if (request.hasCanBeAltBlocking()) {
            type.setCanBeAltBlocking(request.getCanBeAltBlocking());
        }
        if (request.hasCanBeStatWiping()) {
            type.setCanBeStatWiping(request.getCanBeStatWiping());
        }
        if (request.hasAppealable()) {
            type.setAppealable(request.getAppealable());
        }
        if (request.hasAppealForm()) {
            type.setAppealForm(fromAppealForm(request.getAppealForm()));
        }
        if (request.hasPermanentUntilSkinChange()) {
            type.setPermanentUntilSkinChange(request.getPermanentUntilSkinChange());
        }
        if (request.hasPermanentUntilUsernameChange()) {
            type.setPermanentUntilUsernameChange(request.getPermanentUntilUsernameChange());
        }
        return type;
    }

    private static PunishmentDurations fromPunishmentDurations(gg.modl.proto.modl.v1.PunishmentDurations durations) {
        return new PunishmentDurations(
            durations.hasLow() ? fromOffenseLevelDurations(durations.getLow()) : null,
            durations.hasRegular() ? fromOffenseLevelDurations(durations.getRegular()) : null,
            durations.hasSevere() ? fromOffenseLevelDurations(durations.getSevere()) : null
        );
    }

    private static OffenseLevelDurations fromOffenseLevelDurations(
        gg.modl.proto.modl.v1.OffenseLevelDurations durations) {
        return new OffenseLevelDurations(
            durations.hasFirst() ? fromDurationDetail(durations.getFirst()) : null,
            durations.hasMedium() ? fromDurationDetail(durations.getMedium()) : null,
            durations.hasHabitual() ? fromDurationDetail(durations.getHabitual()) : null
        );
    }

    private static DurationDetail fromDurationDetail(gg.modl.proto.modl.v1.DurationDetail detail) {
        return new DurationDetail(detail.getValue(), detail.getUnit(), detail.getType());
    }

    private static PunishmentPoints fromPunishmentPoints(gg.modl.proto.modl.v1.PunishmentPoints points) {
        return new PunishmentPoints(points.getLow(), points.getRegular(), points.getSevere());
    }

    private static AppealForm fromAppealForm(gg.modl.proto.modl.v1.AppealForm form) {
        return AppealForm.builder()
            .fields(form.getFieldsList().stream().map(PanelSettingsProtoMapper::fromAppealFormField).toList())
            .sections(form.getSectionsList().stream().map(PanelSettingsProtoMapper::fromAppealFormSection).toList())
            .build();
    }

    private static AppealFormField fromAppealFormField(gg.modl.proto.modl.v1.AppealFormField field) {
        return AppealFormField.builder()
            .id(field.getId())
            .type(field.getType())
            .label(field.getLabel())
            .description(field.getDescription())
            .required(field.hasRequired() ? field.getRequired() : null)
            .order(field.hasOrder() ? field.getOrder() : null)
            .sectionId(field.getSectionId())
            .build();
    }

    private static AppealFormSection fromAppealFormSection(gg.modl.proto.modl.v1.AppealFormSection section) {
        return AppealFormSection.builder()
            .id(section.getId())
            .title(section.getTitle())
            .description(section.getDescription())
            .order(section.hasOrder() ? section.getOrder() : null)
            .build();
    }
}
