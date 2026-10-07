package com.mathmap.ws;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.mathmap.auth.SessionKeys;
import com.mathmap.game.RoomService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 실시간 연결을 열 때 방과 로그인 세션을 확인한다.
 * 선생님 연결은 선생님 세션만, 학생 연결은 그 방에 입장한 학생 세션만 허용한다.
 */
public class SessionAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ROOM_ID = "roomId";
    public static final String ATTR_STUDENT_ID = "studentId";
    public static final String ATTR_HTTP_SESSION = "httpSessionId";

    private final boolean teacher;
    private final RoomService rooms;

    public SessionAuthHandshakeInterceptor(boolean teacher, RoomService rooms) {
        this.teacher = teacher;
        this.rooms = rooms;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest req = servletRequest.getServletRequest();
        String roomId = req.getParameter("room");
        HttpSession session = req.getSession(false);
        if (rooms.find(roomId).isEmpty()) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
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
            String studentId = SessionKeys.studentId(session, roomId);
            if (studentId == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(ATTR_STUDENT_ID, studentId);
        }
        attributes.put(ATTR_ROOM_ID, roomId);
        attributes.put(ATTR_HTTP_SESSION, session.getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
