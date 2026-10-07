package com.mathmap.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathmap.auth.RateLimiter;
import com.mathmap.auth.SessionKeys;
import com.mathmap.game.Room;
import com.mathmap.game.RoomService;
import com.mathmap.game.Student;
import com.mathmap.ws.Broadcaster;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 학생용 API. 학생은 공유받은 주소의 방(roomId)에만 들어갈 수 있다. */
@RestController
@RequestMapping("/api/rooms/{roomId}")
public class StudentController {

    private final RoomService rooms;
    private final Broadcaster broadcaster;
    /** 방 확인/입장 시도는 IP 당 1분에 30번까지 (방 주소 무작위 대입 방지) */
    private final RateLimiter limiter = new RateLimiter(30, 60_000);

    public StudentController(RoomService rooms, Broadcaster broadcaster) {
        this.rooms = rooms;
        this.broadcaster = broadcaster;
    }

    private ResponseEntity<?> tooMany() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "잠시 후 다시 시도해 주세요."));
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "없는 방이에요. 선생님께 받은 주소를 확인해 주세요."));
    }

    /** 방 이름과, 이 브라우저가 이미 입장했다면 학생 정보 */
    @GetMapping
    public ResponseEntity<?> info(@PathVariable String roomId, HttpServletRequest req) {
        if (!limiter.tryAcquire(ClientIp.of(req))) {
            return tooMany();
        }
        Room room = rooms.find(roomId).orElse(null);
        if (room == null) {
            return notFound();
        }
        String studentId = SessionKeys.studentId(req.getSession(false), roomId);
        return room.findStudent(studentId)
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(Map.of("roomName", room.getName(),
                        "me", Map.of("classNo", s.getClassNo(), "name", s.getName()))))
                .orElseGet(() -> ResponseEntity.ok(Map.of("roomName", room.getName())));
    }

    public record JoinRequest(String classNo, String name) {}

    @PostMapping("/join")
    public ResponseEntity<?> join(@PathVariable String roomId, @RequestBody JoinRequest body, HttpServletRequest req) {
        if (!limiter.tryAcquire(ClientIp.of(req))) {
            return tooMany();
        }
        Room room = rooms.find(roomId).orElse(null);
        if (room == null) {
            return notFound();
        }
        Student s = room.join(body.classNo(), body.name());
        HttpSession session = req.getSession(true);
        req.changeSessionId();
        SessionKeys.setStudentId(session, roomId, s.getId());
        broadcaster.pushTeachers(room);
        return ResponseEntity.ok(Map.of("roomName", room.getName(), "me", Map.of("classNo", s.getClassNo(), "name", s.getName())));
    }
}
