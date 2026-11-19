package com.giki.mess.payment.gateway;

import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.GatewayTimeoutException;
import com.giki.mess.payment.exception.InvalidCallbackException;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentReceipt;
import com.giki.mess.payment.model.PaymentRequest;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.util.LogRedactor;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Behaviour shared by the simulated gateways (Template Method style: subclasses only add
 * their channel and, if needed, extra checks).
 *
 * <p>SECURITY (QA-05): every callback carries an HMAC-SHA256 signature over its important
 * fields. A forged callback, or one whose amount was changed, cannot produce a matching
 * signature without the secret. Comparison is constant-time. Callbacks older than
 * 15 minutes (or dated in the future) are rejected so an old message cannot be replayed.
 */
public abstract class AbstractSignedProcessor implements IPaymentProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSignedProcessor.class);
    private static final String HMAC = "HmacSHA256";
    static final Duration MAX_CALLBACK_AGE = Duration.ofMinutes(15);
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(1);

    private final PaymentChannel channel;
    private final byte[] secret;
    private final Clock clock;
    private volatile boolean simulatedOutage;

    protected AbstractSignedProcessor(PaymentChannel channel, String secret, Clock clock) {
        if (channel == null || secret == null || secret.isBlank() || clock == null) {
            throw new IllegalArgumentException("channel, secret and clock are required");
        }
        this.channel = channel;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** Computes the signature the real gateway would attach (also used by tests as the gateway). */
    public String sign(String transactionId, String studentId, String billId, Money amount,
                       CallbackStatus status, String gatewayReference, Instant issuedAt) {
        // Every field is written as "<length>:<value>", so where one field ends and the next
        // begins is part of the signed data and text cannot be shifted between fields.
        StringBuilder payload = new StringBuilder();
        for (String field : new String[] {transactionId, studentId, billId,
            Long.toString(amount.paisa()), status.name(), gatewayReference,
            Long.toString(issuedAt.toEpochMilli())}) {
            payload.append(field.length()).append(':').append(field).append(';');
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /** Fault injection: while true the gateway behaves as if it timed out. */
    public void setSimulatedOutage(boolean outage) {
        this.simulatedOutage = outage;
    }

    @Override
    public PaymentResponse initiatePayment(PaymentRequest request) {
        if (simulatedOutage) {
            LOG.warn("Gateway {} timed out for transaction {}", channel, request.transactionId());
            throw new GatewayTimeoutException(channel.name());
        }
        String reference = channel.name().substring(0, 2) + "-" + request.transactionId();
        LOG.info("Payment initiated: channel={} tx={} student={}", channel,
            request.transactionId(), LogRedactor.maskStudentId(request.studentId()));
        return new PaymentResponse(request.transactionId(), channel, reference);
    }

    @Override
    public boolean verifyCallback(PaymentCallback callback) {
        if (callback == null) {
            return false;
        }
        String expected = sign(callback.transactionId(), callback.studentId(), callback.billId(),
            callback.amount(), callback.status(), callback.gatewayReference(), callback.issuedAt());
        boolean signatureOk = MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            callback.signature().getBytes(StandardCharsets.UTF_8));
        boolean fresh = isFresh(callback.issuedAt());
        boolean extraOk = extraChecks(callback);
        if (!(signatureOk && fresh && extraOk)) {
            LOG.warn("Callback rejected: channel={} tx={} student={} signatureOk={} fresh={} extraOk={}",
                channel, callback.transactionId(), LogRedactor.maskStudentId(callback.studentId()),
                signatureOk, fresh, extraOk);
        }
        return signatureOk && fresh && extraOk;
    }

    @Override
    public PaymentReceipt reconcile(PaymentCallback callback) {
        if (!verifyCallback(callback)) {
            throw new InvalidCallbackException(
                "Callback rejected: invalid signature, expired, or incomplete");
        }
        return new PaymentReceipt(callback.transactionId(), callback.amount(),
            callback.gatewayReference(), clock.instant());
    }

    @Override
    public PaymentChannel getChannelType() {
        return channel;
    }

    /** Hook for channel-specific rules; the default accepts. */
    protected boolean extraChecks(PaymentCallback callback) {
        return true;
    }

    private boolean isFresh(Instant issuedAt) {
        Duration age = Duration.between(issuedAt, clock.instant());
        return age.compareTo(MAX_CALLBACK_AGE) <= 0 && age.compareTo(MAX_CLOCK_SKEW.negated()) >= 0;
    }
}
