package com.specskart.spin;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One slice of the Spin & Win wheel. Its share of the wheel IS its chance: weight / sum of weights. */
@Entity
@Table(name = "spin_prizes")
@Getter
@Setter
public class SpinPrize extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private int weight;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sortOrder;
}
