package com.mathmap.game;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/** 선생님이 올린 문제 이미지를 메모리에 보관. 파일 내용(앞부분 바이트)으로 실제 이미지인지 확인한다. */
@Service
public class ImageStore {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGES = 300;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, StoredImage> images = new LinkedHashMap<>();

    public synchronized String save(byte[] data) {
        if (data == null || data.length == 0) {
            throw new GameException("이미지 파일이 비어 있어요.");
        }
        if (data.length > MAX_BYTES) {
            throw new GameException("이미지는 5MB 이하만 올릴 수 있어요.");
        }
        String type = detectType(data);
        if (type == null) {
            throw new GameException("PNG, JPG, GIF, WEBP 이미지만 올릴 수 있어요.");
        }
        if (images.size() >= MAX_IMAGES) {
            String oldest = images.keySet().iterator().next();
            images.remove(oldest);
        }
        byte[] b = new byte[16];
        random.nextBytes(b);
        String id = HexFormat.of().formatHex(b);
        images.put(id, new StoredImage(type, data));
        return id;
    }

    public synchronized Optional<StoredImage> get(String id) {
        return Optional.ofNullable(images.get(id));
    }

    static String detectType(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8 && (d[2] & 0xff) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
            return "image/gif";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public record StoredImage(String contentType, byte[] data) {}
}
