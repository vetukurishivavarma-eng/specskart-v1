package com.specskart.simulation;

import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.WhatsAppProvider;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Local simulation harness. Enabled only when specskart.simulation.enabled=true (dev/mock profiles).
 * Lets you drive the whole funnel without Meta credentials.
 */
@RestController
@RequestMapping("/api/sim")
@ConditionalOnProperty(name = "specskart.simulation.enabled", havingValue = "true")
public class SimulationController {

    private final WhatsAppInboundService inbound;
    private final WhatsAppProvider provider;

    public SimulationController(WhatsAppInboundService inbound, WhatsAppProvider provider) {
        this.inbound = inbound;
        this.provider = provider;
    }

    public record SimInbound(@NotBlank String waId, String phone, String name, String text,
                             String buttonId, Map<String, Object> referral) {}

    @PostMapping("/whatsapp/inbound")
    public Map<String, Object> simulateInbound(@RequestBody SimInbound req) {
        inbound.process(new InboundMessage(
                req.waId(),
                req.phone() != null ? req.phone() : req.waId(),
                req.name(),
                req.text(),
                req.buttonId(),
                "sim-" + java.util.UUID.randomUUID(),
                req.referral()));
        return Map.of("ok", true, "outbox", outbox());
    }

    @GetMapping("/whatsapp/outbox")
    public Object outbox() {
        if (provider instanceof MockWhatsAppProvider mock) {
            return mock.outbox();
        }
        return List.of();
    }
}
