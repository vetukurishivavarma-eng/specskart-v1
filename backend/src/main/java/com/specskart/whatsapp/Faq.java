package com.specskart.whatsapp;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One WhatsApp FAQ: a list row (title + question) that replies with {@code answer}. */
@Getter
@Setter
@Entity
@Table(name = "faqs")
public class Faq extends BaseEntity {

    @Column(nullable = false, length = 24)
    private String title;

    @Column(nullable = false, length = 72)
    private String question;

    @Column(nullable = false, length = 1000)
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
