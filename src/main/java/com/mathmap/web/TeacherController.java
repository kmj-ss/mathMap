package com.mathmap.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mathmap.auth.RateLimiter;
import com.mathmap.auth.SessionKeys;
import com.mathmap.auth.TeacherAuthService;
import com.mathmap.auth.TeacherInterceptor;
import com.mathmap.game.GameService;
import com.mathmap.game.GameService.QuestionInput;
import com.mathmap.game.ImageStore;
import com.mathmap.ws.Broadcaster;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 선생님 전용 API. /login, /me 를 제외한 모든 주소는 TeacherInterceptor 가 로그인 여부를 검사한다. */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final GameService game;
    private final ImageStore images;
    private final TeacherAuthService auth;
    private final Broadcaster broadcaster;
    /** 로그인 실패는 IP 당 5분에 10번까지 */
    private final RateLimiter loginLimiter = new RateLimiter(10, 5 * 60_000);

    public TeacherController(GameService game, ImageStore images, TeacherAuthService auth, Broadcaster broadcaster) {
        this.game = game;
        this.images = images;
        this.auth = auth;
        this.broadcaster = broadcaster;
    }

    public record LoginRequest(String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest req) {
        String ip = ClientIp.of(req);
        if (!loginLimiter.tryAcquire(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "로그인 시도가 너무 많아요. 몇 분 뒤 다시 시도해 주세요."));
        }
        if (!auth.matches(body.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "비밀번호가 맞지 않아요."));
        }
        HttpSession session = req.getSession(true);
        req.changeSessionId(); // 세션 고정 공격 방지
        session.setAttribute(SessionKeys.TEACHER, Boolean.TRUE);
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest req) {
        if (TeacherInterceptor.isTeacher(req)) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "선생님 로그인이 필요해요."));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.removeAttribute(SessionKeys.TEACHER);
        }
        broadcaster.disconnectTeacherSession(session == null ? null : session.getId());
        return ResponseEntity.ok(Map.of());
    }

    public record EntryCodeRequest(String entryCode) {}

    @PostMapping("/entry-code")
    public ResponseEntity<?> entryCode(@RequestBody EntryCodeRequest body) {
        game.setEntryCode(body.entryCode());
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        game.resetClass();
        broadcaster.disconnectAllStudents();
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/questions")
    public ResponseEntity<?> addQuestion(@RequestBody QuestionInput body) {
        long id = game.addQuestion(body).getId();
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable long id, @RequestBody QuestionInput body) {
        game.updateQuestion(id, body);
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of());
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable long id) {
        game.deleteQuestion(id);
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of());
    }

    public record NextRequest(Long questionId) {}

    @PostMapping("/next")
    public ResponseEntity<?> next(@RequestBody(required = false) NextRequest body) {
        game.next(body == null ? null : body.questionId());
        broadcaster.pushAll();
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/close")
    public ResponseEntity<?> close() {
        game.closeCurrent();
        broadcaster.pushAll();
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/students/{id}/kick")
    public ResponseEntity<?> kick(@PathVariable String id) {
        game.kick(id).ifPresent(s -> broadcaster.kickStudent(s.getId()));
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of());
    }

    public record UnkickRequest(String key) {}

    @PostMapping("/unkick")
    public ResponseEntity<?> unkick(@RequestBody UnkickRequest body) {
        game.unkick(body.key());
        broadcaster.pushTeachers();
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/images")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String id = images.save(file.getBytes());
        return ResponseEntity.ok(Map.of("imageId", id));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() throws IOException {
        byte[] xlsx = ExcelExporter.write(game.scoreSheet(), new ByteArrayOutputStream());
        String fileName = "mathMap_점수_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mathMap_scores.xlsx\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
