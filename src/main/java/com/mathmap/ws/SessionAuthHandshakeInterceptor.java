package com.mathmap.ws;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.mathmap.auth.SessionKeys;

import jakarta.servlet.http.HttpSession;

/**
 * 실시간 연결을 열 때 로그인 세션을 확인한다.
 * 선생님 연결은 선생님 세션만, 학생 연결은 입장한 학생 세션만 허용한다.
 */
public class SessionAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_STUDENT_ID = "studentId";
    public static final String ATTR_HTTP_SESSION = "httpSessionId";

    private final boolean teacher;

    public SessionAuthHandshakeInterceptor(boolean teacher) {
        this.teacher = teacher;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        if (session == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (teacher) {
            if (!Boolean.TRUE.equals(session.getAttribute(SessionKeys.TEACHER))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        } else {
            Object id = session.getAttribute(SessionKeys.STUDENT_ID);
            if (!(id instanceof String studentId)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(ATTR_STUDENT_ID, studentId);
        }
        attributes.put(ATTR_HTTP_SESSION, session.getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
