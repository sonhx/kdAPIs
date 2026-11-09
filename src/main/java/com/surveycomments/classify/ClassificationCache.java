package com.surveycomments.classify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache storing classification results indexed by:
 * {@code SHA-256(comment_text + "|" + codebook_version + "|" + prompt_version + "|" + model)}.
 *
 * <p>Avoids redundant Gemini API calls across recurring surveys or duplicated clean comments.
 */
@Component
public class ClassificationCache {

    private static final Logger log = LoggerFactory.getLogger(ClassificationCache.class);

    private final Map<String, ClassificationResult> cache = new ConcurrentHashMap<>();

    public Optional<ClassificationResult> get(String commentText, String codebookVersion,
                                             String promptVersion, String model) {
        String key = computeKey(commentText, codebookVersion, promptVersion, model);
        ClassificationResult cached = cache.get(key);
        if (cached != null) {
            log.debug("[survey-comments] Cache hit for key={}", key.substring(0, 8));
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    public void put(String commentText, String codebookVersion,
                    String promptVersion, String model, ClassificationResult result) {
        String key = computeKey(commentText, codebookVersion, promptVersion, model);
        cache.put(key, result);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private String computeKey(String commentText, String codebookVersion,
                              String promptVersion, String model) {
        String raw = (commentText != null ? commentText.trim() : "") + "|"
                + (codebookVersion != null ? codebookVersion : "") + "|"
                + (promptVersion != null ? promptVersion : "") + "|"
                + (model != null ? model : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

