package com.mathmap.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 아주 단순한 시간창 기반 요청 제한 (키별로 window 동안 max 회) */
public class RateLimiter {

    private final int max;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int max, long windowMillis) {
        this.max = max;
        this.windowMillis = windowMillis;
    }

    /** 이번 시도를 기록하고, 허용되면 true */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMillis) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
        }
        if (hits.size() > 10_000) {
            cleanup(now);
        }
        return true;
    }

    private void cleanup(long now) {
        Iterator<Map.Entry<String, Deque<Long>>> it = hits.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> q = it.next().getValue();
            synchronized (q) {
                if (q.isEmpty() || now - q.peekLast() > windowMillis) {
                    it.remove();
                }
            }
        }
    }
}
