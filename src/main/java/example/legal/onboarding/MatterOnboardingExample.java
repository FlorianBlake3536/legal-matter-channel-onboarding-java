package example.legal.onboarding;

import java.time.LocalDate;

public final class MatterOnboardingExample {
    private MatterOnboardingExample() {}

    public static void main(String[] args) {
        MatterOnboarding.MatterIntake intake = new MatterOnboarding.MatterIntake(
                required("MATTER_ID"),
                required("CLIENT_NAME"),
                required("CLIENT_EMAIL"),
                required("CLIENT_PHONE"),
                MatterOnboarding.Channel.valueOf(required("SIGNUP_CHANNEL").toUpperCase()),
                required("SIGNED_DOCUMENT_LINK"),
                LocalDate.parse(required("RESPONSE_DEADLINE")));

        MatterOnboarding service = new MatterOnboarding(new InfraiClient(InfraiConfig.fromEnvironment()));
        try {
            MatterOnboarding.DeliveryReceipt receipt = service.onboard(intake);
            System.out.printf(
                    "matter=%s user=%s delivered_by=%s message_id=%s response_deadline=%s%n",
                    receipt.matterId(), receipt.userId(), receipt.deliveredBy(),
                    receipt.messageId(), receipt.responseDeadline());
        } catch (InfraiClient.InfraiException rejected) {
            System.err.printf("request_status=%d code=%s message=%s%n",
                    rejected.clientStatus(), rejected.code(), rejected.getMessage());
            System.exit(2);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name);
        return value;
    }
}
