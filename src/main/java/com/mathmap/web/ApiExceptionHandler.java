package com.mathmap.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.mathmap.game.GameException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(GameException.class)
    public ResponseEntity<Map<String, String>> game(GameException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> badBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "요청 형식이 올바르지 않아요."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooBig(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "이미지는 5MB 이하만 올릴 수 있어요."));
    }
}
