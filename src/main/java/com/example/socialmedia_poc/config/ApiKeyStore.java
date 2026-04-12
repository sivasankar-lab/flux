package com.example.socialmedia_poc.config;

import com.example.socialmedia_poc.model.AppSetting;
import com.example.socialmedia_poc.repository.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * Thread-safe, mutable holder for API keys.
 * Keys are AES-256-GCM encrypted and persisted to the app_settings table.
 * On startup the DB is checked first; env-vars are used as fallback.
 */
@Component
public class ApiKeyStore {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStore.class);

    private static final String KEY_HF = "huggingface_api_key";
    private static final String KEY_GROK = "grok_api_key";
    private static final String KEY_POOL_GEN = "pool_generation_enabled";

    private final AppSettingRepository settingRepo;
    private final EncryptionUtil encryption;

    private volatile String huggingFaceApiKey;
    private volatile String grokApiKey;
    private volatile boolean poolGenerationEnabled = true;

    private final String envHfKey;
    private final String envGrokKey;

    public ApiKeyStore(AppSettingRepository settingRepo,
                       EncryptionUtil encryption,
                       @Value("${huggingface.api-key:}") String envHfKey,
                       @Value("${grok.api-key:}") String envGrokKey) {
        this.settingRepo = settingRepo;
        this.encryption = encryption;
        this.envHfKey = envHfKey;
        this.envGrokKey = envGrokKey;
    }

    @PostConstruct
    public void init() {
        // Load from DB, falling back to env-vars
        this.huggingFaceApiKey = loadDecrypted(KEY_HF).orElse(envHfKey != null ? envHfKey : "");
        this.grokApiKey = loadDecrypted(KEY_GROK).orElse(envGrokKey != null ? envGrokKey : "");
        this.poolGenerationEnabled = settingRepo.findById(KEY_POOL_GEN)
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);

        log.info("[ApiKeyStore] Initialised — HF key present: {}, Grok key present: {}, Pool gen: {}",
                !huggingFaceApiKey.isBlank(), !grokApiKey.isBlank(), poolGenerationEnabled);
    }

    // ── HuggingFace ──

    public String getHuggingFaceApiKey() {
        return huggingFaceApiKey;
    }

    public void setHuggingFaceApiKey(String key) {
        this.huggingFaceApiKey = key != null ? key.trim() : "";
        persistEncrypted(KEY_HF, this.huggingFaceApiKey);
        log.info("[ApiKeyStore] HuggingFace API key updated (length={})", this.huggingFaceApiKey.length());
    }

    // ── Grok (xAI) ──

    public String getGrokApiKey() {
        return grokApiKey;
    }

    public void setGrokApiKey(String key) {
        this.grokApiKey = key != null ? key.trim() : "";
        persistEncrypted(KEY_GROK, this.grokApiKey);
        log.info("[ApiKeyStore] Grok API key updated (length={})", this.grokApiKey.length());
    }

    // ── Pool Generation Toggle ──

    public boolean isPoolGenerationEnabled() {
        return poolGenerationEnabled;
    }

    public void setPoolGenerationEnabled(boolean enabled) {
        this.poolGenerationEnabled = enabled;
        persistPlain(KEY_POOL_GEN, String.valueOf(enabled));
        log.info("[ApiKeyStore] Pool generation {}", enabled ? "ENABLED" : "DISABLED");
    }

    // ── Helpers ──

    /** Returns a masked version of a key for display (e.g. "hf_hQ...IIqt"). */
    public static String mask(String key) {
        if (key == null || key.length() < 8) return key == null || key.isEmpty() ? "(not set)" : "****";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    // ── DB persistence helpers ──

    private Optional<String> loadDecrypted(String key) {
        return settingRepo.findById(key)
                .map(s -> {
                    try {
                        return encryption.decrypt(s.getSettingValue());
                    } catch (Exception e) {
                        log.warn("[ApiKeyStore] Could not decrypt '{}', using raw value", key);
                        return s.getSettingValue();
                    }
                })
                .filter(v -> v != null && !v.isBlank());
    }

    private void persistEncrypted(String key, String plainValue) {
        try {
            String encrypted = (plainValue != null && !plainValue.isEmpty())
                    ? encryption.encrypt(plainValue) : "";
            AppSetting setting = settingRepo.findById(key).orElse(new AppSetting(key, ""));
            setting.setSettingValue(encrypted);
            settingRepo.save(setting);
        } catch (Exception e) {
            log.error("[ApiKeyStore] Failed to persist setting '{}': {}", key, e.getMessage());
        }
    }

    private void persistPlain(String key, String value) {
        try {
            AppSetting setting = settingRepo.findById(key).orElse(new AppSetting(key, ""));
            setting.setSettingValue(value);
            settingRepo.save(setting);
        } catch (Exception e) {
            log.error("[ApiKeyStore] Failed to persist setting '{}': {}", key, e.getMessage());
        }
    }
}
