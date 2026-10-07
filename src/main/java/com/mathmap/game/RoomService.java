package com.mathmap.game;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 방 목록. 방 주소(id)는 추측할 수 없는 무작위 문자열이고, 학생은 공유받은 주소의 방에만 들어갈 수 있다.
 * 오래 쓰지 않은 방은 자동으로 정리한다.
 */
@Service
public class RoomService {

    /** 헷갈리는 글자(0, o, 1, l, i)를 뺀 문자로 만든 10자리 → 추측 불가 */
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final int ID_LENGTH = 10;
    public static final Pattern ROOM_ID = Pattern.compile("^[" + ALPHABET + "]{" + ID_LENGTH + "}$");
    private static final int MAX_ROOMS = 50;
    private static final Duration IDLE_LIMIT = Duration.ofHours(24);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public synchronized Room create(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > 30) {
            throw new GameException("방 이름은 1~30자로 입력해 주세요.");
        }
        if (rooms.size() >= MAX_ROOMS) {
            throw new GameException("방은 최대 " + MAX_ROOMS + "개까지 만들 수 있어요. 안 쓰는 방을 삭제해 주세요.");
        }
        String id;
        do {
            id = newId();
        } while (rooms.containsKey(id));
        Room room = new Room(id, n);
        rooms.put(id, room);
        return room;
    }

    public Optional<Room> find(String id) {
        if (id == null || !ROOM_ID.matcher(id).matches()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rooms.get(id));
    }

    public Room get(String id) {
        return find(id).orElseThrow(() -> new GameException("방을 찾을 수 없어요."));
    }

    public List<Room> list() {
        List<Room> list = new ArrayList<>(rooms.values());
        list.sort(Comparator.comparing(Room::getCreatedAt).reversed());
        return list;
    }

    public Optional<Room> delete(String id) {
        return Optional.ofNullable(id == null ? null : rooms.remove(id));
    }

    /** 24시간 동안 아무 활동이 없던 방 삭제 (한 시간마다 확인) */
    @Scheduled(fixedRate = 60 * 60 * 1000L)
    public void removeIdleRooms() {
        Instant limit = Instant.now().minus(IDLE_LIMIT);
        rooms.values().removeIf(r -> r.getLastActivity().isBefore(limit));
    }

    private String newId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
