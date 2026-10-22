package io.github.serfom256.intentflow.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

/**
 * Decides who may use the IntentFlow admin UI and API. The starter checks the
 * application's Spring Security
 * authentication when Spring Security is present and falls back to HTTP Basic
 * otherwise; an {@code AdminAccess}
 * bean replaces both, e.g. to check a token claim, a tenant or the client
 * network.
 */
public interface AdminAccess {

    enum Outcome {
        ALLOWED,
        UNAUTHENTICATED,
        FORBIDDEN
    }

    /**
     * The result of a check. {@code editor} is {@code true} for a user who may save
     * and reset overrides, and
     * {@code false} for one who may only read the catalog and explain questions.
     */
    record Decision(Outcome outcome, @Nullable String user, @Nullable String challenge, boolean editor) {

        public static Decision allowed(@Nullable String user) {
            return new Decision(Outcome.ALLOWED, user, null, true);
        }

        public static Decision readOnly(@Nullable String user) {
            return new Decision(Outcome.ALLOWED, user, null, false);
        }

        public static Decision unauthenticated(@Nullable String challenge) {
            return new Decision(Outcome.UNAUTHENTICATED, null, challenge, false);
        }

        public static Decision forbidden(@Nullable String user) {
            return new Decision(Outcome.FORBIDDEN, user, null, false);
        }
    }

    /** Checks one request to the admin. */
    Decision check(HttpServletRequest request);

    /**
     * Whether the UI page and its assets need the check too. Implementations whose
     * sign-in happens in the browser,
     * such as HTTP Basic, return {@code true}; others let the page load and show
     * its own sign-in screen.
     */
    default boolean protectsPages() {
        return false;
    }
}
