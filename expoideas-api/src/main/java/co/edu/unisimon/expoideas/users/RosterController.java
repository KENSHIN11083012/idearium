package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.files.FileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** El listado de la cátedra: lo carga, consulta y limpia la gestión (SecurityConfig: /admin/**). */
@RestController
@RequestMapping("/api/v1/admin/roster")
@RequiredArgsConstructor
public class RosterController {

    private final RosterService rosterService;

    @GetMapping
    public List<RosterEntryResponse> list() {
        return rosterService.list();
    }

    /** Carga un CSV; devuelve qué entró y qué se rechazó. */
    @PostMapping
    public RosterImportResponse importCsv(@RequestPart(FileService.FIELD) MultipartFile file) {
        return rosterService.importCsv(file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Integer id) {
        rosterService.remove(id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        rosterService.clear();
    }
}
