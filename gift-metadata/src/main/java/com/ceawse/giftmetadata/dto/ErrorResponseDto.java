package com.ceawse.giftmetadata.dto;

import com.ceawse.giftmetadata.type.ErrorLevel;
import com.ceawse.giftmetadata.utils.StackElementSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ErrorResponseDto {

    private boolean informative;

    private ErrorLevel level;
    private String message;

    @JsonSerialize(using = StackElementSerializer.class)
    private StackTraceElement[] stacktrace;

    public static ErrorResponseDtoBuilder builder(String message, Throwable cause) {
        return new ErrorResponseDtoBuilder()
                .message(message)
                .stacktrace(cause.getStackTrace())
                .informative(false);
    }

    public static ErrorResponseDtoBuilder builder(String message) {
        return new ErrorResponseDtoBuilder()
                .message(message)
                .informative(false);
    }

    public static ErrorResponseDtoBuilder builder() {
        return new ErrorResponseDtoBuilder();
    }
}

