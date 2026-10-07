package com.mathmap.web;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.mathmap.game.ImageStore;

/** 문제 이미지 보기. 이미지 주소는 추측할 수 없는 무작위 값이다. */
@RestController
public class ImageController {

    private final ImageStore images;

    public ImageController(ImageStore images) {
        this.images = images;
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        if (!id.matches("^[0-9a-f]{32}$")) {
            return ResponseEntity.notFound().build();
        }
        return images.get(id)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.contentType()))
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                        .body(img.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
