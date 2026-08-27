package com.neueda.leap.merchantportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@RestController
public class WebhookController {

    private static final String WEBHOOK_SHARED_SECRET = System.getenv("PAYMENT_WEBHOOK_SECRET");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PayoutStatusUpdater payoutStatusUpdater;

    public WebhookController(PayoutStatusUpdater payoutStatusUpdater) {
        this.payoutStatusUpdater = payoutStatusUpdater;
    }

    // A request body can only be bound once, so we read it as a raw String,
    // verify the signature, then parse it ourselves only if it's valid.
    @PostMapping("/api/webhooks/payment-status")
    public void handlePaymentStatusWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Payment-Signature") String providedSignature) throws Exception {

        String expectedSignature = computeHmac(rawBody, WEBHOOK_SHARED_SECRET);

        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            throw new SecurityException("Invalid webhook signature");
        }

        PaymentStatusEvent event = OBJECT_MAPPER.readValue(rawBody, PaymentStatusEvent.class);
        payoutStatusUpdater.markSettled(event.getPayoutId(), event.getStatus());
    }

    private static String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();
        int result = aBytes.length ^ bBytes.length;
        
        for (int i = 0; i < Math.min(aBytes.length, bBytes.length); i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        
        return result == 0;
    }
}
