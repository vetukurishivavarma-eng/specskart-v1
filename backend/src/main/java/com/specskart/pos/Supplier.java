package com.specskart.pos;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A wholesaler the shop buys frames from. */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String contactName = "";
    @Column(nullable = false)
    private String phone = "";
    @Column(nullable = false)
    private String email = "";
    @Column(nullable = false)
    private String address = "";
    @Column(nullable = false)
    private String notes = "";
    @Column(nullable = false)
    private boolean active = true;
}
