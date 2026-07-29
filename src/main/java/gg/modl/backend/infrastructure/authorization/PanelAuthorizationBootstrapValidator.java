package gg.modl.backend.infrastructure.authorization;

import gg.modl.backend.infrastructure.rest.RouteGroups;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Component
@RequiredArgsConstructor
public class PanelAuthorizationBootstrapValidator implements SmartInitializingSingleton {
    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;
    private final PanelAccessPolicyResolver policyResolver;

    @Override
    public void afterSingletonsInstantiated() {
        List<String> unguarded = new ArrayList<>();
        handlerMappingProvider.getObject().getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            if (requiresPanelAuthorization(mappingInfo) && policyResolver.resolve(handlerMethod).isEmpty()) {
                unguarded.add(describe(mappingInfo, handlerMethod));
            }
        });
        if (!unguarded.isEmpty()) {
            throw new IllegalStateException(
                "Panel endpoints without a @RequiresPanelPermission policy (fail-closed default would deny them): " + unguarded);
        }
    }

    private boolean requiresPanelAuthorization(RequestMappingInfo mappingInfo) {
        return patternsOf(mappingInfo).stream().anyMatch(this::isGuardedPanelPattern);
    }

    private boolean isGuardedPanelPattern(String pattern) {
        return RouteGroups.isPanelPrefix(pattern) && !RouteGroups.isPanelAuthArea(pattern);
    }

    private Set<String> patternsOf(RequestMappingInfo mappingInfo) {
        PathPatternsRequestCondition pathPatterns = mappingInfo.getPathPatternsCondition();
        return pathPatterns != null ? pathPatterns.getPatternValues() : Set.of();
    }

    private String describe(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        return patternsOf(mappingInfo) + " -> " + handlerMethod.getShortLogMessage();
    }
}
