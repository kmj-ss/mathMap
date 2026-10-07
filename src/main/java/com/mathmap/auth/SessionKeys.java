package com.mathmap.auth;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

public final class SessionKeys {
    public static final String TEACHER = "MATHMAP_TEACHER";
    /** 방 id → 그 방에서의 학생 id */
    private static final String STUDENTS = "MATHMAP_STUDENTS";

    private SessionKeys() {}

    public static String studentId(HttpSession session, String roomId) {
        if (session == null) {
            return null;
        }
        Object map = session.getAttribute(STUDENTS);
        if (map instanceof Map<?, ?> m) {
            Object id = m.get(roomId);
            return id instanceof String s ? s : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static void setStudentId(HttpSession session, String roomId, String studentId) {
        Object existing = session.getAttribute(STUDENTS);
        HashMap<String, String> map = existing instanceof HashMap<?, ?> m
                ? new HashMap<>((Map<String, String>) m) : new HashMap<>();
        if (studentId == null) {
            map.remove(roomId);
        } else {
            map.put(roomId, studentId);
        }
        session.setAttribute(STUDENTS, map);
    }
}
