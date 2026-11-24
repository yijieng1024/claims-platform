package com.example.claims.claims.claim;

import static com.example.claims.claims.claim.ClaimStatus.ASSESSED;
import static com.example.claims.claims.claim.ClaimStatus.INFO_REQUESTED;
import static com.example.claims.claims.claim.ClaimStatus.REJECTED;
import static com.example.claims.claims.claim.ClaimStatus.SETTLED;
import static com.example.claims.claims.claim.ClaimStatus.SUBMITTED;
import static com.example.claims.claims.claim.ClaimStatus.UNDER_REVIEW;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Every allowed status change after submission (design section 5) lives here and nowhere else. A new
 * claim always starts as {@link ClaimStatus#SUBMITTED}.
 */
public final class ClaimStateMachine {

    public enum Action { ASSIGN, REQUEST_INFO, PROVIDE_INFO, ASSESS, SETTLE, REJECT }

    private record Key(ClaimStatus from, Action action) {
    }

    static final Map<Key, ClaimStatus> TRANSITIONS = Map.of(
            new Key(SUBMITTED, Action.ASSIGN), UNDER_REVIEW,
            new Key(UNDER_REVIEW, Action.REQUEST_INFO), INFO_REQUESTED,
            new Key(ASSESSED, Action.REQUEST_INFO), INFO_REQUESTED,      // Q7: more info after assessing
            new Key(INFO_REQUESTED, Action.PROVIDE_INFO), UNDER_REVIEW,
            new Key(UNDER_REVIEW, Action.ASSESS), ASSESSED,
            new Key(ASSESSED, Action.ASSESS), ASSESSED,                  // reserve update, status unchanged
            new Key(ASSESSED, Action.SETTLE), SETTLED,
            new Key(UNDER_REVIEW, Action.REJECT), REJECTED,
            new Key(ASSESSED, Action.REJECT), REJECTED);

    private ClaimStateMachine() {
    }

    /** The status after {@code action}, or a 409 naming the current status and the action. */
    public static ClaimStatus next(ClaimStatus from, Action action) {
        ClaimStatus to = TRANSITIONS.get(new Key(from, action));
        if (to == null) {
            throw new InvalidTransitionException(from, action);
        }
        return to;
    }

    public static boolean isAllowed(ClaimStatus from, Action action) {
        return TRANSITIONS.containsKey(new Key(from, action));
    }

    public static class InvalidTransitionException extends ErrorResponseException {

        InvalidTransitionException(ClaimStatus from, Action action) {
            super(HttpStatus.CONFLICT, problem(from, action), null);
        }

        private static ProblemDetail problem(ClaimStatus from, Action action) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "Cannot %s a claim in status %s".formatted(action, from));
            problem.setProperty("currentStatus", from);
            problem.setProperty("action", action);
            return problem;
        }
    }
}
