package com.oncall.agent.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;

@Service
public class DocumentParserService {
    private static final Pattern HEADING = Pattern.compile(
            "^(#{1,6}\\s+.+|chapter\\s+\\d+[:.\\-\\s].+|[A-Z][A-Z0-9 _/.,:()\\-]{8,}|\\d+(?:\\.\\d+)*\\s+.+)$",
            Pattern.CASE_INSENSITIVE);

    public List<ParsedSection> parseSections(MultipartFile file) {
        String text = extractText(file);
        return splitByHeadings(text, file.getOriginalFilename());
    }

    public List<ParsedSection> parseTextSections(String text, String filename) {
        return splitByHeadings(normalize(text), filename);
    }

    private String extractText(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
            new AutoDetectParser().parse(input, handler, metadata);
            return normalize(handler.toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse " + file.getOriginalFilename(), ex);
        }
    }

    private List<ParsedSection> splitByHeadings(String text, String filename) {
        List<ParsedSection> sections = new ArrayList<>();
        String[] lines = text.split("\\R");
        String currentTitle = filename == null ? "Uploaded document" : filename;
        String currentPath = currentTitle;
        StringBuilder content = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (isHeading(line) && content.length() > 0) {
                sections.add(new ParsedSection(currentTitle, currentPath, content.toString().trim()));
                content.setLength(0);
                currentTitle = cleanHeading(line);
                currentPath = currentTitle;
            } else if (isHeading(line)) {
                currentTitle = cleanHeading(line);
                currentPath = currentTitle;
            } else if (!line.isBlank()) {
                content.append(line).append('\n');
            }
        }

        if (content.length() > 0) {
            sections.add(new ParsedSection(currentTitle, currentPath, content.toString().trim()));
        }
        if (sections.isEmpty() && !text.isBlank()) {
            sections.add(new ParsedSection(currentTitle, currentPath, text.trim()));
        }
        return sections;
    }

    private boolean isHeading(String line) {
        if (line.length() < 4 || line.length() > 140) {
            return false;
        }
        Matcher matcher = HEADING.matcher(line);
        return matcher.matches();
    }

    private String cleanHeading(String line) {
        return line.replaceFirst("^#{1,6}\\s*", "").trim();
    }

    private String normalize(String text) {
        return text.replace('\u0000', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
