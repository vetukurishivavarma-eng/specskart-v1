package com.specskart.order;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Hands a staff WhatsApp alert straight into the POS app, on the order it is about.
 *
 * <p>Why a web page and not the app's own URL: WhatsApp only turns http(s) into a tappable link.
 * A "specskartpos://" written into a message body renders as dead plain text, so the message
 * carries an https link here and this page performs the jump.
 *
 * <p>Deliberately unsigned, unlike the PDFs next door in {@link StaffDocController}. Those carry
 * health data and expire in 48 hours; this one carries an opaque id, shows nothing, and only
 * opens an app that then demands a login. Signing it would also put a clock on it, and the whole
 * point is that the lab can act on the alert the next morning.
 */
@RestController
@RequestMapping("/api/public/open")
public class AppLinkController {

    /** Must match "scheme" in the POS app's app.json. */
    private static final String SCHEME = "specskartpos";

    /** A lens order, onto the doorstep ladder in the app's Orders screen. */
    @GetMapping("/lens/{id}")
    public ResponseEntity<String> lens(@PathVariable UUID id) {
        return page(SCHEME + "://orders?id=" + id);
    }

    /** A website order, onto the app's Deliveries screen. */
    @GetMapping("/order/{id}")
    public ResponseEntity<String> order(@PathVariable UUID id) {
        return page(SCHEME + "://deliveries?id=" + id);
    }

    /**
     * `id` is bound as a UUID, so by the time it reaches here it cannot contain a quote, an angle
     * bracket or anything else that would break out of the markup below. That is the whole reason
     * both routes take the id rather than the human-readable order number.
     */
    private static ResponseEntity<String> page(String target) {
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Opening Specskart POS</title>
                <style>
                  body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#f6f3ee;
                       color:#1a1a1a;margin:0;min-height:100vh;display:flex;align-items:center;
                       justify-content:center;padding:24px;box-sizing:border-box}
                  .card{max-width:22rem;text-align:center}
                  h1{font-size:1.15rem;margin:0 0 .5rem}
                  a.btn{display:inline-block;margin:1rem 0;padding:.8rem 1.4rem;border-radius:.6rem;
                        background:#1a1a1a;color:#fff;text-decoration:none;font-weight:600}
                  p.hint{color:#6b6b6b;font-size:.85rem;line-height:1.45}
                </style>
                </head>
                <body>
                <div class="card">
                  <h1>Opening Specskart POS&hellip;</h1>
                  <a class="btn" href="TARGET">Open the order</a>
                  <p class="hint">If nothing happens, the Specskart POS app is not installed on this
                  phone, or you are opening this on a computer. Open the alert on the shop&rsquo;s
                  Android device.</p>
                </div>
                <script>location.replace("TARGET");</script>
                </body>
                </html>
                """.replace("TARGET", target);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // An alert is re-tapped after the order has moved on; never serve a cached jump.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(html);
    }
}
