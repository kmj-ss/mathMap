package com.mathmap.ws;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
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
import com.mathmap.game.Room;

/** 방마다 연결된 선생님/학생 화면 목록을 들고 있다가 상태가 바뀌면 최신 화면 데이터를 보낸다. */
@Component
public class Broadcaster {

    private static final Logger log = LoggerFactory.getLogger(Broadcaster.class);

    private final ObjectMapper mapper;
    /** ws 세션 id → 연결 정보 */
    private final Map<String, Conn> conns = new ConcurrentHashMap<>();

    /** studentId 가 null 이면 선생님 연결 */
    record Conn(String roomId, String studentId, WebSocketSession session) {
        boolean isTeacher() { return studentId == null; }
    }

    public Broadcaster(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    static WebSocketSession wrap(WebSocketSession s) {
        return new ConcurrentWebSocketSessionDecorator(s, 5_000, 512 * 1024);
    }

    // ── 선생님 ──
    void addTeacher(Room room, WebSocketSession s) {
        conns.put(s.getId(), new Conn(room.getId(), null, s));
        send(s, room.teacherView());
    }

    public void pushTeachers(Room room) {
        Map<String, Object> view = room.teacherView();
        conns.values().stream()
                .filter(c -> c.isTeacher() && c.roomId().equals(room.getId()))
                .forEach(c -> send(c.session(), view));
    }

    public void disconnectTeacherSession(String httpSessionId) {
        conns.values().removeIf(c -> {
            if (c.isTeacher() && Objects.equals(
                    c.session().getAttributes().get(SessionAuthHandshakeInterceptor.ATTR_HTTP_SESSION), httpSessionId)) {
                close(c.session(), CloseStatus.NORMAL);
                return true;
            }
            return false;
        });
    }

    // ── 학생 ──
    void addStudent(Room room, String studentId, WebSocketSession s) {
        // 같은 학생이 다른 창/기기로 다시 접속하면 이전 연결은 끊는다
        conns.values().removeIf(c -> {
            if (studentId.equals(c.studentId()) && c.roomId().equals(room.getId())) {
                send(c.session(), Map.of("type", "replaced"));
                close(c.session(), CloseStatus.NORMAL);
                return true;
            }
            return false;
        });
        conns.put(s.getId(), new Conn(room.getId(), studentId, s));
        room.setConnected(studentId, true);
        pushStudent(room, studentId);
        pushTeachers(room);
    }

    /** 연결이 끊긴 경우. 끊긴 쪽이 학생이면 그 방 id 를 돌려준다. */
    Conn remove(WebSocketSession s) {
        return conns.remove(s.getId());
    }

    boolean hasStudentConnection(String roomId, String studentId) {
        return conns.values().stream().anyMatch(c -> studentId.equals(c.studentId()) && c.roomId().equals(roomId));
    }

    public void pushStudent(Room room, String studentId) {
        Map<String, Object> view = room.studentView(studentId);
        conns.values().stream()
                .filter(c -> studentId.equals(c.studentId()) && c.roomId().equals(room.getId()))
                .forEach(c -> send(c.session(), view));
    }

    public void pushAll(Room room) {
        conns.values().stream()
                .filter(c -> !c.isTeacher() && c.roomId().equals(room.getId()))
                .forEach(c -> send(c.session(), room.studentView(c.studentId())));
        pushTeachers(room);
    }

    public void kickStudent(Room room, String studentId) {
        conns.values().removeIf(c -> {
            if (studentId.equals(c.studentId()) && c.roomId().equals(room.getId())) {
                send(c.session(), Map.of("type", "kicked"));
                close(c.session(), CloseStatus.POLICY_VIOLATION);
                return true;
            }
            return false;
        });
    }

    /** 방의 학생 연결을 모두 끊는다 (새 수업 / 방 삭제) */
    public void disconnectStudents(String roomId, String type) {
        conns.values().removeIf(c -> {
            if (!c.isTeacher() && c.roomId().equals(roomId)) {
                send(c.session(), Map.of("type", type));
                close(c.session(), CloseStatus.NORMAL);
                return true;
            }
            return false;
        });
    }

    /** 방 삭제: 선생님/학생 연결 모두 끊기 */
    public void closeRoom(String roomId) {
        disconnectStudents(roomId, "closed");
        conns.values().removeIf(c -> {
            if (c.roomId().equals(roomId)) {
                send(c.session(), Map.of("type", "closed"));
                close(c.session(), CloseStatus.NORMAL);
                return true;
            }
            return false;
        });
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
