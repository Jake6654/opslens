package com.opslens.service;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.opslens.github.GitHubFileContent;
import com.opslens.github.MaterializedPatchFile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Parses a one-file unified diff and applies it to exact GitHub file content
 */
@Service
public class UnifiedDiffMaterializer {

    /**
     * Reads and validates the destination file path from a unified diff.
     */
    public String targetPath(String suggestedDiff) {
        List<String> lines = normalizedLines(suggestedDiff);

        List<String> oldHeaders = lines.stream()
                .filter(line -> line.startsWith("--- "))
                .toList();

        List<String> newHeaders = lines.stream()
                .filter(line -> line.startsWith("+++ "))
                .toList();

        if (oldHeaders.size() != 1 || newHeaders.size() != 1) {
            throw new PatchMaterializationException(
                    "Phase 5C requires a unified diff for exactly one file."
            );
        }

        String oldPath = parseHeaderPath(oldHeaders.getFirst(), "--- ");
        String newPath = parseHeaderPath(newHeaders.getFirst(), "+++ ");

        if ("/dev/null".equals(oldPath)
                || "/dev/null".equals(newPath)) {
            throw new PatchMaterializationException(
                    "New-file creation and deletion are not supported in "
                            + "the Phase 5C MVP."
            );
        }

        if (!oldPath.equals(newPath)) {
            throw new PatchMaterializationException(
                    "File renames are not supported in the Phase 5C MVP."
            );
        }

        validateRepositoryPath(newPath);
        return newPath;
    }

    /**
     *  Applies the unified diff to the exact file content fetched from GitHub
     */
    public MaterializedPatchFile apply(
            String suggestedDiff,
            GitHubFileContent originalFile
    ) {
        String targetPath = targetPath(suggestedDiff);

        if (!targetPath.equals(originalFile.path())) {
            throw new PatchMaterializationException(
                    "The diff target does not match the fetched GitHub file."
            );
        }

        List<String> diffLines = normalizedLines(suggestedDiff);
        List<String> originalLines = originalFile.content()
                .lines()
                .toList();

        try {
            Patch<String> patch =
                    UnifiedDiffUtils.parseUnifiedDiff(diffLines);

            List<String> patchedLines = patch.applyTo(originalLines);
            String patchedContent = String.join("\n", patchedLines);

            // Preserve the source file's final newline convention.
            if (originalFile.content().endsWith("\n")) {
                patchedContent += "\n";
            }

            return new MaterializedPatchFile(
                    targetPath,
                    patchedContent
            );
        } catch (PatchFailedException | RuntimeException error) {
            throw new PatchMaterializationException(
                    "The suggested diff does not apply to the GitHub file "
                            + "at the expected commit.",
                    error
            );
        }
    }

    private List<String> normalizedLines(String suggestedDiff) {
        if (suggestedDiff == null || suggestedDiff.isBlank()) {
            throw new PatchMaterializationException(
                    "A non-empty suggested diff is required."
            );
        }

        String normalized = suggestedDiff
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        if (normalized.startsWith("```")) {
            throw new PatchMaterializationException(
                    "Markdown code fences are not allowed in suggestedDiff."
            );
        }

        return normalized.lines().toList();
    }

    private String parseHeaderPath(
            String header,
            String prefix
    ) {
        String path = header.substring(prefix.length()).trim();

        // Unified diff timestamps, when present, follow a tab character.
        int tabIndex = path.indexOf('\t');
        if (tabIndex >= 0) {
            path = path.substring(0, tabIndex);
        }

        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2);
        }

        return path;
    }

    private void validateRepositoryPath(String path) {
        if (path.isBlank()) {
            throw new PatchMaterializationException(
                    "The diff does not contain a target file path."
            );
        }

        Path normalized = Path.of(path).normalize();

        if (normalized.isAbsolute()
                || normalized.startsWith("..")
                || path.contains("\\")) {
            throw new PatchMaterializationException(
                    "The diff target path is unsafe: " + path
            );
        }

        String normalizedText = normalized.toString().replace('\\', '/');
        if (!normalizedText.equals(path)) {
            throw new PatchMaterializationException(
                    "The diff target path must already be normalized: "
                            + path
            );
        }
    }
}


