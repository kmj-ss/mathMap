package com.mathmap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.mathmap.auth.TeacherInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TeacherInterceptor teacherInterceptor;

    public WebConfig(TeacherInterceptor teacherInterceptor) {
        this.teacherInterceptor = teacherInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(teacherInterceptor)
                .addPathPatterns("/api/teacher/**")
                .excludePathPatterns("/api/teacher/login", "/api/teacher/me");
    }
}
