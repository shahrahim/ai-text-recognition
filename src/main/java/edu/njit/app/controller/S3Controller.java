package edu.njit.app.controller;

import edu.njit.app.service.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/image")
public class S3Controller {

    private final S3Service service;

    public S3Controller(S3Service service) {
        this.service = service;
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Mono<byte[]>> getAllCourse(@PathVariable String fileId) {
        return ResponseEntity.ok().body(this.service.getImageTest(fileId));
    }
}
