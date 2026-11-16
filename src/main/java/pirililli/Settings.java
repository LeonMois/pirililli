package pirililli;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/** The {@code pirililli.*} entries of config.properties. */
@ConfigurationProperties("pirililli")
public record Settings(
        @DefaultValue("data") Path dataDir,
        @DefaultValue("50") int votersCount,
        @DefaultValue("10") int thanksSeconds,
        @DefaultValue("pt") String defaultLanguage,
        @DefaultValue("en") String gringoLanguage) {

    String languageFor(Model.Voter voter) {
        return voter.gringo() ? gringoLanguage : defaultLanguage;
    }
}
