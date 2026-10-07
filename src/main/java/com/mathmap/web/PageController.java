package com.mathmap.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 화면 주소.
 * /             선생님 첫 화면 (로그인, 방 만들기, 내 방 목록)
 * /r/{방id}     학생 공유 주소
 * /t/{방id}     선생님 수업 진행 화면
 * 방이 실제로 있는지, 권한이 있는지는 화면이 부르는 API 에서 서버가 확인한다.
 */
@Controller
public class PageController {

    @GetMapping("/r/{roomId}")
    public String student(@PathVariable String roomId) {
        return "forward:/student.html";
    }

    @GetMapping("/t/{roomId}")
    public String teacher(@PathVariable String roomId) {
        return "forward:/teacher.html";
    }
}
