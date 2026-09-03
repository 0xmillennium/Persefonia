package dev.persefonia.platformoperations.domain.cache;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CacheInvalidationBatch {
    private static final int MAX_ATTEMPTS = 3;

    private final CacheInvalidationBatchId id;
    private final InvalidationReason reason;
    private final InvalidationRequester requestedBy;
    private final Instant requestedAt;
    private CacheInvalidationStatus status;
    private final List<CacheInvalidationTarget> targets;
    private final List<CachePurgeAttempt> attempts;
    private Instant completedAt;
    private CachePurgeFailureReason failureReason;
    private long version;

    private CacheInvalidationBatch(
            CacheInvalidationBatchId id,
            InvalidationReason reason,
            InvalidationRequester requestedBy,
            Instant requestedAt,
            CacheInvalidationStatus status,
            List<CacheInvalidationTarget> targets,
            List<CachePurgeAttempt> attempts,
            Instant completedAt,
            CachePurgeFailureReason failureReason,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.targets = new ArrayList<>(Objects.requireNonNull(targets, "targets"));
        this.attempts = new ArrayList<>(Objects.requireNonNull(attempts, "attempts"));
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.version = version;
        validateInvariants();
    }

    public static CacheInvalidationBatch request(
            CacheInvalidationBatchId id,
            InvalidationReason reason,
            InvalidationRequester requestedBy,
            Instant requestedAt,
            List<CacheInvalidationTarget> targets) {
        Objects.requireNonNull(targets, "targets");
        Map<TargetKey, CacheInvalidationTarget> unique = new LinkedHashMap<>();
        for (CacheInvalidationTarget target : targets) {
            Objects.requireNonNull(target, "target");
            if (target.status() != CacheTargetStatus.PENDING) {
                throw invalid("new cache invalidation targets must be pending");
            }
            unique.putIfAbsent(new TargetKey(target.targetType(), target.value().value()), target);
        }
        return new CacheInvalidationBatch(id, reason, requestedBy, requestedAt,
                CacheInvalidationStatus.REQUESTED, List.copyOf(unique.values()), List.of(), null, null, 0);
    }

    public static CacheInvalidationBatch rehydrate(
            CacheInvalidationBatchId id,
            InvalidationReason reason,
            InvalidationRequester requestedBy,
            Instant requestedAt,
            CacheInvalidationStatus status,
            List<CacheInvalidationTarget> targets,
            List<CachePurgeAttempt> attempts,
            Instant completedAt,
            CachePurgeFailureReason failureReason,
            long version) {
        return new CacheInvalidationBatch(id, reason, requestedBy, requestedAt, status, targets, attempts,
                completedAt, failureReason, version);
    }

    public void beginInitialAttempt() {
        if (status != CacheInvalidationStatus.REQUESTED || !attempts.isEmpty()
                || targets.stream().anyMatch(target -> target.status() != CacheTargetStatus.PENDING)) {
            throw invalid("initial attempt can begin only from a pristine requested batch");
        }
        status = CacheInvalidationStatus.RUNNING;
        version++;
        validateInvariants();
    }

    public void beginManualRetry() {
        if ((status != CacheInvalidationStatus.FAILED && status != CacheInvalidationStatus.PARTIAL)
                || attempts.size() >= MAX_ATTEMPTS || completedAt != null) {
            throw invalid("manual retry is not available for this batch");
        }
        for (CacheInvalidationTarget target : targets) {
            if (target.status() == CacheTargetStatus.FAILED) {
                target.changeStatus(CacheTargetStatus.PENDING);
            }
        }
        status = CacheInvalidationStatus.RUNNING;
        failureReason = null;
        completedAt = null;
        version++;
        validateInvariants();
    }

    public void recordAttemptResult(
            int attemptNumber,
            CachePurgeProvider provider,
            Instant attemptedAt,
            CachePurgeResult result,
            CachePurgeFailureReason failureReason,
            List<CacheTargetOutcome> outcomes,
            Instant recordedAt) {
        if (status != CacheInvalidationStatus.RUNNING) {
            throw invalid("attempt result can be recorded only for a running batch");
        }
        if (attemptNumber != attempts.size() + 1 || attemptNumber > MAX_ATTEMPTS) {
            throw invalid("attempt number must be the next contiguous number within the attempt budget");
        }
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (attemptedAt.isBefore(requestedAt)
                || !attempts.isEmpty() && attemptedAt.isBefore(attempts.getLast().attemptedAt())
                || recordedAt.isBefore(attemptedAt)) {
            throw invalid("attempt timestamps violate cache invalidation temporal ordering");
        }

        Map<CacheInvalidationTargetId, CacheInvalidationTarget> pending = new HashMap<>();
        for (CacheInvalidationTarget target : targets) {
            if (target.status() == CacheTargetStatus.PENDING) {
                pending.put(target.id(), target);
            }
        }
        Map<CacheInvalidationTargetId, CacheTargetStatus> outcomeByTarget = new HashMap<>();
        for (CacheTargetOutcome outcome : outcomes) {
            Objects.requireNonNull(outcome, "outcome");
            if (!pending.containsKey(outcome.targetId()) || outcomeByTarget.put(outcome.targetId(), outcome.status()) != null) {
                throw invalid("attempt outcomes must cover each pending target exactly once");
            }
        }
        if (outcomeByTarget.size() != pending.size()) {
            throw invalid("attempt outcomes must cover each pending target exactly once");
        }
        boolean hasFailedOutcome = outcomeByTarget.containsValue(CacheTargetStatus.FAILED);
        if (result == CachePurgeResult.SUCCESS && (failureReason != null || hasFailedOutcome)) {
            throw invalid("successful attempt cannot contain failure state");
        }
        if (result == CachePurgeResult.FAILED && (failureReason == null || !hasFailedOutcome)) {
            throw invalid("failed attempt requires a safe failure reason and a failed target");
        }

        CachePurgeAttempt attempt = new CachePurgeAttempt(CachePurgeAttemptId.newId(), attemptNumber,
                provider, attemptedAt, result, failureReason);
        for (Map.Entry<CacheInvalidationTargetId, CacheTargetStatus> outcome : outcomeByTarget.entrySet()) {
            pending.get(outcome.getKey()).changeStatus(outcome.getValue());
        }
        attempts.add(attempt);
        deriveTerminalState(failureReason, recordedAt);
        version++;
        validateInvariants();
    }

    private void deriveTerminalState(CachePurgeFailureReason attemptFailureReason, Instant recordedAt) {
        boolean satisfied = targets.stream().anyMatch(target -> isSatisfied(target.status()));
        boolean failed = targets.stream().anyMatch(target -> target.status() == CacheTargetStatus.FAILED);
        if (!failed) {
            status = CacheInvalidationStatus.COMPLETED;
            failureReason = null;
            completedAt = recordedAt;
            return;
        }
        status = satisfied ? CacheInvalidationStatus.PARTIAL : CacheInvalidationStatus.FAILED;
        failureReason = attemptFailureReason;
        completedAt = attempts.size() == MAX_ATTEMPTS ? recordedAt : null;
    }

    private void validateInvariants() {
        if (version < 0 || targets.isEmpty()) {
            throw invalid("cache invalidation batch requires targets and a non-negative version");
        }
        Set<CacheInvalidationTargetId> targetIds = new HashSet<>();
        Set<TargetKey> targetKeys = new HashSet<>();
        for (CacheInvalidationTarget target : targets) {
            if (target == null || !targetIds.add(target.id())
                    || !targetKeys.add(new TargetKey(target.targetType(), target.value().value()))) {
                throw invalid("cache invalidation target identity and value must be unique within a batch");
            }
        }
        if (attempts.size() > MAX_ATTEMPTS) {
            throw invalid("cache invalidation attempt budget exceeded");
        }
        Instant previousAttemptedAt = requestedAt;
        Set<CachePurgeAttemptId> attemptIds = new HashSet<>();
        for (int index = 0; index < attempts.size(); index++) {
            CachePurgeAttempt attempt = Objects.requireNonNull(attempts.get(index), "attempt");
            if (!attemptIds.add(attempt.id())
                    || attempt.attemptNumber() != index + 1
                    || attempt.attemptedAt().isBefore(previousAttemptedAt)
                    || attempt.result() == CachePurgeResult.SUCCESS && index != attempts.size() - 1) {
                throw invalid("attempt history must be contiguous and time ordered");
            }
            previousAttemptedAt = attempt.attemptedAt();
        }
        if (completedAt != null && (completedAt.isBefore(requestedAt) || completedAt.isBefore(previousAttemptedAt))) {
            throw invalid("completion timestamp violates temporal ordering");
        }

        boolean pending = targets.stream().anyMatch(target -> target.status() == CacheTargetStatus.PENDING);
        boolean failed = targets.stream().anyMatch(target -> target.status() == CacheTargetStatus.FAILED);
        boolean satisfied = targets.stream().anyMatch(target -> isSatisfied(target.status()));
        switch (status) {
            case REQUESTED -> {
                require(attempts.isEmpty() && targets.stream().allMatch(t -> t.status() == CacheTargetStatus.PENDING)
                        && failureReason == null && completedAt == null && version == 0,
                        "invalid requested batch state");
            }
            case RUNNING -> require(pending && !failed && attempts.size() < MAX_ATTEMPTS
                            && attempts.stream().noneMatch(a -> a.result() == CachePurgeResult.SUCCESS)
                            && failureReason == null && completedAt == null && version == attempts.size() * 2L + 1,
                    "invalid running batch state");
            case COMPLETED -> require(!pending && !failed && !attempts.isEmpty()
                            && attempts.getLast().result() == CachePurgeResult.SUCCESS
                            && failureReason == null && completedAt != null && version == attempts.size() * 2L,
                    "invalid completed batch state");
            case FAILED -> require(!pending && failed && !satisfied && failureReason != null
                            && latestAttemptFailed() && completionMatchesBudget() && version == attempts.size() * 2L,
                    "invalid failed batch state");
            case PARTIAL -> require(!pending && failed && satisfied && failureReason != null
                            && latestAttemptFailed() && completionMatchesBudget() && version == attempts.size() * 2L,
                    "invalid partial batch state");
        }
        if ((status == CacheInvalidationStatus.FAILED || status == CacheInvalidationStatus.PARTIAL)
                && attempts.getLast().failureReason() != failureReason) {
            throw invalid("batch failure reason must match the latest failed attempt");
        }
    }

    private boolean latestAttemptFailed() {
        return !attempts.isEmpty() && attempts.getLast().result() == CachePurgeResult.FAILED;
    }

    private boolean completionMatchesBudget() {
        return attempts.size() == MAX_ATTEMPTS ? completedAt != null : completedAt == null;
    }

    private static boolean isSatisfied(CacheTargetStatus status) {
        return status == CacheTargetStatus.PURGED || status == CacheTargetStatus.SKIPPED;
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw invalid(message);
    }

    private static CacheInvalidationValidationException invalid(String message) {
        return new CacheInvalidationValidationException(message);
    }

    public CacheInvalidationBatchId id() { return id; }
    public InvalidationReason reason() { return reason; }
    public InvalidationRequester requestedBy() { return requestedBy; }
    public Instant requestedAt() { return requestedAt; }
    public CacheInvalidationStatus status() { return status; }
    public List<CacheInvalidationTarget> targets() { return List.copyOf(targets); }
    public List<CachePurgeAttempt> attempts() { return List.copyOf(attempts); }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public Optional<CachePurgeFailureReason> failureReason() { return Optional.ofNullable(failureReason); }
    public long version() { return version; }

    private record TargetKey(CacheTargetType type, String value) { }
}
