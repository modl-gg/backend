package gg.modl.backend.infrastructure.authorization;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.role.service.PermissionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class PanelAuthorizationBootstrapValidatorTest {

    @RestController
    @RequestMapping(RESTMappingV1.PREFIX_PANEL + "/bootstrap-probe")
    static class UnflaggedPermissionOnSuperAdminRuleController {
        @GetMapping("/unflagged")
        @RequiresPanelPermission(rule = PanelAccessRule.SUPER_ADMIN, supersedesPermissions = PermissionService.ADMIN_SETTINGS_VIEW)
        String unflagged() {
            return "";
        }
    }

    @RestController
    @RequestMapping(RESTMappingV1.PREFIX_PANEL + "/bootstrap-probe")
    static class FlaggedPermissionsFullyRuledController {
        @GetMapping("/billing")
        @RequiresPanelPermission(rule = PanelAccessRule.SUPER_ADMIN,
            supersedesPermissions = {PermissionService.ADMIN_SETTINGS_VIEW_BILLING, PermissionService.ADMIN_SETTINGS_MODIFY_BILLING})
        String billing() {
            return "";
        }

        @GetMapping("/rollback")
        @RequiresPanelPermission(rule = PanelAccessRule.SUPER_ADMIN, supersedesPermissions = PermissionService.ADMIN_AUDIT_ROLLBACK)
        String rollback() {
            return "";
        }
    }

    @RestController
    @RequestMapping(RESTMappingV1.PREFIX_PANEL + "/bootstrap-probe")
    static class FlaggedPermissionEnforcedWithoutSuperAdminRuleController {
        @GetMapping("/leaked")
        @RequiresPanelPermission(PermissionService.ADMIN_AUDIT_ROLLBACK)
        String leaked() {
            return "";
        }
    }

    @RestController
    @RequestMapping(RESTMappingV1.PREFIX_PANEL + "/bootstrap-probe")
    static class SuperAdminRuleWithEnforcedPermissionController {
        @GetMapping("/ignored-enforcement")
        @RequiresPanelPermission(value = PermissionService.ADMIN_SETTINGS_VIEW, rule = PanelAccessRule.SUPER_ADMIN)
        String ignoredEnforcement() {
            return "";
        }
    }

    @RestController
    @RequestMapping(RESTMappingV1.PREFIX_PANEL + "/bootstrap-probe")
    static class SupersedesWithoutSuperAdminRuleController {
        @GetMapping("/dangling-supersedes")
        @RequiresPanelPermission(value = PermissionService.ADMIN_SETTINGS_VIEW,
            supersedesPermissions = PermissionService.ADMIN_AUDIT_ROLLBACK)
        String danglingSupersedes() {
            return "";
        }
    }

    @RestController
    @RequestMapping(RESTMappingV1.PREFIX_PANEL + "/bootstrap-probe")
    @RequiresPanelPermission(rule = PanelAccessRule.SUPER_ADMIN)
    static class SuperAdminControllerWeakenedByMethodOverrideController {
        @GetMapping("/weakened")
        @RequiresPanelPermission(PermissionService.ADMIN_SETTINGS_VIEW)
        String weakened() {
            return "";
        }
    }

    @RestController
    @RequestMapping(RESTMappingV1.PREFIX_PANEL + "/bootstrap-probe")
    static class UnguardedController {
        @GetMapping("/unguarded")
        String unguarded() {
            return "";
        }
    }

    @Test
    void superAdminRuledHandlerNamingAnUnflaggedPermissionIsRejected() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> validate(new UnflaggedPermissionOnSuperAdminRuleController(), new FlaggedPermissionsFullyRuledController()));

        Assertions.assertTrue(failure.getMessage().contains("not flagged superAdminOnly"), failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains(PermissionService.ADMIN_SETTINGS_VIEW), failure.getMessage());
    }

    @Test
    void flaggedPermissionNamedByNoSuperAdminRuledHandlerIsRejected() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> validate(new UnflaggedPermissionOnSuperAdminRuleController()));

        Assertions.assertTrue(failure.getMessage().contains("superseded by no super-admin-ruled panel endpoint"), failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains(PermissionService.ADMIN_AUDIT_ROLLBACK), failure.getMessage());
    }

    @Test
    void flaggedPermissionEnforcedWithoutSuperAdminRuleIsRejected() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> validate(new FlaggedPermissionEnforcedWithoutSuperAdminRuleController(), new FlaggedPermissionsFullyRuledController()));

        Assertions.assertTrue(failure.getMessage().contains("without rule = SUPER_ADMIN"), failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains(PermissionService.ADMIN_AUDIT_ROLLBACK), failure.getMessage());
    }

    @Test
    void superAdminRuledHandlerDeclaringAnEnforcedPermissionIsRejected() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> validate(new SuperAdminRuleWithEnforcedPermissionController(), new FlaggedPermissionsFullyRuledController()));

        Assertions.assertTrue(failure.getMessage().contains("combining rule = SUPER_ADMIN with an enforced permission"),
            failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains(PermissionService.ADMIN_SETTINGS_VIEW), failure.getMessage());
    }

    @Test
    void supersedesPermissionsWithoutSuperAdminRuleIsRejected() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> validate(new SupersedesWithoutSuperAdminRuleController(), new FlaggedPermissionsFullyRuledController()));

        Assertions.assertTrue(failure.getMessage().contains("declaring supersedesPermissions without rule = SUPER_ADMIN"),
            failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains(PermissionService.ADMIN_AUDIT_ROLLBACK), failure.getMessage());
    }

    @Test
    void methodLevelOverrideWeakeningASuperAdminControllerIsRejected() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> validate(new SuperAdminControllerWeakenedByMethodOverrideController(),
                new FlaggedPermissionsFullyRuledController()));

        Assertions.assertTrue(
            failure.getMessage().contains("overriding a rule = SUPER_ADMIN controller with a weaker method-level policy"),
            failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains("/weakened"), failure.getMessage());
    }

    @Test
    void unguardedPanelHandlerIsRejected() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> validate(new UnguardedController(), new FlaggedPermissionsFullyRuledController()));

        Assertions.assertTrue(failure.getMessage().contains("without a @RequiresPanelPermission policy"), failure.getMessage());
    }

    @Test
    void productionHandlerMappingSatisfiesEveryInvariant() {
        Assertions.assertDoesNotThrow(() -> validatorFor(PanelHandlerMappingTestSupport.buildHandlerMapping()).afterSingletonsInstantiated());
    }

    private void validate(Object... controllers) {
        validatorFor(PanelHandlerMappingTestSupport.handlerMappingOf(controllers)).afterSingletonsInstantiated();
    }

    private PanelAuthorizationBootstrapValidator validatorFor(RequestMappingHandlerMapping handlerMapping) {
        ObjectProvider<RequestMappingHandlerMapping> provider = PanelHandlerMappingTestSupport.providerOf(handlerMapping);
        return new PanelAuthorizationBootstrapValidator(provider, new PanelAccessPolicyResolver(provider));
    }
}
