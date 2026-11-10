// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.api.PermissionDecision.*;

/** A principal created only from a mounted module or trusted host access. */
final class ModuleAccess implements UIPermissionManager {
    private final ScopeLifecycle scopes;
    private final ScopeLifecycle.Scope issuer;
    private final ModuleDiscovery.Module module;
    private final Consumer<String> audit;
    private final Runnable validateGuard;
    private volatile Map<UIPermission, ScopeLifecycle.Scope> grants;

    ModuleAccess(ScopeLifecycle scopes, ScopeLifecycle.Scope issuer, ModuleDiscovery.Module module,
                 Consumer<String> audit, Runnable validateGuard) {
        this.scopes = scopes;
        this.issuer = issuer;
        this.module = module;
        this.audit = audit;
        this.validateGuard = validateGuard;
        var initial = new EnumMap<UIPermission, ScopeLifecycle.Scope>(UIPermission.class);
        if (module != null) {
            module.permissions().forEach(permission -> initial.put(permission, scopes.root()));
        }
        grants = Map.copyOf(initial);
    }

    ScopeLifecycle.Scope defaultScope() {
        return issuer == null ? scopes.root() : issuer;
    }

    ScopeLifecycle.Scope requireIssuer() {
        if (issuer == null) {
            throw new SecurityException("UI mutation requires a module context");
        }
        requireOpen(issuer);
        return issuer;
    }

    void requireSchemaOwner(UiId id) {
        if (!requireIssuer().owner().namespace().equals(id.namespace())) {
            throw new SecurityException(issuer.owner() + " cannot define UI schema " + id);
        }
    }

    void assertSideEffectAllowed() {
        validateGuard.run();
    }

    void requireClaim(ModuleDescriptor.ResourceClaim claim) {
        if (requireIssuer() != scopes.root() && !module.descriptor().resourceClaims().contains(claim)) {
            throw new SecurityException(issuer.owner() + " did not declare UI resource " + claim);
        }
    }

    ScopeLifecycle.Scope read(ScopeHandle handle) {
        var target = scopes.checkedScope(handle);
        if (target == scopes.root()) {
            requireOpen(defaultScope());
            requireOpen(target);
            return target;
        }
        return write(target);
    }

    ScopeLifecycle.Scope write(ScopeHandle handle) {
        var owner = requireIssuer();
        var target = scopes.checkedScope(handle);
        requireOpen(target);
        if (owner != scopes.root() && !owner.owner().equals(target.owner())) {
            require(UIPermission.MODIFY_FOREIGN_SCOPE, target);
        }
        return target;
    }

    boolean allowsScope(ScopeLifecycle.Scope target) {
        return issuer != null && issuer.isOpen() && target.isOpen()
                && (issuer == scopes.root() || issuer.owner().equals(target.owner())
                || granted(UIPermission.MODIFY_FOREIGN_SCOPE, target));
    }

    boolean canReadDiagnostics() {
        return decision(UIPermission.ACCESS_INTERNAL_DIAGNOSTICS, scopes.root()).allowed();
    }

    @Override
    public PermissionDecision check(PermissionRequest request) {
        try {
            return decision(request.permission(), scopes.checkedScope(request.target()));
        } catch (SecurityException invalidScope) {
            return INVALID_SCOPE;
        }
    }

    @Override
    public void require(PermissionRequest request) {
        var result = check(request);
        if (!result.allowed()) {
            deny(request.permission(), result == INVALID_SCOPE ? null : scopes.checkedScope(request.target()), result);
        }
    }

    PermissionDecision decision(UIPermission permission, ScopeLifecycle.Scope target) {
        if (issuer == null) {
            return NO_MODULE_CONTEXT;
        }
        if (!issuer.isOpen() || !target.isOpen()) {
            return CLOSED_SCOPE;
        }
        if (issuer == scopes.root()) {
            return GRANTED;
        }
        var current = grants;
        var boundary = current.get(permission);
        if (boundary == null) {
            return MISSING_GRANT;
        }
        if (!inside(target, boundary)) {
            return OUTSIDE_GRANT;
        }
        boolean global = target == scopes.root() && switch (permission) {
            case MODIFY_GLOBAL_CONFIG, MODIFY_GLOBAL_THEME, ACCESS_INTERNAL_DIAGNOSTICS -> true;
            default -> false;
        };
        if (global || permission == UIPermission.MODIFY_FOREIGN_SCOPE || issuer.owner().equals(target.owner())
                || inside(target, current.get(UIPermission.MODIFY_FOREIGN_SCOPE))) {
            return GRANTED;
        }
        return OUTSIDE_GRANT;
    }

    void require(UIPermission permission, ScopeLifecycle.Scope target) {
        var decision = decision(permission, target);
        if (!decision.allowed()) {
            deny(permission, target, decision);
        }
    }

    void restrict(UIPermission permission, ScopeLifecycle.Scope boundary) {
        requireIssuer().assertOwner();
        if (!granted(permission, boundary) || !boundary.isOpen()) {
            throw new IllegalArgumentException("Permission restriction must narrow a live grant: " + permission);
        }
        changeGrant(permission, boundary);
    }

    void revoke(UIPermission permission) {
        requireIssuer().assertOwner();
        if (grants.containsKey(permission)) {
            changeGrant(permission, null);
        }
    }

    private void changeGrant(UIPermission permission, ScopeLifecycle.Scope boundary) {
        var next = new EnumMap<UIPermission, ScopeLifecycle.Scope>(UIPermission.class);
        next.putAll(grants);
        if (boundary == null) {
            next.remove(permission);
        } else {
            next.put(permission, boundary);
        }
        grants = Map.copyOf(next);
        audit.accept("UI grant " + permission + " for " + module.origin()
                + (boundary == null ? " revoked" : " restricted to " + boundary.id() + "#" + boundary.generation()));
    }

    private boolean granted(UIPermission permission, ScopeLifecycle.Scope target) {
        return inside(target, grants.get(permission));
    }

    private static boolean inside(ScopeLifecycle.Scope target, ScopeLifecycle.Scope boundary) {
        return boundary != null && boundary.isOpen() && target.isWithin(boundary);
    }

    private void deny(UIPermission permission, ScopeLifecycle.Scope target, PermissionDecision result) {
        String source = module == null ? "anonymous" : module.origin().toString();
        String where = target == null ? "unverified scope" : target.id() + "#" + target.generation();
        String message = "UI permission " + permission + " denied for " + source + " at " + where + " (" + result + ")";
        audit.accept(message);
        throw new SecurityException(message);
    }

    private static void requireOpen(ScopeLifecycle.Scope scope) {
        if (!scope.isOpen()) {
            throw new IllegalStateException("UI scope is closed: " + scope.id());
        }
    }
}
