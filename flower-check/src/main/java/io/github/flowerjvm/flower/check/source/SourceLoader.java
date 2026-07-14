package io.github.flowerjvm.flower.check.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Walks one or more source roots and reads every {@code .java} file into a
 * {@link SourceFile}. IO only — no parsing, no rule logic.
 *
 * <p>Generated-source and build-output directories are skipped so the checker
 * never reports on machine output it does not own.
 */
public final class SourceLoader {

    private static final List<String> SKIP_DIR_SEGMENTS = Arrays.asList(
            "target", "build", "out", "generated", "generated-sources", ".git", ".idea");

    /** Load all Java files under the given roots, relativized to the first root. */
    public List<SourceFile> load(List<String> roots) {
        List<SourceFile> result = new ArrayList<>();
        for (String root : roots) {
            Path rootPath = Paths.get(root).toAbsolutePath().normalize();
            if (!Files.isDirectory(rootPath) && !Files.isRegularFile(rootPath)) {
                throw new IllegalArgumentException("source path does not exist: " + root);
            }
            loadRoot(rootPath, result);
        }
        return result;
    }

    private void loadRoot(Path rootPath, List<SourceFile> sink) {
        try (Stream<Path> walk = Files.walk(rootPath)) {
            List<Path> javaFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !isSkipped(rootPath, p))
                    .collect(Collectors.toList());
            for (Path p : javaFiles) {
                sink.add(read(rootPath, p));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to walk source root: " + rootPath, e);
        }
    }

    private boolean isSkipped(Path rootPath, Path file) {
        Path relative = rootPath.relativize(file);
        for (Path segment : relative) {
            if (SKIP_DIR_SEGMENTS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private SourceFile read(Path rootPath, Path file) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            List<String> lines = Arrays.asList(content.split("\n", -1));
            Path base = Files.isDirectory(rootPath) ? rootPath : rootPath.getParent();
            String relative = (base == null ? file : base.relativize(file)).toString().replace('\\', '/');
            return new SourceFile(relative, file.toString(), content, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read source file: " + file, e);
        }
    }
}
