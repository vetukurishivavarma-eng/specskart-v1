package com.specskart.shared;

import java.time.Instant;

/**
 * One chat-ready "here's where your order is" line, plus when that order was placed so the
 * bot can pick the customer's most recent one across the frames and lens funnels.
 */
public record TrackUpdate(Instant at, String message) {
}
