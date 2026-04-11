package com.example.socialmedia_poc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thread-safe, mutable holder for API keys.
 * Initialised from application.properties / env-vars, but can be
 * updated at runtime through the Admin Settings UI.
 */
@Component
public class ApiKeyStore {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStore.class);

    private volatile String huggingFaceApiKey;
    private volatile String grokApiKey;

    public ApiKeyStore(@Value("${huggingface.api-key:}") String huggingFaceApiKey,
                       @Value("${grok.api-key:}") String grokApiKey) {
        this.huggingFaceApiKey = huggingFaceApiKey;
        this.grokApiKey = grokApiKey;
        log.info("[ApiKeyStore] Initialised — HF key present: {}, Grok key present: {}",
                !huggingFaceApiKey.isBlank(), !grokApiKey.isBlank());
    }

    // ── HuggingFace ──

    public String getHuggingFaceApiKey() {
        return huggingFaceApiKey;
    }

    public void setHuggingFaceApiKey(String key) {
        this.huggingFaceApiKey = key != null ? key.trim() : "";
        log.info("[ApiKeyStore] HuggingFace API key updated (length={})", this.huggingFaceApiKey.length());
    }

    // ── Grok (xAI) ──

    public String getGrokApiKey() {
        return grokApiKey;
    }

    public void setGrokApiKey(String key) {
        this.grokApiKey = key != null ? key.trim() : "";
        log.info("[ApiKeyStore] Grok API key updated (length={})", this.grokApiKey.length());
    }

    // ── Helpers ──

    /** Returns a masked version of a key for display (e.g. "hf_hQ...IIqt"). */
    public static String mask(String key) {
        if (key == null || key.length() < 8) return key == null || key.isEmpty() ? "(not set)" : "****";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
