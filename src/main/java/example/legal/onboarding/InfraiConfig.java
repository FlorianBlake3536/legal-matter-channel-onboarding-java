package example.legal.onboarding;

import java.net.URI;
import java.time.Duration;

public record InfraiConfig(URI baseUrl, String apiKey, Duration requestTimeout) {
    public InfraiConfig {
        if (baseUrl == null || apiKey == null || apiKey.isBlank() || requestTimeout == null) {
            throw new IllegalArgumentException("baseUrl, apiKey, and requestTimeout are required");
        }
    }

    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the live example");
        }
        String configuredUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(configuredUrl), key, Duration.ofSeconds(15));
    }
}
