package com.specskart.push;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A staff phone's Firebase Cloud Messaging token, registered by the POS app after login. */
@Entity
@Table(name = "push_tokens")
@Getter
@Setter
public class PushToken extends BaseEntity {

    @Column(nullable = false, unique = true, length = 4096)
    private String token;

    @Column(nullable = false)
    private String userEmail;
}
