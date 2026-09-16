package com.specskart.lead;

import com.specskart.order.Order;
import com.specskart.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Who the yearly eye-test recall picks up, and who it must leave alone. */
@SpringBootTest
@ActiveProfiles("mock")
class EyeTestRecallTest {

    @Autowired LeadRepository leads;
    @Autowired EyeTestRecallService service;
    @Autowired EntityManager em;

    private static final Instant CUTOFF = Instant.now().minus(365, ChronoUnit.DAYS);

    @Test
    @Transactional
    void aYearOldDeliveredOrderMakesItsCustomerDue() {
        Lead lead = customerWithDeliveredOrder(400);
        assertThat(dueIds()).contains(lead.getId());
    }

    @Test
    @Transactional
    void arecentBuyerIsNotDueYet() {
        Lead lead = customerWithDeliveredOrder(30);
        assertThat(dueIds()).doesNotContain(lead.getId());
    }

    @Test
    @Transactional
    void someoneWhoRepliedStopIsNeverRecalled() {
        Lead lead = customerWithDeliveredOrder(400);
        lead.setFollowUpState(FollowUpState.OPTED_OUT);
        leads.saveAndFlush(lead);
        assertThat(dueIds()).doesNotContain(lead.getId());
    }

    @Test
    @Transactional
    void recallingMarksThemSoTheyAreNotNudgedAgainTomorrow() {
        Lead lead = customerWithDeliveredOrder(400);
        service.recall(lead.getId());
        em.flush();
        em.clear();

        Lead after = leads.findById(lead.getId()).orElseThrow();
        assertThat(after.getEyeTestRecalledAt()).isNotNull();
        assertThat(dueIds()).doesNotContain(lead.getId());
    }

    @Test
    void theplainMessageCarriesTheOfferAndTheOptOut() {
        String msg = EyeTestRecallService.plainMessage("Grace", 12, "10% off with code FIT-ABCDE", "https://x.test/store");
        assertThat(msg).contains("Grace").contains("12 months")
                .contains("FIT-ABCDE").contains("https://x.test/store").contains("STOP");
    }

    private List<UUID> dueIds() {
        em.flush();
        return leads.findDueForEyeTestRecall(CUTOFF, PageRequest.of(0, 100)).stream().map(Lead::getId).toList();
    }

    private Lead customerWithDeliveredOrder(int daysAgo) {
        Lead lead = new Lead();
        lead.setWhatsappWaId("2609" + (long) (Math.random() * 100_000_000L));
        lead.setName("Grace Banda");
        lead.setFirstContactAt(Instant.now().minus(daysAgo + 1, ChronoUnit.DAYS));
        leads.saveAndFlush(lead);

        Order order = new Order();
        order.setOrderNo("SK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setLeadId(lead.getId());
        order.setStatus(OrderStatus.DELIVERED);
        order.setCustomerName("Grace Banda");
        order.setCustomerPhone(lead.getWhatsappWaId());
        order.setShipAddress("5 Great East Rd");
        order.setShipCity("Lusaka");
        order.setSubtotalMinor(80_000);
        order.setTotalMinor(80_000);
        em.persist(order);
        em.flush();
        // createdAt is set by the entity's own auditing, so age it by hand.
        em.createQuery("update Order o set o.createdAt = :when where o.id = :id")
                .setParameter("when", Instant.now().minus(daysAgo, ChronoUnit.DAYS))
                .setParameter("id", order.getId())
                .executeUpdate();
        em.clear();
        return leads.findById(lead.getId()).orElseThrow();
    }
}
