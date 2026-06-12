package gg.modl.backend.realtime.publish;

import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.Topic;
import java.util.EnumMap;
import java.util.Map;

final class PanelResourceTopics {
    private static final Map<PanelResource, Topic> TOPICS = topics();

    private PanelResourceTopics() {
    }

    static Topic topicFor(PanelResource resource) {
        Topic topic = TOPICS.get(resource);
        if (topic == null) {
            throw new IllegalArgumentException("No realtime topic mapped for panel resource " + resource);
        }
        return topic;
    }

    private static Map<PanelResource, Topic> topics() {
        Map<PanelResource, Topic> topics = new EnumMap<>(PanelResource.class);
        topics.put(PanelResource.PANEL_RESOURCE_PLAYERS, Topic.TOPIC_PANEL_PLAYERS);
        topics.put(PanelResource.PANEL_RESOURCE_PUNISHMENTS, Topic.TOPIC_PANEL_PUNISHMENTS);
        topics.put(PanelResource.PANEL_RESOURCE_STAFF, Topic.TOPIC_PANEL_STAFF);
        topics.put(PanelResource.PANEL_RESOURCE_ROLES, Topic.TOPIC_PANEL_ROLES);
        topics.put(PanelResource.PANEL_RESOURCE_SETTINGS, Topic.TOPIC_PANEL_SETTINGS);
        topics.put(PanelResource.PANEL_RESOURCE_PUNISHMENT_TYPES, Topic.TOPIC_PANEL_PUNISHMENT_TYPES);
        topics.put(PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE, Topic.TOPIC_PANEL_KNOWLEDGEBASE);
        topics.put(PanelResource.PANEL_RESOURCE_AUDIT, Topic.TOPIC_PANEL_AUDIT);
        topics.put(PanelResource.PANEL_RESOURCE_HOMEPAGE, Topic.TOPIC_PANEL_HOMEPAGE);
        topics.put(PanelResource.PANEL_RESOURCE_APPEALS, Topic.TOPIC_PANEL_APPEALS);
        topics.put(PanelResource.PANEL_RESOURCE_TICKETS, Topic.TOPIC_PANEL_TICKETS);
        topics.put(PanelResource.PANEL_RESOURCE_DASHBOARD, Topic.TOPIC_PANEL_DASHBOARD);
        topics.put(PanelResource.PANEL_RESOURCE_NOTIFICATIONS, Topic.TOPIC_PANEL_NOTIFICATIONS);
        return Map.copyOf(topics);
    }
}
