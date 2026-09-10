package com.specskart.lead;

/** Lifecycle of a lead's automated WhatsApp nurture sequence. */
public enum FollowUpState {
    /** In the sequence — the job sends the next touch when it comes due. */
    ACTIVE,
    /** Bought something — sequence stopped, success. */
    CONVERTED,
    /** Replied STOP / unsubscribe — sequence stopped, do not message again. */
    OPTED_OUT,
    /** Ran through every touch without converting — sequence stopped. */
    DONE
}
