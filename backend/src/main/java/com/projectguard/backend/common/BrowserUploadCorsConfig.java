package com.projectguard.backend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 브라우저가 파일을 이 서버로 바로 올리는 두 요청만 프론트엔드 주소(FRONTEND_ORIGIN)에서 허용한다.
 * 나머지 API는 지금처럼 Next.js 서버를 거쳐서만 호출된다. 파일은 Next.js를 거치면 요청 크기 제한
 * (Server Action 1MB, Vercel 4.5MB)에 걸리기 때문이다 (AnalysisUploadTicketService 참고).
 * 쿠키를 쓰지 않으므로(allowCredentials 끔) 등기부 분석은 1회용 입장권 헤더로 인증한다.
 */
@Configuration
public class BrowserUploadCorsConfig implements WebMvcConfigurer {

    private final String frontendOrigin;

    public BrowserUploadCorsConfig(@Value("${FRONTEND_ORIGIN:http://localhost:3000}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/analyze")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("POST")
                .allowedHeaders("X-Upload-Ticket");
        registry.addMapping("/api/lawyer/auth/signup")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("POST");
    }
}
