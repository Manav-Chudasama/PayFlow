package com.payflow.ledger.categorizer;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.payflow.ledger.event.TransactionEvent;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Labels a transaction with a category (e.g. "groceries", "salary", "bill") using
 * the Google Gemini API, with a rule-based fallback.
 *
 * <p>The AI lives on the <em>enrichment</em> path, not the money path: it enriches
 * an already-recorded ledger entry and must never break the ledger. So an LLM call
 * that fails — or a missing API key — degrades gracefully to the rule fallback.
 */
@Component
public class TransactionCategorizer {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategorizer.class);

    private static final String SYSTEM_INSTRUCTION =
            "You are a financial transaction classifier. Return exactly one category from this "
            + "list and nothing else: salary, groceries, rent, utilities, shopping, transfer, "
            + "entertainment, travel, healthcare, food, other. Respond with only the category word.";

    private final Client client;   // null when no API key is configured
    private final String model;

    public TransactionCategorizer(@Value("${llm.api-key}") String apiKey,
                                  @Value("${llm.model}") String model) {
        this.model = model;
        this.client = (apiKey == null || apiKey.isBlank())
                ? null
                : Client.builder().apiKey(apiKey).build();
    }

    public String categorize(TransactionEvent event) {
        boolean hasMerchant = event.merchant() != null && !event.merchant().isBlank();
        boolean hasDescription = event.description() != null && !event.description().isBlank();
        if (!hasMerchant && !hasDescription) {
            // Nothing to classify on — label "other" without spending an LLM call.
            return "other";
        }
        if (client == null) {
            // No key configured — skip straight to the fallback (still fully functional).
            return ruleFallback(event);
        }
        try {
            return llmCategorize(event);
        } catch (Exception e) {
            log.warn("Gemini categorization failed ({}); using rule fallback", e.toString());
            return ruleFallback(event);
        }
    }

    /** Single call to the Gemini API asking for one category word. */
    private String llmCategorize(TransactionEvent event) {
        String prompt = """
                Transaction:
                Merchant: %s
                Description: %s
                Amount: %s %s

                Return only the category.""".formatted(
                orNa(event.merchant()), orNa(event.description()), event.amount(), event.currency());

        GenerateContentConfig config = GenerateContentConfig.builder()
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel("MINIMAL").build())
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                .build();

        GenerateContentResponse response = client.models.generateContent(model, prompt, config);

        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("empty completion");
        }
        return normalize(text);
    }

    /** Reduce the model's reply to a single clean category token. */
    private String normalize(String text) {
        String word = text.strip().toLowerCase().split("\\s+")[0];
        return word.replaceAll("[^a-z]", "");
    }

    private static String orNa(String value) {
        return (value == null || value.isBlank()) ? "N/A" : value;
    }

    /**
     * Deterministic heuristic used whenever the LLM is unavailable. Prefers keyword
     * matching on the merchant/description; falls back to amount bands when there's
     * no text to match on.
     */
    private String ruleFallback(TransactionEvent event) {
        String text = (orNa(event.merchant()) + " " + orNa(event.description())).toLowerCase();
        if (text.contains("salary") || text.contains("payroll")) {
            return "salary";
        }
        if (text.contains("rent")) {
            return "rent";
        }
        if (text.contains("grocery") || text.contains("groceries") || text.contains("supermarket")) {
            return "groceries";
        }
        if (text.contains("food") || text.contains("restaurant") || text.contains("swiggy")
                || text.contains("zomato") || text.contains("dining")) {
            return "food";
        }
        if (text.contains("electric") || text.contains("water") || text.contains("gas")
                || text.contains("utility") || text.contains("bill")) {
            return "utilities";
        }
        if (text.contains("uber") || text.contains("flight") || text.contains("travel")
                || text.contains("hotel")) {
            return "travel";
        }
        if (text.contains("pharma") || text.contains("hospital") || text.contains("clinic")
                || text.contains("health")) {
            return "healthcare";
        }
        if (text.contains("movie") || text.contains("netflix") || text.contains("game")) {
            return "entertainment";
        }
        if (text.contains("shop") || text.contains("store") || text.contains("amazon")) {
            return "shopping";
        }

        // No useful text — fall back to amount bands.
        BigDecimal amount = event.amount();
        if (amount == null) {
            return "transfer";
        }
        if (amount.compareTo(new BigDecimal("50000")) >= 0) {
            return "salary";
        }
        if (amount.compareTo(new BigDecimal("10000")) >= 0) {
            return "rent";
        }
        if (amount.compareTo(new BigDecimal("2000")) >= 0) {
            return "utilities";
        }
        if (amount.compareTo(new BigDecimal("500")) >= 0) {
            return "shopping";
        }
        return "other";
    }

    @PreDestroy
    void closeClient() {
        if (client != null) {
            client.close();
        }
    }
}
