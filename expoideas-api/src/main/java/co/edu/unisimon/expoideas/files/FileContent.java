package co.edu.unisimon.expoideas.files;

import org.springframework.core.io.Resource;

/** Lo necesario para responder una descarga ya autorizada. */
public record FileContent(
        String name, String contentType, long sizeBytes, String sha256, boolean isPublic, Resource resource) {}
