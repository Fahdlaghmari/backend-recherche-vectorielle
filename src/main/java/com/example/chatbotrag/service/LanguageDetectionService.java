package com.example.chatbotrag.service;

import org.apache.tika.language.LanguageIdentifier;
import org.springframework.stereotype.Service;

@Service
public class LanguageDetectionService {

    public String detectLanguage(String text) {
        LanguageIdentifier identifier = new LanguageIdentifier(text);
        return identifier.getLanguage();
    }
}
