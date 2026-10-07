package com.mathmap.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 응답에 보안 헤더를 붙이고, 상태를 바꾸는 API 요청은 같은 사이트에서 보낸 JSON 요청만 받는다.
 * (다른 사이트가 몰래 폼을 제출하거나 주소창으로 조작하는 것을 막음)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final boolean hsts;

    public SecurityHeadersFilter(@Value("${mathmap.secure-headers.hsts:false}") boolean hsts) {
        this.hsts = hsts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'; object-src 'none'");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (hsts) {
            res.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        String path = req.getRequestURI();
        if (path.startsWith("/api/")) {
            res.setHeader("Cache-Control", "no-store");
            String method = req.getMethod();
            boolean changesState = !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));
            if (changesState && !isSameOriginApiCall(req)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    /** 우리 화면의 자바스크립트만 붙이는 헤더 + Origin 이 있으면 같은 사이트여야 함 */
    private static boolean isSameOriginApiCall(HttpServletRequest req) {
        if (!"1".equals(req.getHeader("X-MathMap"))) {
            return false;
        }
        String origin = req.getHeader("Origin");
        if (origin == null) {
            return true;
        }
        return matchesHost(origin, req.getHeader("Host")) || matchesHost(origin, req.getHeader("X-Forwarded-Host"));
    }

    private static boolean matchesHost(String origin, String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.split(",")[0].trim();
        return origin.equals("https://" + h) || origin.equals("http://" + h);
    }
}
