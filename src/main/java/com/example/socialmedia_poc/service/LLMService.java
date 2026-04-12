package com.example.socialmedia_poc.service;

/**
 * Abstraction for LLM providers (Grok cloud, Ollama local).
 */
public interface LLMService {

    /**
     * Generate content from a single user prompt.
     */
    String generateContent(String prompt) throws Exception;

    /**
     * Generate content with an explicit system message and user prompt.
     */
    String generateContent(String systemMessage, String prompt) throws Exception;

    /**
     * Generate content with an explicit system message, prompt, and custom max tokens.
     */
    default String generateContent(String systemMessage, String prompt, int maxTokens) throws Exception {
        return generateContent(systemMessage, prompt);
    }

    /**
     * Human-readable provider name for logging / debugging.
     */
    String getProviderName();
}
