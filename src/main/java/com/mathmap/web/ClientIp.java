package com.mathmap.web;

import jakarta.servlet.http.HttpServletRequest;

final class ClientIp {
    private ClientIp() {}

    /**
     * 클라우드 프록시 뒤에서는 프록시가 X-Forwarded-For 맨 끝에 실제 접속 주소를 붙인다.
     * 앞쪽 값은 접속자가 마음대로 넣을 수 있으므로 맨 끝 값을 쓴다.
     */
    static String of(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        return req.getRemoteAddr();
    }
}
