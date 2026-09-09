package com.specskart.whatsapp;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic "have we seen this webhook event before?" gate. Meta delivers the same
 * message more than once (fast retries, at-least-once delivery), often near-
 * simultaneously — a check-then-insert races and both deliveries get processed,
 * so the customer gets duplicate bot replies.
 *
 * The claim runs in its own transaction and lets the unique constraint settle
 * the race: the losing delivery's insert throws, which the caller treats as a
 * duplicate. The exception is deliberately NOT caught here — swallowing it
 * inside this transactional method would leave the tx rollback-only and make
 * Spring raise UnexpectedRollbackException on exit.
 */
@Component
public class WebhookDedupe {

    private final WebhookEventRepository events;

    public WebhookDedupe(WebhookEventRepository events) {
        this.events = events;
    }

    /**
     * @return true if this key was newly claimed (first time seen). Returns false if it was
     *         already recorded; throws {@link org.springframework.dao.DataAccessException}
     *         if a concurrent delivery won the insert race — callers treat both as a duplicate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String dedupeKey, String kind) {
        if (events.existsByDedupeKey(dedupeKey)) return false;
        WebhookEvent ev = new WebhookEvent();
        ev.setDedupeKey(dedupeKey);
        ev.setKind(kind);
        events.saveAndFlush(ev); // flush now so a losing racer fails here, before any side effects
        return true;
    }
}
