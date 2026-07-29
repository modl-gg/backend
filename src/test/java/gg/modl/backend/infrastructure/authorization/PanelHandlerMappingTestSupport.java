package gg.modl.backend.infrastructure.authorization;

import gg.modl.backend.alert.controller.PanelSystemAlertController;
import gg.modl.backend.analytics.controller.AnalyticsController;
import gg.modl.backend.appeal.controller.PanelAppealController;
import gg.modl.backend.audit.controller.AuditController;
import gg.modl.backend.billing.controller.PanelBillingController;
import gg.modl.backend.dashboard.controller.DashboardController;
import gg.modl.backend.homepage.controller.PanelHomepageCardController;
import gg.modl.backend.knowledgebase.controller.PanelKnowledgebaseController;
import gg.modl.backend.log.controller.PanelLogController;
import gg.modl.backend.migration.controller.PanelMigrationController;
import gg.modl.backend.player.controller.PanelPlayerController;
import gg.modl.backend.replay.controller.PanelReplayController;
import gg.modl.backend.role.controller.PanelRoleController;
import gg.modl.backend.server.controller.PanelServerController;
import gg.modl.backend.settings.controller.PanelAiSuggestionController;
import gg.modl.backend.settings.controller.PanelApiKeyController;
import gg.modl.backend.settings.controller.PanelDomainSettingsController;
import gg.modl.backend.settings.controller.PanelPunishmentTypeController;
import gg.modl.backend.settings.controller.PanelSettingsController;
import gg.modl.backend.staff.controller.PanelStaffController;
import gg.modl.backend.storage.controller.PanelMediaController;
import gg.modl.backend.storage.controller.PanelStorageController;
import gg.modl.backend.ticket.controller.PanelTicketController;
import gg.modl.backend.ticket.controller.TicketSubscriptionController;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public final class PanelHandlerMappingTestSupport {

    private static final List<Class<?>> PANEL_CONTROLLERS = List.of(
        PanelStaffController.class,
        PanelRoleController.class,
        DashboardController.class,
        PanelSystemAlertController.class,
        AnalyticsController.class,
        AuditController.class,
        PanelLogController.class,
        PanelReplayController.class,
        PanelPlayerController.class,
        PanelTicketController.class,
        TicketSubscriptionController.class,
        PanelAppealController.class,
        PanelBillingController.class,
        PanelHomepageCardController.class,
        PanelKnowledgebaseController.class,
        PanelMediaController.class,
        PanelMigrationController.class,
        PanelStorageController.class,
        PanelServerController.class,
        PanelSettingsController.class,
        PanelAiSuggestionController.class,
        PanelPunishmentTypeController.class,
        PanelDomainSettingsController.class,
        PanelApiKeyController.class
    );

    private PanelHandlerMappingTestSupport() {
    }

    public static PanelAccessPolicyResolver buildResolver() {
        return new PanelAccessPolicyResolver(providerOf(buildHandlerMapping()));
    }

    public static RequestMappingHandlerMapping buildHandlerMapping() {
        Object[] controllers = PANEL_CONTROLLERS.stream()
            .map(PanelHandlerMappingTestSupport::instantiateWithMocks)
            .toArray();
        return handlerMappingOf(controllers);
    }

    public static RequestMappingHandlerMapping handlerMappingOf(Object... controllers) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        for (Object controller : controllers) {
            context.getBeanFactory().registerSingleton(controller.getClass().getName(), controller);
        }
        RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
        handlerMapping.setPatternParser(new PathPatternParser());
        handlerMapping.setApplicationContext(context);
        handlerMapping.afterPropertiesSet();
        return handlerMapping;
    }

    private static Object instantiateWithMocks(Class<?> controller) {
        Constructor<?> constructor = widestConstructor(controller);
        constructor.setAccessible(true);
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            arguments[index] = Mockito.mock(parameterTypes[index]);
        }
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate controller " + controller.getName(), exception);
        }
    }

    private static Constructor<?> widestConstructor(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredConstructors())
            .max(Comparator.comparingInt(Constructor::getParameterCount))
            .orElseThrow(() -> new IllegalStateException("No constructor for " + controller.getName()));
    }

    public static ObjectProvider<RequestMappingHandlerMapping> providerOf(RequestMappingHandlerMapping handlerMapping) {
        return new ObjectProvider<>() {
            @Override
            public RequestMappingHandlerMapping getObject() {
                return handlerMapping;
            }

            @Override
            public RequestMappingHandlerMapping getObject(Object... args) {
                return handlerMapping;
            }

            @Override
            public RequestMappingHandlerMapping getIfAvailable() {
                return handlerMapping;
            }

            @Override
            public RequestMappingHandlerMapping getIfUnique() {
                return handlerMapping;
            }
        };
    }
}
