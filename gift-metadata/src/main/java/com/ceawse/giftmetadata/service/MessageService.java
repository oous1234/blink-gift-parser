package com.ceawse.giftmetadata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    public static Locale russian = new Locale("ru", "RU");
    private final ResourceBundleMessageSource messageSource;

    public String getMessage(String code) {
        return getMessage(code, russian);
    }

    public String getMessage(String code, Locale locale) {
        try {
            return messageSource.getMessage(code, null, Optional.ofNullable(locale).orElse(russian));
        } catch (Exception e) {
            log.warn("Get message key='" + code + "' error:" + e.getMessage());
        }
        return code;
    }

    public String getMessage(String code, Object... args) {
        return messageSource.getMessage(code, args, russian);
    }

    public String getMessage(Enum e) {
        Assert.notNull(e, "Error convert enum value to message. Enum value is null.");
        String className = e.getClass().getName();
        return getMessage(className + "." + e);
    }
}