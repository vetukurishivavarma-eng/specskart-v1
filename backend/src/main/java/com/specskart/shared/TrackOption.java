package com.specskart.shared;

import java.time.Instant;

/**
 * One of the customer's orders, offered as a row in a WhatsApp list so they can pick which one
 * they meant.
 *
 * "Track my order" used to answer with whichever order was newest, which is right for someone
 * with one order and wrong for anyone who has bought twice — they asked about the older one and
 * were told about the new one, with no way to say otherwise.
 *
 * @param rowId  what comes back when the row is tapped, "TRACK:L:&lt;uuid&gt;" or "TRACK:O:&lt;orderNo&gt;"
 * @param at     when the order was placed, so both funnels can be merged newest-first
 * @param title  the row's heading; Meta truncates past 24 characters
 * @param description the row's second line; Meta truncates past 72
 * @param message what to send once the row is tapped
 */
public record TrackOption(String rowId, Instant at, String title, String description, String message) {
}
