package com.specskart.lead;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A prescription staff keep on a customer: a chat photo we copied (WhatsApp drops media after ~30 days)
 *  and/or the numbers typed from it. Sales and online orders keep their own Rx; see LeadPrescriptionService. */
@Entity
@Table(name = "lead_prescriptions")
@Getter
@Setter
public class LeadPrescription extends BaseEntity {

    @Column(nullable = false)
    private UUID leadId;
    /** WhatsApp media id it was saved from -- one copy per photo. */
    @Column(length = 100)
    private String mediaId;
    /** prescription_files row holding the photo bytes (admin-only table). */
    private UUID fileId;

    private BigDecimal sphRight;
    private BigDecimal sphLeft;
    private BigDecimal cylRight;
    private BigDecimal cylLeft;
    private Integer axisRight;
    private Integer axisLeft;
    private BigDecimal addPower;
    @Column(length = 16)
    private String pd;
    private String savedBy;
}
