package gg.modl.backend.infrastructure.authorization;

import gg.modl.backend.infrastructure.rest.RouteGroups;
import gg.modl.backend.role.service.PermissionService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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

    private static final class ViolationGroup {
        private final String description;
        private final List<String> offenders = new ArrayList<>();

        private ViolationGroup(String description) {
            this.description = description;
        }

        private void record(String offender) {
            offenders.add(offender);
        }

        private boolean isViolated() {
            return !offenders.isEmpty();
        }

        private String render() {
            return description + ": " + offenders;
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        RequestMappingHandlerMapping handlerMapping = handlerMappingProvider.getObject();
        ViolationGroup unguarded = new ViolationGroup(
            "Panel endpoints without a @RequiresPanelPermission policy (fail-closed default would deny them)");
        ViolationGroup weakenedSuperAdminControllers = new ViolationGroup(
            "Panel endpoints overriding a rule = SUPER_ADMIN controller with a weaker method-level policy");
        ViolationGroup ignoredEnforcedPermissions = new ViolationGroup(
            "Panel endpoints combining rule = SUPER_ADMIN with an enforced permission that rule = SUPER_ADMIN ignores "
                + "(declare catalog linkage with supersedesPermissions instead)");
        ViolationGroup danglingSupersededPermissions = new ViolationGroup(
            "Panel endpoints declaring supersedesPermissions without rule = SUPER_ADMIN");
        ViolationGroup unflaggedSuperseded = new ViolationGroup(
            "Super-admin-ruled panel endpoints superseding permissions that are not flagged superAdminOnly in the catalog");
        ViolationGroup leakedSuperAdminPermissions = new ViolationGroup(
            "Panel endpoints enforcing a superAdminOnly permission without rule = SUPER_ADMIN");
        ViolationGroup unruledSuperAdminPermissions = new ViolationGroup(
            "Catalog permissions flagged superAdminOnly but superseded by no super-admin-ruled panel endpoint");
        Set<String> supersededPermissions = new LinkedHashSet<>();

        handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            if (!requiresPanelAuthorization(mappingInfo)) {
                return;
            }
            RequiresPanelPermission annotation = policyResolver.resolveAnnotation(handlerMethod).orElse(null);
            String route = describe(mappingInfo, handlerMethod);
            if (annotation == null) {
                unguarded.record(route);
                return;
            }
            if (annotation.rule() != PanelAccessRule.SUPER_ADMIN) {
                if (declaresSuperAdminControllerRule(handlerMethod)) {
                    weakenedSuperAdminControllers.record(route);
                }
                enforcedPermissions(annotation)
                    .filter(PermissionService::isSuperAdminOnly)
                    .forEach(permission -> leakedSuperAdminPermissions.record(route + " enforces '" + permission + "'"));
                supersedes(annotation)
                    .forEach(permission -> danglingSupersededPermissions.record(route + " supersedes '" + permission + "'"));
                return;
            }
            enforcedPermissions(annotation)
                .forEach(permission -> ignoredEnforcedPermissions.record(route + " enforces '" + permission + "'"));
            supersedes(annotation).forEach(permission -> {
                supersededPermissions.add(permission);
                if (!PermissionService.isSuperAdminOnly(permission)) {
                    unflaggedSuperseded.record(route + " supersedes '" + permission + "'");
                }
            });
        });

        PermissionService.superAdminOnlyPermissionIds().stream()
            .filter(permission -> !supersededPermissions.contains(permission))
            .sorted()
            .forEach(unruledSuperAdminPermissions::record);

        failIfViolated(unguarded, weakenedSuperAdminControllers, ignoredEnforcedPermissions, danglingSupersededPermissions,
            unflaggedSuperseded, leakedSuperAdminPermissions, unruledSuperAdminPermissions);
    }

    private boolean declaresSuperAdminControllerRule(HandlerMethod handlerMethod) {
        return policyResolver.resolveTypeAnnotation(handlerMethod)
            .filter(typeAnnotation -> typeAnnotation.rule() == PanelAccessRule.SUPER_ADMIN)
            .isPresent();
    }

    private static void failIfViolated(ViolationGroup... groups) {
        List<String> violations = Stream.of(groups)
            .filter(ViolationGroup::isViolated)
            .map(ViolationGroup::render)
            .toList();
        if (!violations.isEmpty()) {
            throw new IllegalStateException(String.join("; ", violations));
        }
    }

    private static Stream<String> enforcedPermissions(RequiresPanelPermission annotation) {
        return Stream.of(annotation.value(), annotation.view(), annotation.modify())
            .filter(permission -> !permission.isBlank());
    }

    private static Stream<String> supersedes(RequiresPanelPermission annotation) {
        return Stream.of(annotation.supersedesPermissions())
            .filter(permission -> !permission.isBlank());
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
