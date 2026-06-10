package io.github.parkkevinsb.flower.check.cli;

/**
 * Maven-friendly entry point for build lifecycle execution.
 *
 * <p>{@link FlowerCheckCli} owns the normal CLI contract and calls
 * {@code System.exit} from its public {@code main}. The Maven exec plugin runs
 * in-process, so this adapter converts a non-zero checker exit code into an
 * exception instead.
 */
public final class FlowerCheckVerify {

    private FlowerCheckVerify() {
    }

    public static void main(String[] args) {
        int code = new FlowerCheckCli().execute(args, System.out, System.err);
        if (code != ExitCode.OK) {
            throw new IllegalStateException("flower-check failed with exit code " + code);
        }
    }
}
