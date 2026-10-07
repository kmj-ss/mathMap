package com.mathmap.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import com.mathmap.game.ImageStore;
import com.mathmap.game.Room;
import com.mathmap.game.Room.QuestionInput;
import com.mathmap.game.RoomService;
import com.mathmap.ws.Broadcaster;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 선생님 전용 API. /login, /me 를 제외한 모든 주소는 TeacherInterceptor 가 로그인 여부를 검사한다. */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final RoomService rooms;
    private final ImageStore images;
    private final TeacherAuthService auth;
    private final Broadcaster broadcaster;
    /** 로그인 실패는 IP 당 5분에 10번까지 */
    private final RateLimiter loginLimiter = new RateLimiter(10, 5 * 60_000);

    public TeacherController(RoomService rooms, ImageStore images, TeacherAuthService auth, Broadcaster broadcaster) {
        this.rooms = rooms;
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

    // ───────── 방 ─────────

    @GetMapping("/rooms")
    public List<Map<String, Object>> listRooms() {
        return rooms.list().stream().map(r -> Map.<String, Object>of(
                "id", r.getId(),
                "name", r.getName(),
                "createdAt", r.getCreatedAt().toString(),
                "studentCount", r.studentCount())).toList();
    }

    public record CreateRoomRequest(String name) {}

    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest body) {
        Room room = rooms.create(body.name());
        return ResponseEntity.ok(Map.of("id", room.getId()));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomId) {
        rooms.delete(roomId).ifPresent(r -> broadcaster.closeRoom(r.getId()));
        return ResponseEntity.ok(Map.of());
    }

    // ───────── 방 안의 수업 진행 ─────────

    @PostMapping("/rooms/{roomId}/reset")
    public ResponseEntity<?> reset(@PathVariable String roomId) {
        Room room = rooms.get(roomId);
        room.resetClass();
        broadcaster.disconnectStudents(roomId, "reset");
        broadcaster.pushTeachers(room);
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/rooms/{roomId}/questions")
    public ResponseEntity<?> addQuestion(@PathVariable String roomId, @RequestBody QuestionInput body) {
        Room room = rooms.get(roomId);
        long id = room.addQuestion(body).getId();
        broadcaster.pushTeachers(room);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/rooms/{roomId}/questions/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable String roomId, @PathVariable long id, @RequestBody QuestionInput body) {
        Room room = rooms.get(roomId);
        room.updateQuestion(id, body);
        broadcaster.pushTeachers(room);
        return ResponseEntity.ok(Map.of());
    }

    @DeleteMapping("/rooms/{roomId}/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable String roomId, @PathVariable long id) {
        Room room = rooms.get(roomId);
        room.deleteQuestion(id);
        broadcaster.pushTeachers(room);
        return ResponseEntity.ok(Map.of());
    }

    public record NextRequest(Long questionId) {}

    @PostMapping("/rooms/{roomId}/next")
    public ResponseEntity<?> next(@PathVariable String roomId, @RequestBody(required = false) NextRequest body) {
        Room room = rooms.get(roomId);
        room.next(body == null ? null : body.questionId());
        broadcaster.pushAll(room);
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/rooms/{roomId}/close")
    public ResponseEntity<?> close(@PathVariable String roomId) {
        Room room = rooms.get(roomId);
        room.closeCurrent();
        broadcaster.pushAll(room);
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/rooms/{roomId}/students/{id}/kick")
    public ResponseEntity<?> kick(@PathVariable String roomId, @PathVariable String id) {
        Room room = rooms.get(roomId);
        room.kick(id).ifPresent(s -> broadcaster.kickStudent(room, s.getId()));
        broadcaster.pushTeachers(room);
        return ResponseEntity.ok(Map.of());
    }

    public record UnkickRequest(String key) {}

    @PostMapping("/rooms/{roomId}/unkick")
    public ResponseEntity<?> unkick(@PathVariable String roomId, @RequestBody UnkickRequest body) {
        Room room = rooms.get(roomId);
        room.unkick(body.key());
        broadcaster.pushTeachers(room);
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/images")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String id = images.save(file.getBytes());
        return ResponseEntity.ok(Map.of("imageId", id));
    }

    @GetMapping("/rooms/{roomId}/export")
    public ResponseEntity<byte[]> export(@PathVariable String roomId) throws IOException {
        Room room = rooms.get(roomId);
        byte[] xlsx = ExcelExporter.write(room.scoreSheet(), new ByteArrayOutputStream());
        String fileName = room.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + "_점수_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mathMap_scores.xlsx\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
