package com.cleanbengaluru.controller;

import com.cleanbengaluru.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/files/{fileName} — serves an uploaded photo. Public so <img src> works simply. */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> serve(@PathVariable String fileName) {
        Resource resource = fileStorageService.load(fileName);

        MediaType mediaType = fileName.toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(resource);
    }
}
