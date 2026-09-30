package com.projectguard.backend.admin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AdminAuthService> adminAuthService;
    private final String adminSecret;

    public AdminWebConfig(ObjectProvider<AdminAuthService> adminAuthService, @Value("${ADMIN_SECRET:}") String adminSecret) {
        this.adminAuthService = adminAuthService;
        this.adminSecret = adminSecret;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(adminAuthService, adminSecret))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/**");
    }
}
