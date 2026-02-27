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
     * Human-readable provider name for logging / debugging.
     */
    String getProviderName();
}
