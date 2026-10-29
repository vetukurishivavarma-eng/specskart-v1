package com.specskart.lead;

import com.specskart.lens.LensInquiry;
import com.specskart.lens.LensInquiryRepository;
import com.specskart.whatsapp.WhatsAppMessage;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class LeadPrescriptionTest {

    @Autowired LeadPrescriptionService prescriptions;
    @Autowired LeadRepository leads;
    @Autowired LensInquiryRepository inquiries;
    @Autowired WhatsAppMessageRepository messages;

    private Lead lead() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        Lead l = new Lead();
        l.setWhatsappWaId(waId);
        l.setWhatsappNumber(waId);
        return leads.save(l);
    }

    @Test
    void aCounterSaleShowsOnTheLeadByWhatsAppNumber() {
        Lead l = lead();
        LensInquiry sale = new LensInquiry(); // counter sale: number only, never linked to the lead
        sale.setWalkIn(true);
        sale.setWaId(l.getWhatsappWaId());
        sale.setPhoneRaw(l.getWhatsappWaId());
        sale.setVerifyTokenHash("t-" + UUID.randomUUID());
        sale.setSphRight(new BigDecimal("-1.25"));
        sale.setShopName("Lusaka");
        inquiries.save(sale);

        var list = prescriptions.forLead(l);
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("kind", "SHOP").containsEntry("shopName", "Lusaka");
    }

    @Test
    void theCounterFindsLastPrescriptionByNumberEvenWithoutALead() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        LensInquiry sale = new LensInquiry();
        sale.setWalkIn(true);
        sale.setWaId(waId);
        sale.setPhoneRaw("0" + waId.substring(3));
        sale.setVerifyTokenHash("t-" + UUID.randomUUID());
        sale.setSphLeft(new BigDecimal("-0.25"));
        sale.setAddPower(new BigDecimal("2.00"));
        sale.setLensStructure("BIFOCAL");
        inquiries.save(sale);

        var list = prescriptions.forPhone("0" + waId.substring(3)); // typed the local way
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("kind", "SHOP").containsEntry("lensStructure", "BIFOCAL");
        assertThat(prescriptions.forPhone("12")).isEmpty();
    }

    @Test
    void staffAddAPaperPrescriptionForANewNumber() {
        String local = "097" + (3000000 + (int) (Math.random() * 6000000));
        var p = prescriptions.create(local, "Paper Rx", "staff@specskart.local");
        Lead walkIn = leads.findById(p.getLeadId()).orElseThrow();
        assertThat(walkIn.getName()).isEqualTo("Paper Rx");
        prescriptions.uploadPhoto(walkIn.getId(), p.getId(), new byte[] {1, 2, 3}, "image/jpeg");
        prescriptions.setNumbers(walkIn.getId(), p.getId(), new LeadPrescriptionService.Numbers(
                new BigDecimal("-1"), null, null, null, null, null, null, null, null));
        assertThat(leads.findById(walkIn.getId()).orElseThrow().getName()).isEqualTo("Paper Rx"); // blank name: kept
        prescriptions.setNumbers(walkIn.getId(), p.getId(), new LeadPrescriptionService.Numbers(
                new BigDecimal("-1"), null, null, null, null, null, null, null, "  Mwila Banda "));
        assertThat(leads.findById(walkIn.getId()).orElseThrow().getName()).isEqualTo("Mwila Banda");
        assertThat(prescriptions.forPhone(local)).singleElement().satisfies(e ->
                assertThat(e).containsEntry("kind", "SAVED").containsEntry("hasPhoto", true));
        // same number again -> same customer, no duplicate lead
        assertThat(prescriptions.create(local, null, "x").getLeadId()).isEqualTo(walkIn.getId());
        assertThatThrownBy(() -> prescriptions.uploadPhoto(walkIn.getId(), p.getId(), new byte[] {1}, "application/pdf"))
                .hasMessageContaining("Only photos");
    }

    @Test
    void aChatPhotoIsSavedOnceAndTheNumbersTypedFromIt() {
        Lead l = lead();
        String mediaId = "m-" + UUID.randomUUID();
        WhatsAppMessage m = new WhatsAppMessage();
        m.setLeadId(l.getId());
        m.setDirection("INBOUND");
        m.setMessageType("image");
        m.setMediaId(mediaId);
        messages.save(m);

        var p = prescriptions.savePhoto(l, mediaId, "staff@specskart.local");
        assertThat(prescriptions.savePhoto(l, mediaId, "staff@specskart.local").getId()).isEqualTo(p.getId());
        assertThat(prescriptions.photo(l.getId(), p.getId()).getContentType()).isEqualTo("image/png");
        assertThatThrownBy(() -> prescriptions.savePhoto(l, "not-in-this-chat", "x")).hasMessageContaining("isn't in this chat");

        prescriptions.setNumbers(l.getId(), p.getId(), new LeadPrescriptionService.Numbers(
                new BigDecimal("-2"), new BigDecimal("-1.75"), new BigDecimal("-0.5"), null, 90, null, null, "62", null));
        Map<String, Object> row = prescriptions.forLead(l).get(0);
        assertThat(row).containsEntry("kind", "SAVED").containsEntry("hasPhoto", true).containsEntry("pd", "62");
        assertThat((BigDecimal) row.get("sphRight")).isEqualByComparingTo("-2");

        assertThatThrownBy(() -> prescriptions.setNumbers(l.getId(), p.getId(), new LeadPrescriptionService.Numbers(
                null, null, null, null, 200, null, null, null, null))).hasMessageContaining("Axis");
        // another customer's lead can't reach it
        assertThatThrownBy(() -> prescriptions.photo(lead().getId(), p.getId())).hasMessageContaining("not found");
    }
}
