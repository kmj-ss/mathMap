package com.mathmap.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathmap.auth.RateLimiter;
import com.mathmap.auth.SessionKeys;
import com.mathmap.game.GameService;
import com.mathmap.game.Student;
import com.mathmap.ws.Broadcaster;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final GameService game;
    private final Broadcaster broadcaster;
    /** 입장 시도는 IP 당 1분에 20번까지 (입장 코드 무작위 대입 방지) */
    private final RateLimiter joinLimiter = new RateLimiter(20, 60_000);

    public StudentController(GameService game, Broadcaster broadcaster) {
        this.game = game;
        this.broadcaster = broadcaster;
    }

    public record JoinRequest(String entryCode, String classNo, String name) {}

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest body, HttpServletRequest req) {
        if (!joinLimiter.tryAcquire(ClientIp.of(req))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "잠시 후 다시 시도해 주세요."));
        }
        Student s = game.join(body.entryCode(), body.classNo(), body.name());
        HttpSession old = req.getSession(false);
        boolean wasTeacher = old != null && Boolean.TRUE.equals(old.getAttribute(SessionKeys.TEACHER));
        HttpSession session = req.getSession(true);
        req.changeSessionId();
        session.setAttribute(SessionKeys.STUDENT_ID, s.getId());
        if (wasTeacher) {
            session.setAttribute(SessionKeys.TEACHER, Boolean.TRUE);
        }
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of("classNo", s.getClassNo(), "name", s.getName()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        String id = session == null ? null : (String) session.getAttribute(SessionKeys.STUDENT_ID);
        return game.findStudent(id)
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(Map.of("classNo", s.getClassNo(), "name", s.getName())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "입장 정보가 없어요.")));
    }

    @PostMapping("/leave")
    public ResponseEntity<?> leave(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.removeAttribute(SessionKeys.STUDENT_ID);
        }
        return ResponseEntity.ok(Map.of());
    }
}
