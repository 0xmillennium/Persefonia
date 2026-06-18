package dev.persefonia.medialibrary.application.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class ChecksumCalculatorTest {
    @Test
    void checksumIsStableForTheSameBytes() throws Exception {
        ChecksumCalculator calculator = new ChecksumCalculator();
        byte[] content = "stable media".getBytes();

        String first = calculator.calculate(new ByteArrayInputStream(content));
        String second = calculator.calculate(new ByteArrayInputStream(content));

        assertThat(first).isEqualTo(second).hasSize(64);
    }
}
