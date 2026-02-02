package com.ceawse.giftdiscovery.controller;

import com.ceawse.giftdiscovery.dto.ErrorResponseDto;
import com.ceawse.giftdiscovery.exception.InformationException;
import com.ceawse.giftdiscovery.exception.ServiceException;
import com.ceawse.giftdiscovery.service.MessageService;
import com.ceawse.giftdiscovery.utils.ErrorResponseBuilder;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NoResultException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageService messageService;
    private final ErrorResponseBuilder errorResponseBuilder;

    private String includeStacktrace = "never";

    public GlobalExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
        this.errorResponseBuilder = new ErrorResponseBuilder(messageService);
    }

    private boolean isStacktraceEnabled() {
        return "always".equals(includeStacktrace);
    }

    @ExceptionHandler(InformationException.class)
    public ResponseEntity<ErrorResponseDto> resolveInformationException(
            HttpServletRequest request,
            InformationException exception
    ) {
        logRequestException(request, exception);

        String description;
        if (!StringUtils.hasLength(exception.getDescription())) {
            description = messageService.getMessage("common.exception", exception.getMessage());
        } else {
            description = exception.getDescription();
            if (description.startsWith("$")) {
                description = messageService.getMessage(description.substring(1), exception.getMessage());
            }
        }

        return new ResponseEntity<>(
                ErrorResponseDto.builder()
                        .informative(true)
                        .level(exception.getLevel())
                        .message(description)
                        .stacktrace(isStacktraceEnabled() ? exception.getStackTrace() : null)
                        .build(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponseDto> resolveServiceException(
            HttpServletRequest request,
            ServiceException exception
    ) {
        logRequestException(request, exception);

        String description;
        if (!StringUtils.hasLength(exception.getDescription())) {
            description = messageService.getMessage("common.exception", exception.getMessage());
        } else {
            description = exception.getDescription();
        }

        return new ResponseEntity<>(
                ErrorResponseDto.builder()
                        .informative(false)
                        .message(description)
                        .stacktrace(isStacktraceEnabled() ? exception.getStackTrace() : null)
                        .build(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler({ NoResultException.class, EmptyResultDataAccessException.class })
    public ResponseEntity<ErrorResponseDto> resolveNoResult(HttpServletRequest request, Exception exception) {
        logRequestException(request, exception);
        return errorResponseBuilder.makeResponse("entity.empty.exception", exception);
    }

    @ExceptionHandler({ EntityNotFoundException.class })
    public ResponseEntity<ErrorResponseDto> resolveEntityNotFound(HttpServletRequest request, Exception exception) {
        logRequestException(request, exception);
        return errorResponseBuilder.makeResponse("entity.not.found.exception", exception);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> resolveAccessDeniedException(
            HttpServletRequest request,
            Exception exception
    ) {
        logRequestException(request, exception);
        return errorResponseBuilder.makeResponse("access.denied.exception", HttpStatus.FORBIDDEN, exception, true);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponseDto> resolveException(HttpServletRequest request, Exception exception) {
        logRequestException(request, exception);
        return errorResponseBuilder.makeResponse("common.exception", exception);
    }

    private void logRequestException(HttpServletRequest request, Exception exception) {
        log.debug("Unexpected exception processing request: " + request.getRequestURI());
        log.error("Exception: ", exception);
    }
}