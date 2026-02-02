package com.ceawse.coreprocessor.controller;

import com.ceawse.coreprocessor.dto.ErrorResponseDto;
import com.ceawse.coreprocessor.exception.InformationException;
import com.ceawse.coreprocessor.exception.ServiceException;
import com.ceawse.coreprocessor.service.MessageProvider;
import com.ceawse.coreprocessor.utils.ErrorResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final MessageProvider messageProvider;
    private final ErrorResponseBuilder errorResponseBuilder;

    @Value("${server.error.include-stacktrace:never}")
    private String includeStacktraceMode;

    @ExceptionHandler(InformationException.class)
    public ResponseEntity<ErrorResponseDto> resolveInformationException(
            HttpServletRequest request,
            InformationException exception
    ) {
        logRequestException(request, exception);
        String description = resolveLocalizedDescription(exception.getDescription(), exception.getMessage());
        return new ResponseEntity<>(
                ErrorResponseDto.builder()
                        .informative(true)
                        .level(exception.getLevel())
                        .message(description)
                        .stacktrace(shouldShowStacktrace() ? exception.getStackTrace() : null)
                        .build(),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponseDto> resolveServiceException(
            HttpServletRequest request,
            ServiceException exception
    ) {
        logRequestException(request, exception);
        String description = resolveLocalizedDescription(exception.getDescription(), exception.getMessage());
        return new ResponseEntity<>(
                ErrorResponseDto.builder()
                        .informative(false)
                        .message(description)
                        .stacktrace(shouldShowStacktrace() ? exception.getStackTrace() : null)
                        .build(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponseDto> resolveException(HttpServletRequest request, Exception exception) {
        logRequestException(request, exception);
        return errorResponseBuilder.makeResponse("common.exception", HttpStatus.INTERNAL_SERVER_ERROR, exception, true);
    }

    private String resolveLocalizedDescription(String rawDescription, String fallbackMessage) {
        if (!StringUtils.hasLength(rawDescription)) {
            return messageProvider.getMessage("common.exception", fallbackMessage);
        }
        if (rawDescription.startsWith("$")) {
            return messageProvider.getMessage(rawDescription.substring(1), fallbackMessage);
        }
        return rawDescription;
    }

    private boolean shouldShowStacktrace() {
        return "always".equalsIgnoreCase(includeStacktraceMode);
    }

    private void logRequestException(HttpServletRequest request, Exception exception) {
        log.error("Request failed: {} {} | Error: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        if (shouldShowStacktrace()) {
            log.debug("Full stacktrace: ", exception);
        }
    }
}