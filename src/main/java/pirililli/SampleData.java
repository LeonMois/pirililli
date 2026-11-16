package pirililli;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes example spreadsheets and placeholder pictures so the app works out of the box. */
final class SampleData {
    private SampleData() {}

    /** Creates each sample file that doesn't exist yet. */
    static void createMissing(Path dir) throws IOException {
        if (!Files.exists(Catalog.xlsx(dir, "candidates"))) {
            candidates(dir);
            System.out.println("Created sample " + Catalog.xlsx(dir, "candidates").toAbsolutePath());
        }
        if (!Files.exists(Catalog.xlsx(dir, "questions"))) {
            questions(dir);
            System.out.println("Created sample " + Catalog.xlsx(dir, "questions").toAbsolutePath());
        }
        if (!Files.exists(Catalog.xlsx(dir, "translations"))) {
            List<List<String>> rows = new ArrayList<>();
            Translations.DEFAULTS.forEach((k, v) -> rows.add(List.of(k, v[0], v[1])));
            Spreadsheets.write(Catalog.xlsx(dir, "translations"), "Translations", List.of("key", "pt", "en"), rows);
            System.out.println("Created " + Catalog.xlsx(dir, "translations").toAbsolutePath());
        }
    }

    private static void candidates(Path dir) throws IOException {
        String[][] candidates = {
                {"10", "Ana Souza", "PDF - Partido da Festa"},
                {"13", "Bruno Lima", "PCC - Partido do Churrasco"},
                {"22", "Carla Mendes", "PSD - Partido do Samba"},
                {"25", "Diego Rocha", "PDF - Partido da Festa"},
                {"33", "Eduarda Alves", "PPP - Partido da Picanha"},
                {"42", "Felipe Costa", "PCC - Partido do Churrasco"},
                {"55", "Gabriela Nunes", "PSD - Partido do Samba"},
                {"77", "Hugo Martins", "PPP - Partido da Picanha"},
        };
        Path pics = dir.resolve("pictures");
        Files.createDirectories(pics);
        List<List<String>> cRows = new ArrayList<>();
        int i = 0;
        for (String[] c : candidates) {
            String file = c[0] + ".png";
            Path target = pics.resolve(file);
            if (!Files.exists(target)) ImageIO.write(avatar(c[1], i++), "png", target.toFile());
            cRows.add(List.of(c[0], c[1], c[2], file));
        }
        Spreadsheets.write(Catalog.xlsx(dir, "candidates"), "Candidates", Catalog.CANDIDATE_HEADER, cRows);
    }

    private static void questions(Path dir) throws IOException {
        Spreadsheets.write(Catalog.xlsx(dir, "questions"), "Questions", List.of("pt", "en", "candidates"), List.of(
                List.of("Melhor dançarino(a) do ano", "Best dancer of the year", ""),
                List.of("Mais atrasado(a) do ano", "Most late person of the year", ""),
                List.of("Melhor churrasqueiro(a)", "Best barbecue master", "13, 33, 42, 77"),
                List.of("Rainha da festa", "Queen of the party", "10, 22, 33, 55"),
                List.of("Viagem mais épica do ano", "Most epic trip of the year", "")
        ));
    }

    private static BufferedImage avatar(String name, int seed) {
        Color[] palette = {new Color(0x2E7D32), new Color(0x1565C0), new Color(0xC62828), new Color(0x6A1B9A),
                new Color(0xEF6C00), new Color(0x00838F), new Color(0x4E342E), new Color(0x37474F)};
        int w = 300, h = 360;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xDDDDDD));
        g.fillRect(0, 0, w, h);
        g.setColor(palette[seed % palette.length]);
        g.fillOval(75, 50, 150, 150);          // head
        g.fillRoundRect(30, 215, 240, 200, 120, 120); // shoulders
        String initials = "";
        for (String part : name.split(" ")) if (!part.isEmpty()) initials += part.charAt(0);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(initials, (w - fm.stringWidth(initials)) / 2, 125 + fm.getAscent() / 2 - 8);
        g.dispose();
        return img;
    }
}
