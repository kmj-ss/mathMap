package com.mathmap.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 선생님 비밀번호 확인. 비밀번호는 환경변수 MATHMAP_TEACHER_PASSWORD 로 정한다.
 * 비어 있으면 실행할 때마다 임시 비밀번호를 만들어 서버 로그에만 출력한다.
 */
@Service
public class TeacherAuthService {

    private static final Logger log = LoggerFactory.getLogger(TeacherAuthService.class);

    private final byte[] passwordHash;

    public TeacherAuthService(@Value("${mathmap.teacher-password:}") String configured) {
        String password = configured;
        if (password == null || password.isBlank()) {
            byte[] b = new byte[6];
            new SecureRandom().nextBytes(b);
            password = HexFormat.of().formatHex(b);
            log.warn("MATHMAP_TEACHER_PASSWORD 가 설정되지 않아 임시 선생님 비밀번호를 만들었습니다: {}", password);
        } else if (password.length() < 8) {
            log.warn("선생님 비밀번호가 8자보다 짧습니다. 더 긴 비밀번호를 권장합니다.");
        }
        this.passwordHash = sha256(password);
    }

    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        // 해시끼리 상수 시간 비교 (응답 시간으로 비밀번호를 추측하지 못하게)
        return MessageDigest.isEqual(passwordHash, sha256(candidate));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
