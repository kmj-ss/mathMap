package com.mathmap.ws;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathmap.auth.RateLimiter;
import com.mathmap.game.GameException;
import com.mathmap.game.GameService;

/**
 * 학생 화면과의 실시간 연결.
 * 학생은 {"type":"answer","questionId":1,"answer":"..."} 만 보낼 수 있고, 학생 신원은 서버 세션으로만 판단한다.
 */
@Component
public class StudentSocketHandler extends TextWebSocketHandler {

    private final GameService game;
    private final Broadcaster broadcaster;
    private final ObjectMapper mapper;
    /** 학생 한 명당 10초에 20개 메시지까지 */
    private final RateLimiter limiter = new RateLimiter(20, 10_000);

    public StudentSocketHandler(GameService game, Broadcaster broadcaster, ObjectMapper mapper) {
        this.game = game;
        this.broadcaster = broadcaster;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String studentId = studentId(session);
        if (game.findStudent(studentId).isEmpty()) {
            session.sendMessage(new TextMessage("{\"type\":\"state\",\"status\":\"GONE\"}"));
            session.close(CloseStatus.NORMAL);
            return;
        }
        broadcaster.addStudent(studentId, Broadcaster.wrap(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String studentId = studentId(session);
        if (message.getPayloadLength() > 2_000 || !limiter.tryAcquire(studentId)) {
            return;
        }
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (IOException e) {
            return;
        }
        if (!"answer".equals(node.path("type").asText())) {
            return;
        }
        try {
            game.submit(studentId, node.path("questionId").asLong(-1), node.path("answer").asText(null));
            broadcaster.pushStudent(studentId);
            broadcaster.pushTeachers();
        } catch (GameException e) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "error", "message", e.getMessage()))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.removeStudent(session);
    }

    private static String studentId(WebSocketSession session) {
        return (String) session.getAttributes().get(SessionAuthHandshakeInterceptor.ATTR_STUDENT_ID);
    }
}
