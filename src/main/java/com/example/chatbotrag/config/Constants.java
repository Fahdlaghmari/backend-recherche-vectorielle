package com.example.chatbotrag.config;

/**
 * Application-wide constants
 */
public final class Constants {
    
    /**
     * The only ChromaDB collection name used throughout the application
     */
    public static final String CHROMA_COLLECTION_NAME = "emsi-ai-collection";
    
    // Private constructor to prevent instantiation
    private Constants() {
        throw new AssertionError("Constants class should not be instantiated");
    }
}
