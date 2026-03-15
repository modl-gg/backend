package gg.modl.backend.billing.config;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "modl.stripe")
@Validated
@Getter
@Setter
@Slf4j
public class StripeConfiguration {
    private String secretKey = "";
    private String webhookSecret = "";
    private String priceId = "";

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
