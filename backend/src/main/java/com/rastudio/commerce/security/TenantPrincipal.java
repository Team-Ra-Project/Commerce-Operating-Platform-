package com.rastudio.commerce.security; import com.rastudio.commerce.user.UserRole; public record TenantPrincipal(Long userId,Long organizationId,UserRole role){}
