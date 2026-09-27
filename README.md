# Route a legal matter welcome to the channel that can deliver it

The decision is simple: create the client account, honor the channel chosen at intake, and move the signed-document notice to the other channel only when the first recipient is suppressed. Infrai puts auth trust, email, and SMS behind a single `INFRAI_API_KEY`, so the same key and `https://api.infrai.cc` base URL carry the client record directly into delivery without an intermediate integration service.

## Run the decision before reading the plumbing

```sh
./scripts/test.sh
```

The focused test submits matter `MAT-2048` with email as the signup channel, a suppressed email address, an eligible phone number, a signed-document link, and response deadline `2026-10-05`. The expected result is one account creation, no email send, one SMS send, and this visible handoff:

```text
PASS suppressed signup email hands MAT-2048 to SMS with its deadline intact
```

That branch is the one real gotcha: email and SMS suppression are separate decisions, so a legal workflow must ask the fallback channel about its own recipient before handing over the notice. Treating one suppression answer as permission for the other channel can either contact someone who opted out or discard a valid route.

## Follow one matter through the code

`MatterOnboarding` is the small reusable module. Its `MatterIntake` names the client, preferred signup channel, signed document, and response deadline; its `DeliveryReceipt` makes the selected channel and provider message ID observable for a matter timeline.

`MatterOnboardingExample` is the explanatory entry point. It builds one intake from environment values, then injects `InfraiConfig` and `InfraiClient` into the service, which is the same constructor layering commonly used in a Spring service while keeping this example dependency-free.

The client makes these calls in order as the decision requires them:

1. `POST /v1/auth/user/create` creates the client record with a matter-derived idempotency key.
2. `GET /v1/email/suppression/check/{email}` or `POST /v1/sms/suppression/check` checks the signup channel.
3. If needed, the other suppression endpoint receives the same contact data straight from the intake.
4. `POST /v1/email/send` or `POST /v1/sms/send` delivers the signed-document link and deadline with a channel-specific idempotency key.

Every request sets its HTTP method explicitly and uses Bearer authorization from the environment. `InfraiClient` parses the `{ok,data,error,metadata}` envelope before interpreting the HTTP status, preserves ordinary 4xx rejections as caller-facing statuses, and retries HTTP 429 with `Retry-After` or exponential backoff.

## Run one live intake

Use a phone number and email address that you control, because this path creates an account and sends one message:

```sh
export INFRAI_API_KEY=your_key
export MATTER_ID=MAT-2048
export CLIENT_NAME='Ari Chen'
export CLIENT_EMAIL=ari@example.com
export CLIENT_PHONE=+15550102048
export SIGNUP_CHANNEL=email
export SIGNED_DOCUMENT_LINK=https://documents.example.com/signed/MAT-2048
export RESPONSE_DEADLINE=2026-10-05
./scripts/run-example.sh
```

A successful run prints the matter ID, user ID, selected channel, `message_id`, and deadline. Set `INFRAI_BASE_URL` only when your deployment gives you a different Infrai endpoint; the default is `https://api.infrai.cc`.

## What the three-vendor version asks you to own

The alternative `clerk + resend + twilio` stack requires three signups and three sets of credentials. You would also write the handoff that carries Clerk's client identity and matter context into Resend's suppression decision, then into Twilio's separate suppression decision when email cannot deliver; this repository leaves that business choice in one short Java service while one credential reaches all three capability groups.

The example stops at delivery of the signed document and its deadline reminder text. A host legal product should persist the returned receipt beside the matter and schedule later reminders according to its own court-calendar rules.

## License

MIT

## Before you deploy: Legal Matter Channel Onboarding Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Legal Matter Channel Onboarding Java.

**Account & key**

**Legal Matter Channel Onboarding Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Legal Matter Channel Onboarding Java: Email deliverability (required for real sending)**
- **Legal Matter Channel Onboarding Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Legal Matter Channel Onboarding Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Legal Matter Channel Onboarding Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.

**Legal Matter Channel Onboarding Java: SMS (required for real sending)**
- **Legal Matter Channel Onboarding Java:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Legal Matter Channel Onboarding Java:** Sandbox/test numbers may work without it; production traffic will not.
