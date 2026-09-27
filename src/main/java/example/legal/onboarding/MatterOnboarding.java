package example.legal.onboarding;

import java.time.LocalDate;
import java.util.Objects;

public final class MatterOnboarding {
    public enum Channel { EMAIL, SMS }

    public record MatterIntake(
            String matterId,
            String clientName,
            String email,
            String phone,
            Channel signupChannel,
            String signedDocumentLink,
            LocalDate responseDeadline) {
        public MatterIntake {
            Objects.requireNonNull(matterId);
            Objects.requireNonNull(clientName);
            Objects.requireNonNull(email);
            Objects.requireNonNull(phone);
            Objects.requireNonNull(signupChannel);
            Objects.requireNonNull(signedDocumentLink);
            Objects.requireNonNull(responseDeadline);
        }
    }

    public record DeliveryReceipt(
            String matterId,
            String userId,
            Channel deliveredBy,
            String messageId,
            LocalDate responseDeadline) {}

    public interface Gateway {
        String createClient(MatterIntake intake);
        boolean isEmailSuppressed(String email);
        boolean isSmsSuppressed(String phone);
        String sendEmail(MatterIntake intake);
        String sendSms(MatterIntake intake);
    }

    private final Gateway gateway;

    public MatterOnboarding(Gateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    public DeliveryReceipt onboard(MatterIntake intake) {
        String userId = gateway.createClient(intake);
        Channel first = intake.signupChannel();
        Channel second = first == Channel.EMAIL ? Channel.SMS : Channel.EMAIL;

        Channel selected = available(first, intake) ? first
                : available(second, intake) ? second
                : null;
        if (selected == null) {
            throw new NoDeliveryChannelException(intake.matterId());
        }

        String messageId = selected == Channel.EMAIL
                ? gateway.sendEmail(intake)
                : gateway.sendSms(intake);
        return new DeliveryReceipt(
                intake.matterId(), userId, selected, messageId, intake.responseDeadline());
    }

    private boolean available(Channel channel, MatterIntake intake) {
        return channel == Channel.EMAIL
                ? !gateway.isEmailSuppressed(intake.email())
                : !gateway.isSmsSuppressed(intake.phone());
    }

    public static final class NoDeliveryChannelException extends RuntimeException {
        public NoDeliveryChannelException(String matterId) {
            super("No permitted delivery channel for matter " + matterId);
        }
    }
}
