package example.legal.onboarding;

import java.time.LocalDate;

public final class MatterOnboardingTest {
    public static void main(String[] args) {
        MatterOnboarding.MatterIntake intake = new MatterOnboarding.MatterIntake(
                "MAT-2048", "Ari Chen", "ari@example.test", "+15550102048",
                MatterOnboarding.Channel.EMAIL, "https://documents.example.test/signed/MAT-2048",
                LocalDate.of(2026, 10, 5));
        RecordingGateway gateway = new RecordingGateway();

        MatterOnboarding.DeliveryReceipt receipt = new MatterOnboarding(gateway).onboard(intake);

        check(gateway.created == 1, "client account is created once");
        check(gateway.emailChecks == 1, "preferred email is checked once");
        check(gateway.smsChecks == 1, "fallback SMS is checked once");
        check(gateway.emailSends == 0, "suppressed email is not sent");
        check(gateway.smsSends == 1, "SMS fallback is sent once");
        check(receipt.deliveredBy() == MatterOnboarding.Channel.SMS, "receipt exposes SMS handoff");
        check(receipt.responseDeadline().equals(LocalDate.of(2026, 10, 5)), "deadline remains observable");
        System.out.println("PASS suppressed signup email hands MAT-2048 to SMS with its deadline intact");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGateway implements MatterOnboarding.Gateway {
        int created;
        int emailChecks;
        int smsChecks;
        int emailSends;
        int smsSends;

        @Override public String createClient(MatterOnboarding.MatterIntake intake) {
            created++;
            return "user-mat-2048";
        }
        @Override public boolean isEmailSuppressed(String email) { emailChecks++; return true; }
        @Override public boolean isSmsSuppressed(String phone) { smsChecks++; return false; }
        @Override public String sendEmail(MatterOnboarding.MatterIntake intake) {
            emailSends++;
            return "email-unused";
        }
        @Override public String sendSms(MatterOnboarding.MatterIntake intake) {
            smsSends++;
            return "sms-mat-2048";
        }
    }
}
