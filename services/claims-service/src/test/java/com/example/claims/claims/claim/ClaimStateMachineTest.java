package com.example.claims.claims.claim;

import static com.example.claims.claims.claim.ClaimStatus.ASSESSED;
import static com.example.claims.claims.claim.ClaimStatus.INFO_REQUESTED;
import static com.example.claims.claims.claim.ClaimStatus.REJECTED;
import static com.example.claims.claims.claim.ClaimStatus.SETTLED;
import static com.example.claims.claims.claim.ClaimStatus.SUBMITTED;
import static com.example.claims.claims.claim.ClaimStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.claims.claims.claim.ClaimStateMachine.Action;
import com.example.claims.claims.claim.ClaimStateMachine.InvalidTransitionException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class ClaimStateMachineTest {

    /** The expected table, written out independently of ClaimStateMachine (design section 5). */
    static Stream<Arguments> allowed() {
        return Stream.of(
                Arguments.of(SUBMITTED, Action.ASSIGN, UNDER_REVIEW),
                Arguments.of(UNDER_REVIEW, Action.REQUEST_INFO, INFO_REQUESTED),
                Arguments.of(ASSESSED, Action.REQUEST_INFO, INFO_REQUESTED),
                Arguments.of(INFO_REQUESTED, Action.PROVIDE_INFO, UNDER_REVIEW),
                Arguments.of(UNDER_REVIEW, Action.ASSESS, ASSESSED),
                Arguments.of(ASSESSED, Action.ASSESS, ASSESSED),
                Arguments.of(ASSESSED, Action.SETTLE, SETTLED),
                Arguments.of(UNDER_REVIEW, Action.REJECT, REJECTED),
                Arguments.of(ASSESSED, Action.REJECT, REJECTED));
    }

    /** Every (status, action) pair not in {@link #allowed()}: 6 x 6 - 9 = 27 cases. */
    static Stream<Arguments> forbidden() {
        Set<String> ok = allowed().map(a -> a.get()[0] + "/" + a.get()[1]).collect(Collectors.toSet());
        return Arrays.stream(ClaimStatus.values())
                .flatMap(s -> Arrays.stream(Action.values()).map(a -> Arguments.of(s, a)))
                .filter(a -> !ok.contains(a.get()[0] + "/" + a.get()[1]));
    }

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @MethodSource("allowed")
    void allowedTransition(ClaimStatus from, Action action, ClaimStatus to) {
        assertThat(ClaimStateMachine.isAllowed(from, action)).isTrue();
        assertThat(ClaimStateMachine.next(from, action)).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} --{1}--> 409")
    @MethodSource("forbidden")
    void forbiddenTransition(ClaimStatus from, Action action) {
        assertThat(ClaimStateMachine.isAllowed(from, action)).isFalse();
        assertThatThrownBy(() -> ClaimStateMachine.next(from, action))
                .isInstanceOfSatisfying(InvalidTransitionException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getBody().getDetail()).contains(from.name()).contains(action.name());
                    assertThat(e.getBody().getProperties())
                            .containsEntry("currentStatus", from)
                            .containsEntry("action", action);
                });
    }

    @Test
    void tablesCoverEveryPair() {
        assertThat(allowed().count() + forbidden().count())
                .isEqualTo((long) ClaimStatus.values().length * Action.values().length);
        assertThat(ClaimStateMachine.TRANSITIONS).hasSize((int) allowed().count());
    }

    @Test
    void closedStatusesAreTerminal() {
        for (ClaimStatus closed : new ClaimStatus[] {SETTLED, REJECTED}) {
            assertThat(closed.isClosed()).isTrue();
            assertThat(Arrays.stream(Action.values()).noneMatch(a -> ClaimStateMachine.isAllowed(closed, a))).isTrue();
        }
    }
}
