package com.rap.generator.utils;

import com.rap.generator.generators.GeneratorEngine.GeneratedArtifact;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ExportUtil {

    /**
     * Export all generated artifacts to individual files in a directory.
     */
    public static int exportToDirectory(Map<String, GeneratedArtifact> artifacts, String directory) throws IOException {
        Path dir = Path.of(directory);
        Files.createDirectories(dir);

        int count = 0;
        for (Map.Entry<String, GeneratedArtifact> entry : artifacts.entrySet()) {
            GeneratedArtifact artifact = entry.getValue();
            Path file = dir.resolve(artifact.getFileName());
            Files.writeString(file, artifact.getSourceCode(), StandardCharsets.UTF_8);
            count++;
        }
        return count;
    }

    /**
     * Export all artifacts as a single concatenated text.
     */
    public static String exportAsText(Map<String, GeneratedArtifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, GeneratedArtifact> entry : artifacts.entrySet()) {
            sb.append("*======================================================================*\n");
            sb.append("* ").append(entry.getKey()).append("\n");
            sb.append("* File: ").append(entry.getValue().getFileName()).append("\n");
            sb.append("* Type: ").append(entry.getValue().getObjectType()).append("\n");
            sb.append("*======================================================================*\n\n");
            sb.append(entry.getValue().getSourceCode());
            sb.append("\n\n\n");
        }
        return sb.toString();
    }
}
