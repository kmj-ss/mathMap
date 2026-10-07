package com.mathmap.ws;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathmap.game.GameService;

/** 연결된 선생님/학생 화면 목록을 들고 있다가 상태가 바뀌면 최신 화면 데이터를 보낸다. */
@Component
public class Broadcaster {

    private static final Logger log = LoggerFactory.getLogger(Broadcaster.class);

    private final GameService game;
    private final ObjectMapper mapper;
    private final Set<WebSocketSession> teachers = ConcurrentHashMap.newKeySet();
    /** ws 세션 id → (학생 id, 세션) */
    private final Map<String, StudentConn> students = new ConcurrentHashMap<>();

    record StudentConn(String studentId, WebSocketSession session) {}

    public Broadcaster(GameService game, ObjectMapper mapper) {
        this.game = game;
        this.mapper = mapper;
    }

    static WebSocketSession wrap(WebSocketSession s) {
        return new ConcurrentWebSocketSessionDecorator(s, 5_000, 512 * 1024);
    }

    // ── 선생님 ──
    void addTeacher(WebSocketSession s) {
        teachers.add(s);
        send(s, game.teacherView());
    }

    void removeTeacher(WebSocketSession s) {
        teachers.removeIf(t -> t.getId().equals(s.getId()));
    }

    public void pushTeachers() {
        Map<String, Object> view = game.teacherView();
        teachers.forEach(t -> send(t, view));
    }

    public void disconnectTeacherSession(String httpSessionId) {
        teachers.removeIf(t -> {
            if (Objects.equals(t.getAttributes().get(SessionAuthHandshakeInterceptor.ATTR_HTTP_SESSION), httpSessionId)) {
                close(t, CloseStatus.NORMAL);
                return true;
            }
            return false;
        });
    }

    // ── 학생 ──
    void addStudent(String studentId, WebSocketSession s) {
        // 같은 학생이 다른 창/기기로 다시 접속하면 이전 연결은 끊는다
        students.values().removeIf(c -> {
            if (c.studentId().equals(studentId)) {
                send(c.session(), Map.of("type", "replaced"));
                close(c.session(), CloseStatus.NORMAL);
                return true;
            }
            return false;
        });
        students.put(s.getId(), new StudentConn(studentId, s));
        game.setConnected(studentId, true);
        pushStudent(studentId);
        pushTeachers();
    }

    void removeStudent(WebSocketSession s) {
        StudentConn c = students.remove(s.getId());
        if (c != null && students.values().stream().noneMatch(o -> o.studentId().equals(c.studentId()))) {
            game.setConnected(c.studentId(), false);
            pushTeachers();
        }
    }

    public void pushStudent(String studentId) {
        Map<String, Object> view = game.studentView(studentId);
        students.values().stream().filter(c -> c.studentId().equals(studentId)).forEach(c -> send(c.session(), view));
    }

    public void pushAll() {
        students.values().forEach(c -> send(c.session(), game.studentView(c.studentId())));
        pushTeachers();
    }

    public void kickStudent(String studentId) {
        students.values().removeIf(c -> {
            if (c.studentId().equals(studentId)) {
                send(c.session(), Map.of("type", "kicked"));
                close(c.session(), CloseStatus.POLICY_VIOLATION);
                return true;
            }
            return false;
        });
    }

    public void disconnectAllStudents() {
        students.values().forEach(c -> {
            send(c.session(), Map.of("type", "reset"));
            close(c.session(), CloseStatus.NORMAL);
        });
        students.clear();
    }

    private void send(WebSocketSession s, Object payload) {
        if (!s.isOpen()) {
            return;
        }
        try {
            s.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            log.error("JSON 변환 실패", e);
        } catch (IOException | IllegalStateException e) {
            log.debug("전송 실패: {}", e.getMessage());
        }
    }

    private static void close(WebSocketSession s, CloseStatus status) {
        try {
            s.close(status);
        } catch (IOException ignored) {
            // 이미 끊긴 연결
        }
    }
}
