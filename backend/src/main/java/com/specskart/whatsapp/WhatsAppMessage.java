package com.specskart.whatsapp;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "whatsapp_messages", indexes = @Index(name = "idx_wamsg_lead", columnList = "leadId"))
@Getter
@Setter
public class WhatsAppMessage extends BaseEntity {

    @Column(nullable = false)
    private UUID leadId;

    @Column(nullable = false)
    private String direction; // INBOUND | OUTBOUND

    private String waMessageId;
    private String messageType; // text | interactive | template | button_reply

    @Column(length = 4000)
    private String body;

    private String status; // sent | delivered | read | failed
}
