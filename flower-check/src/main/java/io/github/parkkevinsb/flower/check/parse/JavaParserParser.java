package io.github.parkkevinsb.flower.check.parse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Providers;
import com.github.javaparser.ast.CompilationUnit;
import io.github.parkkevinsb.flower.check.source.SourceFile;

import java.util.Objects;
import java.util.Optional;

/**
 * JavaParser-backed parser used by the default checker pipeline.
 *
 * <p>Concrete JavaParser AST types stay inside the parse package. Other layers
 * receive a {@link SourceUnit} whose AST is deliberately typed as Object.
 */
public final class JavaParserParser implements Parser {

    private final JavaParser javaParser;
    private final Parser fallback;

    public JavaParserParser() {
        this(new TextFallbackParser());
    }

    public JavaParserParser(Parser fallback) {
        this(createJavaParser(), fallback);
    }

    JavaParserParser(JavaParser javaParser, Parser fallback) {
        this.javaParser = Objects.requireNonNull(javaParser, "javaParser");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public SourceUnit parse(SourceFile file) {
        Objects.requireNonNull(file, "file");
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(
                    ParseStart.COMPILATION_UNIT,
                    Providers.provider(file.content()));
            Optional<CompilationUnit> unit = result.getResult();
            if (result.isSuccessful() && unit.isPresent()) {
                return new SourceUnit(file, unit.get(), true);
            }
        } catch (RuntimeException ignored) {
            // Malformed or unsupported source must not abort a checker run.
        }
        return fallback.parse(file);
    }

    private static JavaParser createJavaParser() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT);
        return new JavaParser(configuration);
    }
}
