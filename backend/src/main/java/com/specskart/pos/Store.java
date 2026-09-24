package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A physical Specskart shop location. */
@Entity
@Table(name = "stores")
@Getter
@Setter
public class Store extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String city = "";

    /** Street address, for the "come and collect" message. The map pin is for distance maths;
     *  this is what a person reads and walks to. */
    @Column(length = 500)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    /** Map pin. Set on an active shop = its stock sells online and it ships web orders (nearest wins). */
    private Double latitude;
    private Double longitude;
}
