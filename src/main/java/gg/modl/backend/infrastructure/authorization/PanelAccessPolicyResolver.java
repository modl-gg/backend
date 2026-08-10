package gg.modl.backend.infrastructure.authorization;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class PanelAccessPolicyResolver {
    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;

    public List<PanelAccessPolicy> resolvePolicies(HttpServletRequest request) {
        RequestMappingHandlerMapping handlerMapping = handlerMappingProvider.getObject();
        boolean parsedHere = ensureParsedRequestPath(handlerMapping, request);
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
                return resolve(handlerMethod).map(List::of).orElseGet(List::of);
            }
            return List.of();
        } catch (HttpRequestMethodNotSupportedException methodNotSupported) {
            return siblingPolicies(handlerMapping, request);
        } catch (Exception exception) {
            log.debug("Panel handler resolution failed for {} {}", request.getMethod(), request.getRequestURI(), exception);
            return List.of();
        } finally {
            if (parsedHere) {
                ServletRequestPathUtils.clearParsedRequestPath(request);
            }
        }
    }

    public Optional<PanelAccessPolicy> resolve(HandlerMethod handlerMethod) {
        return Optional.ofNullable(findAnnotation(handlerMethod)).map(PanelAccessPolicyResolver::toPolicy);
    }

    private List<PanelAccessPolicy> siblingPolicies(RequestMappingHandlerMapping handlerMapping, HttpServletRequest request) {
        List<PanelAccessPolicy> policies = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            if (matchesPath(mappingInfo, request)) {
                resolve(handlerMethod).ifPresent(policies::add);
            }
        });
        return policies;
    }

    private static boolean matchesPath(RequestMappingInfo mappingInfo, HttpServletRequest request) {
        PathPatternsRequestCondition pathPatterns = mappingInfo.getPathPatternsCondition();
        return pathPatterns != null && pathPatterns.getMatchingCondition(request) != null;
    }

    private boolean ensureParsedRequestPath(RequestMappingHandlerMapping handlerMapping, HttpServletRequest request) {
        if (handlerMapping.getPatternParser() == null || ServletRequestPathUtils.hasParsedRequestPath(request)) {
            return false;
        }
        ServletRequestPathUtils.parseAndCache(request);
        return true;
    }

    private static RequiresPanelPermission findAnnotation(HandlerMethod handlerMethod) {
        RequiresPanelPermission methodAnnotation = handlerMethod.getMethodAnnotation(RequiresPanelPermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresPanelPermission.class);
    }

    private static PanelAccessPolicy toPolicy(RequiresPanelPermission annotation) {
        return switch (annotation.rule()) {
            case PERMIT_ALL -> PermitAllPolicy.INSTANCE;
            case PLAYER_ACCESS -> PlayerAccessPolicy.INSTANCE;
            case PUNISHMENT_TYPE_ACCESS -> PunishmentTypeAccessPolicy.INSTANCE;
            case APPEAL_REPLY -> AppealReplyPolicy.INSTANCE;
            case REQUIRE_PERMISSION -> new ReadWritePermissionPolicy(viewPermission(annotation), modifyPermission(annotation));
        };
    }

    private static String viewPermission(RequiresPanelPermission annotation) {
        return annotation.view().isEmpty() ? annotation.value() : annotation.view();
    }

    private static String modifyPermission(RequiresPanelPermission annotation) {
        return annotation.modify().isEmpty() ? annotation.value() : annotation.modify();
    }
}
