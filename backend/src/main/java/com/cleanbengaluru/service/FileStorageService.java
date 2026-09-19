package com.cleanbengaluru.service;

import com.cleanbengaluru.exception.FileStorageException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Saves uploaded images to a folder on disk and returns the generated file NAME.
 * Only that name goes into MySQL — never the bytes.
 *
 * WHY NOT STORE IMAGES IN MYSQL: a few thousand 3MB photos would bloat the database,
 * slow down every backup, and make simple SELECTs expensive. Files belong in a file store.
 *
 * MOVING TO CLOUD LATER: extract this class into an interface (StorageService) with two
 * implementations — LocalStorageService (this one) and S3StorageService that calls
 * s3Client.putObject(...). Nothing else in the codebase changes, because the rest of the
 * app only ever deals with the returned file name.
 */
@Service
@Slf4j
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(uploadRoot);
            log.info("Upload directory ready at {}", uploadRoot);
        } catch (IOException e) {
            throw new FileStorageException("Could not create upload directory: " + uploadRoot, e);
        }
    }

    /** Validates then stores the file. Returns the stored file name, e.g. "9f1c...-a2.jpg". */
    public String store(MultipartFile file) {
        validate(file);

        String extension = extensionOf(file.getOriginalFilename());
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        try {
            Path target = uploadRoot.resolve(storedName).normalize();

            // Defence against a crafted filename trying to escape the upload folder.
            if (!target.getParent().equals(uploadRoot)) {
                throw new FileStorageException("Invalid file path");
            }

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return storedName;

        } catch (IOException e) {
            throw new FileStorageException("Failed to store file " + file.getOriginalFilename(), e);
        }
    }

    /** Loads a stored file so FileController can stream it back. */
    public Resource load(String fileName) {
        try {
            Path file = uploadRoot.resolve(fileName).normalize();
            if (!file.getParent().equals(uploadRoot)) {
                throw new FileStorageException("Invalid file path");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new FileStorageException("File not found: " + fileName);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new FileStorageException("File not found: " + fileName, e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new FileStorageException("File is larger than the 5MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new FileStorageException("Only JPG, PNG and WEBP images are allowed");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("Only JPG, PNG and WEBP images are allowed");
        }
    }

    private String extensionOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            throw new FileStorageException("File must have an extension");
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
