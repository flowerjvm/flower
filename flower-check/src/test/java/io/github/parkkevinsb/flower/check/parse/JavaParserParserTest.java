package io.github.parkkevinsb.flower.check.parse;

import io.github.parkkevinsb.flower.check.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserParserTest {

    @Test
    void parsesValidJavaSourceIntoAst() {
        SourceUnit unit = new JavaParserParser().parse(source(
                "package demo;\n"
                        + "class Sample {\n"
                        + "    void run() {}\n"
                        + "}\n"));

        assertThat(unit.parsed()).isTrue();
        assertThat(unit.ast()).isPresent();
    }

    @Test
    void fallsBackWhenSourceCannotBeParsed() {
        SourceUnit unit = new JavaParserParser().parse(source(
                "package demo;\n"
                        + "class Broken {\n"
                        + "    void run( {\n"
                        + "}\n"));

        assertThat(unit.parsed()).isFalse();
        assertThat(unit.ast()).isEmpty();
    }

    private static SourceFile source(String content) {
        return new SourceFile(
                "src/test/java/demo/Sample.java",
                "D:/tmp/Sample.java",
                content,
                Arrays.asList(content.split("\n", -1)));
    }
}
