package example.legal.onboarding;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiClient implements MatterOnboarding.Gateway {
    private static final int MAX_ATTEMPTS = 4;
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
    }

    @Override
    public String createClient(MatterOnboarding.MatterIntake intake) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", intake.email());
        body.put("name", intake.clientName());
        body.put("idempotency_key", intake.matterId() + ":client");
        return requiredString(request("POST", "/v1/auth/user/create", body), "user_id");
    }

    @Override
    public boolean isEmailSuppressed(String email) {
        String segment = URLEncoder.encode(email, StandardCharsets.UTF_8).replace("+", "%20");
        return requiredBoolean(request("GET", "/v1/email/suppression/check/" + segment, null), "suppressed");
    }

    @Override
    public boolean isSmsSuppressed(String phone) {
        return requiredBoolean(request("POST", "/v1/sms/suppression/check", Map.of("phone", phone)), "suppressed");
    }

    @Override
    public String sendEmail(MatterOnboarding.MatterIntake intake) {
        String html = "<p>Hello " + escapeHtml(intake.clientName()) + ",</p>"
                + "<p>Your signed document for matter " + escapeHtml(intake.matterId())
                + " is ready: <a href=\"" + escapeHtml(intake.signedDocumentLink()) + "\">open document</a>.</p>"
                + "<p>Please respond by " + intake.responseDeadline() + ".</p>";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", intake.email());
        body.put("subject", "Signed document for matter " + intake.matterId());
        body.put("html", html);
        body.put("idempotency_key", intake.matterId() + ":signed-document:email");
        return requiredString(request("POST", "/v1/email/send", body), "message_id");
    }

    @Override
    public String sendSms(MatterOnboarding.MatterIntake intake) {
        String message = "Matter " + intake.matterId() + ": your signed document is ready at "
                + intake.signedDocumentLink() + ". Response deadline: " + intake.responseDeadline() + ".";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", intake.phone());
        body.put("body", message);
        body.put("idempotency_key", intake.matterId() + ":signed-document:sms");
        return requiredString(request("POST", "/v1/sms/send", body), "message_id");
    }

    private Map<String, Object> request(String method, String path, Map<String, Object> body) {
        String jsonBody = body == null ? null : Json.write(body);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (jsonBody == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            }

            HttpResponse<String> response = send(builder.build());
            Map<String, Object> envelope = Json.object(Json.parse(response.body()), "response envelope");
            boolean ok = Json.bool(envelope.get("ok"), "ok");

            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!ok) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?>
                        ? Json.object(envelope.get("error"), "error") : Map.of();
                throw new InfraiException(
                        String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                        String.valueOf(error.getOrDefault("message", "Request rejected")),
                        response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Infrai transport response " + response.statusCode());
            }
            return Json.object(envelope.get("data"), "data");
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private URI resolve(String path) {
        String base = config.baseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + path);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Could not complete Infrai request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Infrai request interrupted", e);
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            try {
                return Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
            } catch (DateTimeParseException ignoredDate) {
                return Duration.ofMillis(250L * (1L << attempt));
            }
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(Math.max(0, duration.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    private static String requiredString(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Response data omitted " + field);
        }
        return text;
    }

    private static boolean requiredBoolean(Map<String, Object> data, String field) {
        return Json.bool(data.get(field), field);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int clientStatus;

        InfraiException(String code, String message, int upstreamStatus) {
            super(message);
            this.code = code;
            this.clientStatus = upstreamStatus >= 400 && upstreamStatus < 500 ? upstreamStatus : 502;
        }

        public String code() { return code; }
        public int clientStatus() { return clientStatus; }
    }

    private static final class Json {
        private final String input;
        private int index;

        private Json(String input) { this.input = input; }

        static Object parse(String input) {
            Json parser = new Json(input);
            Object value = parser.value();
            parser.space();
            if (parser.index != input.length()) throw parser.error("Trailing content");
            return value;
        }

        static String write(Object value) {
            if (value == null) return "null";
            if (value instanceof String text) return quote(text);
            if (value instanceof Boolean || value instanceof Number) return value.toString();
            if (value instanceof Map<?, ?> map) {
                StringBuilder out = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    out.append(quote(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
                }
                return out.append('}').toString();
            }
            throw new IllegalArgumentException("Unsupported JSON value");
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(Object value, String name) {
            if (!(value instanceof Map<?, ?>)) throw new IllegalStateException(name + " must be an object");
            return (Map<String, Object>) value;
        }

        static boolean bool(Object value, String name) {
            if (!(value instanceof Boolean result)) throw new IllegalStateException(name + " must be boolean");
            return result;
        }

        private Object value() {
            space();
            if (index >= input.length()) throw error("Expected value");
            char c = input.charAt(index);
            if (c == '{') return objectValue();
            if (c == '"') return string();
            if (input.startsWith("true", index)) { index += 4; return true; }
            if (input.startsWith("false", index)) { index += 5; return false; }
            if (input.startsWith("null", index)) { index += 4; return null; }
            if (c == '-' || Character.isDigit(c)) return number();
            throw error("Unexpected value");
        }

        private Map<String, Object> objectValue() {
            index++;
            Map<String, Object> result = new LinkedHashMap<>();
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                if (!take(':')) throw error("Expected colon");
                result.put(key, value());
                space();
            } while (take(','));
            if (!take('}')) throw error("Expected object end");
            return result;
        }

        private String string() {
            if (!take('"')) throw error("Expected string");
            StringBuilder out = new StringBuilder();
            while (index < input.length()) {
                char c = input.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (index >= input.length()) throw error("Unfinished escape");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > input.length()) throw error("Short unicode escape");
                        out.append((char) Integer.parseInt(input.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw error("Unknown escape");
                }
            }
            throw error("Unclosed string");
        }

        private Number number() {
            int start = index;
            while (index < input.length() && "-+0123456789.eE".indexOf(input.charAt(index)) >= 0) index++;
            String token = input.substring(start, index);
            try { return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token); }
            catch (NumberFormatException e) { throw error("Invalid number"); }
        }

        private void space() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private boolean take(char expected) {
            if (index < input.length() && input.charAt(index) == expected) { index++; return true; }
            return false;
        }

        private IllegalStateException error(String message) {
            return new IllegalStateException(message + " at JSON offset " + index);
        }

        private static String quote(String value) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : value.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> out.append(c);
                }
            }
            return out.append('"').toString();
        }
    }
}
