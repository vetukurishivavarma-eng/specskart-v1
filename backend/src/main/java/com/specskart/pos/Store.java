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

    @Column(nullable = false)
    private boolean active = true;
}
