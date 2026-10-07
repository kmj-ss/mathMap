package com.mathmap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.mathmap.ws.SessionAuthHandshakeInterceptor;
import com.mathmap.ws.StudentSocketHandler;
import com.mathmap.ws.TeacherSocketHandler;

/** 실시간 연결 주소. 다른 사이트에서의 연결은 기본값(같은 출처만 허용)으로 막힌다. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TeacherSocketHandler teacherHandler;
    private final StudentSocketHandler studentHandler;

    public WebSocketConfig(TeacherSocketHandler teacherHandler, StudentSocketHandler studentHandler) {
        this.teacherHandler = teacherHandler;
        this.studentHandler = studentHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(teacherHandler, "/ws/teacher")
                .addInterceptors(new SessionAuthHandshakeInterceptor(true));
        registry.addHandler(studentHandler, "/ws/student")
                .addInterceptors(new SessionAuthHandshakeInterceptor(false));
    }
}
