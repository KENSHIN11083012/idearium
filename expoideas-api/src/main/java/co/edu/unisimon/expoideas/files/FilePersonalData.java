package co.edu.unisimon.expoideas.files;

import co.edu.unisimon.expoideas.users.PersonalDataSource;
import co.edu.unisimon.expoideas.users.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lo que este módulo guarda de una persona: los archivos que subió, sean su
 * foto, entregables o plantillas. Va la ficha de cada uno, no el contenido: el
 * archivo mismo lo descarga desde donde lo subió.
 */
@Component
@RequiredArgsConstructor
class FilePersonalData implements PersonalDataSource {

    private final FileRepository fileRepository;

    record OwnedFile(String name, String contentType, int sizeBytes, LocalDateTime uploadedAt) {}

    @Override
    public Map<String, List<?>> dataOf(User user) {
        List<OwnedFile> files = fileRepository.findByOwner(user).stream()
                .map(file -> new OwnedFile(
                        file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.getCreatedAt()))
                .toList();
        return Map.of("files", files);
    }
}
