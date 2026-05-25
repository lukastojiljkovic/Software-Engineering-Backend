package rs.raf.banka2_bek.interbank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.model.InterbankTransactionStatus;
import rs.raf.banka2_bek.interbank.protocol.Transaction;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterbankPaymentAsyncService {

    private final TransactionExecutorService transactionExecutorService;
    private final InterbankTransactionRepository interbankTransactionRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Runs the full 2PC flow for an interbank payment on a dedicated thread pool.
     * The calling thread (HTTP request) has already committed the Payment record
     * with status=PROCESSING before this method is invoked.
     *
     * After execute() returns, reads the final InterbankTransaction status and
     * mirrors it back to the Payment row: COMMITTED → COMPLETED, anything else → REJECTED.
     */
    @Async("interbankTaskExecutor")
    @Transactional(propagation = Propagation.NEVER)
    public void executeAsync(Long paymentId, Transaction tx) {
        // BE-INT-04: idempotency guard.
        // Spring @Async ne garantuje at-most-once preko app restart-a; ako se
        // posle restart-a executor task queue rehydrate-uje, re-invoked async
        // job moze flipnuti vec-terminal Payment (npr. COMPLETED → REJECTED).
        // Guard: ako Payment vise nije u PROCESSING stanju, ovo je replay i
        // izlazimo bez modifikacije stanja.
        Payment current = paymentRepository.findById(paymentId).orElse(null);
        if (current == null) {
            log.warn("Skipping async settlement: payment {} not found (replay or deleted)", paymentId);
            return;
        }
        if (current.getStatus() != PaymentStatus.PROCESSING) {
            log.debug("Skipping async settlement for payment {} — already in terminal state {}",
                    paymentId, current.getStatus());
            return;
        }

        try {
            transactionExecutorService.execute(tx);
        } catch (Exception e) {
            log.error("Interbank 2PC execute failed for payment {}: {}", paymentId, e.getMessage(), e);
        }

        interbankTransactionRepository
                .findByTransactionRoutingNumberAndTransactionIdString(
                        tx.transactionId().routingNumber(), tx.transactionId().id())
                .ifPresentOrElse(ibTx -> {
                    PaymentStatus finalStatus = ibTx.getStatus() == InterbankTransactionStatus.COMMITTED
                            ? PaymentStatus.COMPLETED
                            : PaymentStatus.REJECTED;
                    paymentRepository.findById(paymentId).ifPresent(p -> {
                        p.setStatus(finalStatus);
                        paymentRepository.save(p);
                    });
                }, () -> {
                    log.warn("InterbankTransaction not found after execute() for payment {}", paymentId);
                    paymentRepository.findById(paymentId).ifPresent(p -> {
                        p.setStatus(PaymentStatus.REJECTED);
                        paymentRepository.save(p);
                    });
                });
    }
}
