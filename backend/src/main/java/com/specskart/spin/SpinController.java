package com.specskart.spin;

import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The customer's spin page (signed link from WhatsApp) and the staff side: look up / use a code, edit the wheel. */
@RestController
public class SpinController {

    private final SpinService spins;
    private final CurrentUser currentUser;

    public SpinController(SpinService spins, CurrentUser currentUser) {
        this.spins = spins;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/public/spin/{leadId}")
    public SpinService.Page page(@PathVariable UUID leadId, @RequestParam long exp, @RequestParam String sig) {
        return spins.page(leadId, exp, sig);
    }

    @PostMapping("/api/public/spin/{leadId}")
    public SpinService.Result spin(@PathVariable UUID leadId, @RequestParam long exp, @RequestParam String sig) {
        return spins.spin(leadId, exp, sig);
    }

    @GetMapping("/api/admin/spins")
    public List<SpinService.SpinView> recent() {
        return spins.recent();
    }

    @GetMapping("/api/admin/spins/code/{code}")
    public SpinService.SpinView lookup(@PathVariable String code) {
        return spins.lookup(code);
    }

    @PostMapping("/api/admin/spins/code/{code}/redeem")
    public SpinService.SpinView redeem(@PathVariable String code, Authentication auth) {
        return spins.redeem(code, currentUser.nameOf(auth));
    }

    @GetMapping("/api/admin/spins/prizes")
    public List<SpinPrize> prizes() {
        return spins.prizes();
    }

    /** ADMIN only (SecurityConfig): what the wheel holds and each prize's chance. */
    @PutMapping("/api/admin/spins/prizes")
    public List<SpinPrize> savePrizes(@RequestBody List<SpinService.PrizeInput> prizes) {
        return spins.savePrizes(prizes);
    }
}
