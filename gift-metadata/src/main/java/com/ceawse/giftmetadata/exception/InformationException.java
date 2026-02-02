package com.ceawse.giftmetadata.exception;

import com.ceawse.giftmetadata.type.ErrorLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class InformationException extends RuntimeException {

    private ErrorLevel level;
    private String description;

    public InformationException(String description, Throwable cause, ErrorLevel level) {
        super(cause != null ? cause.getMessage() : description, cause);
        this.level = level;
        this.description = description;
    }

    public static InformationExceptionBuilder builder(String description, Throwable throwable) {
        return new InformationExceptionBuilder()
                .description(description)
                .throwable(throwable);
    }

    public static InformationExceptionBuilder builder(String description) {
        return new InformationExceptionBuilder()
                .description(description);
    }

    public static InformationExceptionBuilder builder(String description, ErrorLevel level) {
        return new InformationExceptionBuilder()
                .description(description)
                .level(level);
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    public static class InformationExceptionBuilder {

        private ErrorLevel level = ErrorLevel.ERROR;
        private String description;
        private Throwable throwable;

        InformationExceptionBuilder() {
        }

        public InformationException build() {
            return new InformationException(description, throwable, level);
        }
    }
}
