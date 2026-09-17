package com.payflow.ledger.categorizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.ledger.event.TransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests the rule-based fallback. Constructing the categorizer with a blank API key
 * means no Gemini client is built, so {@code categorize} always takes the fallback
 * path — no network, deterministic output.
 */
class TransactionCategorizerTest {

    private final TransactionCategorizer categorizer =
            new TransactionCategorizer("", "gemini-flash-lite-latest");

    private TransactionEvent event(String amount, String description, String merchant) {
        return new TransactionEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(amount), "INR", description, merchant, Instant.now());
    }

    @Test
    void fallbackMatchesGroceriesKeyword() {
        assertThat(categorizer.categorize(event("3400", "Grocery purchase", "Reliance Fresh")))
                .isEqualTo("groceries");
    }

    @Test
    void fallbackMatchesFoodKeyword() {
        assertThat(categorizer.categorize(event("780", "Food delivery", "Swiggy")))
                .isEqualTo("food");
    }

    @Test
    void fallbackUsesAmountBandWhenNoKeywordMatches() {
        // Text present but no keyword hit -> falls through to amount bands (>=50000 = salary).
        assertThat(categorizer.categorize(event("60000", "n/a", "Unknown Corp")))
                .isEqualTo("salary");
    }

    @Test
    void classifiesAsOtherWithoutMerchantOrDescription() {
        // No merchant and no description -> "other" without any LLM/fallback banding.
        assertThat(categorizer.categorize(event("999", null, null)))
                .isEqualTo("other");
    }
}
