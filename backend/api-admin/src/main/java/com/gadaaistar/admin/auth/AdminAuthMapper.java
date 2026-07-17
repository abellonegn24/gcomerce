package com.gadaaistar.admin.auth;

import com.gadaaistar.persistence.admin.AdminUser;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdminAuthMapper {

    public AdminAuthDtos.AdminUserDto toDto(AdminUser a) {
        return new AdminAuthDtos.AdminUserDto(
                a.id,
                a.email,
                a.fullName,
                a.role == null ? null : a.role.name,
                a.status,
                a.isMfaEnrolled(),
                a.lastLoginAt
        );
    }
}
