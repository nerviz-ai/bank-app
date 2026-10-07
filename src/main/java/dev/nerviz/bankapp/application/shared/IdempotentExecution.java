package dev.nerviz.bankapp.application.shared;

import dev.nerviz.bankapp.application.port.IdempotencyClaim;
import dev.nerviz.bankapp.application.port.IdempotencyKeyPort;
import dev.nerviz.bankapp.application.port.IdempotencyRequest;
import dev.nerviz.bankapp.application.port.StoredResponse;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The two-transaction shape of {@code Idempotency-Key} ({@.claude/rules/api-rest.md} §
 * Idempotency). Transaction 1, the claim, commits on its own. Transaction 2 commits the
 * effect and the stored response together; when it rolls back, the key is released so a
 * retry runs again instead of waiting out the lease.
 */
@Service
public class IdempotentExecution {

    private final IdempotencyKeyPort idempotencyKeys;
    private final TransactionOperations transactions;

    public IdempotentExecution(IdempotencyKeyPort idempotencyKeys, TransactionOperations transactions) {
        this.idempotencyKeys = idempotencyKeys;
        this.transactions = transactions;
    }

    public <R> IdempotentOutcome<R> execute(
            IdempotencyRequest request, Supplier<R> effect, Function<R, StoredResponse> recorder) {
        if (idempotencyKeys.claim(request) instanceof IdempotencyClaim.Replay(StoredResponse response)) {
            return new IdempotentOutcome.Replayed<>(response);
        }
        boolean committed = false;
        try {
            R result = transactions.execute(status -> {
                R produced = effect.get();
                idempotencyKeys.complete(request.key(), recorder.apply(produced));
                return produced;
            });
            committed = true;
            return new IdempotentOutcome.Executed<>(result);
        } finally {
            if (!committed) {
                idempotencyKeys.release(request.key());
            }
        }
    }
}
