package com.aigreentick.services.storage.api;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorCodeTest {

    @Test
    void everyCodeHasAnErrorStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.httpStatus()).as(code.name()).isBetween(400, 599);
        }
    }

    /** template-service branches on these per-file names; renaming one silently breaks media sync. */
    @Test
    void perFileCodesTemplateServiceReadsStillExist() {
        for (String name : List.of("QUOTA_EXCEEDED", "QUOTA_NOT_PROVISIONED", "CONTENT_TYPE_NOT_ALLOWED",
                "CONTENT_TYPE_MISMATCH", "MEDIA_TOO_LARGE", "BATCH_ITEM_SKIPPED", "INTERNAL_ERROR")) {
            assertThat(ErrorCode.valueOf(name)).isNotNull();
        }
    }

    @Test
    void wrapperRefusesMismatchedStatus() {
        assertThatThrownBy(() -> ApiResponse.success(HttpStatus.BAD_REQUEST, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiResponse.error(200, "X", "x", List.of(), "/p"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
