package com.rastudio.commerce.user;
import java.util.Map;
/** Human-readable labels for UserRole, mirroring frontend/js/core/shell.js's ROLE_LABELS — used in the invitation email. */
public final class RoleLabels {
    public static final Map<UserRole,String> LABELS = Map.of(
        UserRole.BUSINESS_OWNER_ADMIN, "Business Owner / Admin",
        UserRole.OPERATIONS_MANAGER, "Operations Manager",
        UserRole.MARKETING_MANAGER, "Marketing Manager",
        UserRole.SUPPORT_CRM_AGENT, "Support / CRM Agent",
        UserRole.WAREHOUSE_STAFF, "Warehouse Staff",
        UserRole.ANALYST_VIEWER, "Analyst / Viewer"
    );
    private RoleLabels() {}
}