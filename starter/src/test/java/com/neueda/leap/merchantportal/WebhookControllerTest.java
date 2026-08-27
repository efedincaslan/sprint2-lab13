package com.neueda.leap.merchantportal;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class WebhookControllerTest {

    // must match PAYMENT_WEBHOOK_SECRET configured for the test JVM in pom.xml
    private static final String SHARED_SECRET = "test-webhook-secret";

    private static class FakePayoutStatusUpdater implements PayoutStatusUpdater {
        Long lastPayoutId;
        String lastStatus;
        int callCount;

        @Override
        public void markSettled(Long payoutId, String status) {
            lastPayoutId = payoutId;
            lastStatus = status;
            callCount++;
        }
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes()));
    }

    @Test
    void validSignatureAppliesPayoutUpdate() throws Exception {
        FakePayoutStatusUpdater updater = new FakePayoutStatusUpdater();
        WebhookController controller = new WebhookController(updater);
        String body = "{\"payoutId\":42,\"status\":\"SETTLED\"}";
        String signature = sign(body, SHARED_SECRET);

        controller.handlePaymentStatusWebhook(body, signature);

        assertEquals(1, updater.callCount);
        assertEquals(42L, updater.lastPayoutId);
        assertEquals("SETTLED", updater.lastStatus);
    }

    @Test
    void invalidSignatureIsRejected() {
        FakePayoutStatusUpdater updater = new FakePayoutStatusUpdater();
        WebhookController controller = new WebhookController(updater);
        String body = "{\"payoutId\":42,\"status\":\"SETTLED\"}";

        assertThrows(SecurityException.class,
                () -> controller.handlePaymentStatusWebhook(body, "not-a-real-signature"));
        assertEquals(0, updater.callCount);
    }

    @Test
    void tamperedBodyIsRejectedEvenWithSignatureFromOriginalBody() throws Exception {
        FakePayoutStatusUpdater updater = new FakePayoutStatusUpdater();
        WebhookController controller = new WebhookController(updater);
        String originalBody = "{\"payoutId\":42,\"status\":\"SETTLED\"}";
        String signature = sign(originalBody, SHARED_SECRET);
        String tamperedBody = "{\"payoutId\":9999,\"status\":\"SETTLED\"}";

        assertThrows(SecurityException.class,
                () -> controller.handlePaymentStatusWebhook(tamperedBody, signature));
        assertEquals(0, updater.callCount);
    }

    @Test
    void signatureComputedWithWrongSecretIsRejected() throws Exception {
        FakePayoutStatusUpdater updater = new FakePayoutStatusUpdater();
        WebhookController controller = new WebhookController(updater);
        String body = "{\"payoutId\":42,\"status\":\"SETTLED\"}";
        String signature = sign(body, "wrong-secret");

        assertThrows(SecurityException.class,
                () -> controller.handlePaymentStatusWebhook(body, signature));
        assertEquals(0, updater.callCount);
    }
}
