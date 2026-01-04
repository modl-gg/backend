package gg.modl.backend.billing.config;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StripeConfiguration {
    @Value("${modl.stripe.secret-key:}")
    private String secretKey;

    @Getter
    @Value("${modl.stripe.webhook-secret:}")
    private String webhookSecret;

    @Getter
    @Value("${modl.stripe.price-id:}")
    private String priceId;

    @Getter
    @Value("${modl.domain}")
    private String domain;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
            log.info("Stripe API initialized");
        } else {
            log.warn("STRIPE_SECRET_KEY not found. Billing features will be disabled.");
        }
    }

    @Bean
    public StripeClient stripeClient() {
        if (secretKey == null || secretKey.isBlank()) {
            return null;
        }
        return new StripeClient(secretKey);
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
