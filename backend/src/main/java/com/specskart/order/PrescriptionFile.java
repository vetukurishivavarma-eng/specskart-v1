package com.specskart.order;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Raw bytes of a customer-uploaded prescription photo/PDF. Isolated table, admin-only
 *  access — this is personal medical-ish data, never served publicly. */
@Entity
@Table(name = "prescription_files")
@Getter
@Setter
public class PrescriptionFile extends BaseEntity {

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, length = 12_000_000)
    private byte[] bytes;
}
