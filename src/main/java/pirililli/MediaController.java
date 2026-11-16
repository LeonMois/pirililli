package pirililli;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Serves data/pictures/* and data/sounds/* so they can be swapped without rebuilding. */
@RestController
class MediaController {
    private final Path pictures, sounds;

    MediaController(Settings settings) {
        this.pictures = settings.dataDir().resolve("pictures").toAbsolutePath().normalize();
        this.sounds = settings.dataDir().resolve("sounds").toAbsolutePath().normalize();
    }

    @GetMapping({"/media/pictures/**", "/media/sounds/**"})
    ResponseEntity<Resource> media(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean isSound = uri.startsWith("/media/sounds/");
        Path base = isSound ? sounds : pictures;
        String name = java.net.URLDecoder.decode(uri.substring(uri.indexOf('/', "/media/".length()) + 1),
                java.nio.charset.StandardCharsets.UTF_8);
        Path file = base.resolve(name).normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        Resource res = new FileSystemResource(file);
        MediaType type = MediaTypeFactory.getMediaType(res).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(res);
    }
}
