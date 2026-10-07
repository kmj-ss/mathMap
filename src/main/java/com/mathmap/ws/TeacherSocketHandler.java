package com.mathmap.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 선생님 화면: 서버 → 선생님 방향으로 현황만 받는다. 조작은 로그인 검사를 거치는 /api/teacher 로만 한다. */
@Component
public class TeacherSocketHandler extends TextWebSocketHandler {

    private final Broadcaster broadcaster;

    public TeacherSocketHandler(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        broadcaster.addTeacher(Broadcaster.wrap(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 선생님 쪽에서 오는 메시지는 사용하지 않음 (연결 유지용 ping 만 허용)
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.removeTeacher(session);
    }
}
