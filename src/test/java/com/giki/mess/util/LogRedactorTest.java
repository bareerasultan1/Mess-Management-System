package com.giki.mess.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LogRedactor")
class LogRedactorTest {

    @Test
    @DisplayName("masks the individual part of a student id")
    void masksStudentId() {
        assertThat(LogRedactor.maskStudentId("2023294")).isEqualTo("202****");
    }

    @Test
    @DisplayName("null, blank and very short ids never leak")
    void handlesOddInput() {
        assertThat(LogRedactor.maskStudentId(null)).isEqualTo("****");
        assertThat(LogRedactor.maskStudentId(" ")).isEqualTo("****");
        assertThat(LogRedactor.maskStudentId("7")).isEqualTo("****");
    }
}
