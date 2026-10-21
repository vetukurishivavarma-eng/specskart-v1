package com.specskart.whatsapp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Every message we sent, by Meta's id: a customer's reaction names only that id, so this is how
 * "Reacted 👍" becomes "Reacted 👍 to: Back in stock...". Written at the one place all sends go
 * through (MetaWhatsAppProvider.post). ponytail: never pruned; delete rows over 60 days if it grows.
 */
@Entity
@Table(name = "wa_sent_messages")
@Getter
@Setter
public class SentMessage {

    @Id
    private String waMessageId;

    private String toWaId;

    @Column(columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
