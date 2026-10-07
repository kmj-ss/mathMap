package com.mathmap.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면이 열려 있는 동안 주기적으로 부르는 주소. 무료 서버가 수업 중에 잠들지 않게 한다. */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().build();
    }
}
