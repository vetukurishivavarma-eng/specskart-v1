package com.specskart.admin;

import com.specskart.config.AppProperties;
import com.specskart.framefinder.FrameFinderService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Integration status surfaced in the CRM (WhatsApp mode, simulation flag, retention policy). */
@RestController
@RequestMapping("/api/admin/system")
public class SystemStatusController {

    private final WhatsAppProvider whatsapp;
    private final AppProperties props;
    private final boolean simulationEnabled;

    public SystemStatusController(WhatsAppProvider whatsapp, AppProperties props,
                                  @Value("${specskart.simulation.enabled:false}") boolean simulationEnabled) {
        this.whatsapp = whatsapp;
        this.props = props;
        this.simulationEnabled = simulationEnabled;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean waConfigured = "mock".equals(props.whatsapp().provider())
                || (props.whatsapp().accessToken() != null && !props.whatsapp().accessToken().isBlank());
        return Map.of(
                "storeName", props.storeName(),
                "whatsappMode", whatsapp.mode(),
                "whatsappProvider", props.whatsapp().provider(),
                "whatsappConfigured", waConfigured,
                "simulationEnabled", simulationEnabled,
                "frameRetainImages", props.face().retainImages(),
                "sessionExpiryHours", props.session().expiryHours(),
                "consentPolicyVersion", FrameFinderService.POLICY_VERSION);
    }
}
