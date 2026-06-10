package io.github.parkkevinsb.flower.check.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads a {@link FlowerCheckConfig} from a config file.
 *
 * <p>Skeleton: returns {@link FlowerCheckConfig#defaults()} when no config file
 * is supplied. The real implementation parses the {@code flower-check.config} described in
 * {@code docs/01-architecture.md} (failOn, per-rule severity, disabled rules,
 * stepBaseClasses, provider names, agent opt-in). Keep the format small and
 * boring; do not pull in a heavy config framework.
 */
public final class ConfigLoader {

    public FlowerCheckConfig load(Optional<Path> configPath) {
        if (configPath.isPresent()) {
            Path path = configPath.get();
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("config file does not exist: " + path);
            }
            throw new IllegalArgumentException("config loading is not implemented yet: " + path);
        }
        return FlowerCheckConfig.defaults();
    }
}
