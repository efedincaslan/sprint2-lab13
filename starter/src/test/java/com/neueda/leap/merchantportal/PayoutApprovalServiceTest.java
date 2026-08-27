package com.neueda.leap.merchantportal;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PayoutApprovalServiceTest {

    @Test
    void requesterCannotApproveOwnPayout() {
        PayoutRepository repository = mock(PayoutRepository.class);

        Long payoutId = 1L;
        Long requestingUserId = 100L;

        PayoutRequest payout = new PayoutRequest(
                payoutId,
                10L,
                requestingUserId,
                500.00
        );

        when(repository.findById(payoutId))
                .thenReturn(Optional.of(payout));

        PayoutApprovalService service =
                new PayoutApprovalService(repository);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.approve(payoutId, requestingUserId)
        );

        assertEquals(
                "The user who requested the payout cannot approve it",
                exception.getMessage()
        );

        // Most importantly, the payout must not be saved.
        verify(repository, never()).save(any(PayoutRequest.class));

        // And its status should remain unchanged.
        assertEquals("PENDING", payout.getApprovalStatus());
    }

    @Test
    void differentUserCanApprovePayout() {
        PayoutRepository repository = mock(PayoutRepository.class);

        Long payoutId = 1L;
        Long requestingUserId = 100L;
        Long approvingUserId = 200L;

        PayoutRequest payout = new PayoutRequest(
                payoutId,
                10L,
                requestingUserId,
                500.00
        );

        when(repository.findById(payoutId))
                .thenReturn(Optional.of(payout));

        PayoutApprovalService service =
                new PayoutApprovalService(repository);

        service.approve(payoutId, approvingUserId);

        assertEquals("APPROVED", payout.getApprovalStatus());
        assertEquals(approvingUserId, payout.getApprovedByUserId());

        verify(repository).save(payout);
    }
}
