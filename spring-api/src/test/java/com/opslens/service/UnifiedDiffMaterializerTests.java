package com.opslens.service;

import com.opslens.github.GitHubFileContent;
import com.opslens.github.MaterializedPatchFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnifiedDiffMaterializerTests {

    private final UnifiedDiffMaterializer materializer =
            new UnifiedDiffMaterializer();

    @Test
    void appliesOneFileUnifiedDiff() {
        String original = """
                public class Example {
                    String normalize(String input) {
                        return input.trim();
                    }
                }
                """;

        String diff = """
                --- a/src/Example.java
                +++ b/src/Example.java
                @@ -1,5 +1,8 @@
                 public class Example {
                     String normalize(String input) {
                +        if (input == null) {
                +            throw new IllegalArgumentException("input");
                +        }
                         return input.trim();
                     }
                 }
                """;

        MaterializedPatchFile result = materializer.apply(
                diff,
                new GitHubFileContent(
                        "src/Example.java",
                        "blob-sha",
                        original
                )
        );

        assertEquals("src/Example.java", result.path());
        assertEquals(true, result.content().contains("input == null"));
    }

    @Test
    void rejectsMultipleFiles() {
        String diff = """
                --- a/One.java
                +++ b/One.java
                @@ -1 +1 @@
                -old
                +new
                --- a/Two.java
                +++ b/Two.java
                @@ -1 +1 @@
                -old
                +new
                """;

        assertThrows(
                PatchMaterializationException.class,
                () -> materializer.targetPath(diff)
        );
    }

    @Test
    void rejectsPathTraversal() {
        String diff = """
                --- a/../secret.txt
                +++ b/../secret.txt
                @@ -1 +1 @@
                -old
                +new
                """;

        assertThrows(
                PatchMaterializationException.class,
                () -> materializer.targetPath(diff)
        );
    }
}