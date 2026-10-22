package com.specskart;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.AdminChatController;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppBotService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class AdminChatTest {

    @Autowired AdminChatController chat;
    @Autowired WhatsAppBotService bot;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppProvider provider;
    @Autowired com.specskart.whatsapp.WhatsAppWebhookController webhook;

    private String newLead() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        Lead l = new Lead();
        l.setWhatsappWaId(waId);
        l.setWhatsappNumber(waId);
        leads.save(l);
        return waId;
    }

    private long sentTo(String waId) {
        return ((MockWhatsAppProvider) provider).outbox().stream().filter(s -> waId.equals(s.toWaId())).count();
    }

    private void customerSays(String waId, String text) {
        bot.handleInbound(leads.findByWhatsappWaId(waId).orElseThrow(), text, null, "wamid." + UUID.randomUUID());
    }

    @Test
    void staffMessageGoesFromBusinessNumberAndKeepsTheBotQuietUntilHandedBack() {
        String waId = newLead();
        customerSays(waId, "hello");                 // opens the 24h window; bot answers
        long afterGreeting = sentTo(waId);
        assertThat(afterGreeting).isGreaterThan(0);

        chat.send(waId, new AdminChatController.SendRequest("Hi, your glasses are ready"));
        assertThat(((MockWhatsAppProvider) provider).outbox())
                .anyMatch(s -> waId.equals(s.toWaId()) && "Hi, your glasses are ready".equals(s.text()));
        long afterStaff = sentTo(waId);

        customerSays(waId, "ok thanks");             // off-script: the bot would send its menu
        assertThat(sentTo(waId)).isEqualTo(afterStaff);
        assertThat(chat.get(waId).get("botQuietUntil")).isNotNull();

        chat.resumeBot(waId);
        customerSays(waId, "menu");
        assertThat(sentTo(waId)).isGreaterThan(afterStaff);
    }

    @Test
    void newNumberChatIsRefusedAndSavesNothingWithoutATemplate() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        assertThatThrownBy(() -> chat.start(waId, new AdminChatController.StartRequest("X")))
                .hasMessageContaining("template");
        assertThat(leads.findByWhatsappWaId(waId)).isEmpty();
        assertThat(sentTo(waId)).isZero();
    }

    @Test
    void refusesPlainTextOutsideTheServiceWindow() {
        String waId = newLead();                     // never messaged us: window shut
        assertThatThrownBy(() -> chat.send(waId, new AdminChatController.SendRequest("Hello")))
                .hasMessageContaining("24 hours");
        assertThat(sentTo(waId)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void customerPhotoShowsInTheChatAndOnlyThisChatsMediaCanBeFetched() {
        String waId = newLead();
        bot.handleInbound(leads.findByWhatsappWaId(waId).orElseThrow(), "[image]", null,
                "wamid." + UUID.randomUUID(), "media-" + waId, null);

        var msgs = (java.util.List<java.util.Map<String, Object>>) chat.get(waId).get("messages");
        assertThat(msgs).anyMatch(m -> "image".equals(m.get("type")) && ("media-" + waId).equals(m.get("mediaId")));

        bot.handleInbound(leads.findByWhatsappWaId(waId).orElseThrow(), "[document]", null,
                "wamid." + UUID.randomUUID(), "doc-" + waId, "prescription.pdf");
        msgs = (java.util.List<java.util.Map<String, Object>>) chat.get(waId).get("messages");
        assertThat(msgs).anyMatch(m -> "document".equals(m.get("type")) && "prescription.pdf".equals(m.get("body")));

        assertThatThrownBy(() -> chat.media(waId, "someone-elses-media")).hasMessageContaining("isn't in this chat");
        // the mock provider serves every media id as a tiny PNG
        assertThat(chat.media(waId, "media-" + waId).getHeaders().getContentType().toString()).isEqualTo("image/png");
    }

    @Test
    void mediaGetsAnExtensionThePhoneCanOpen() {
        assertThat(AdminChatController.extension("application/pdf")).isEqualTo("pdf");
        assertThat(AdminChatController.extension("audio/ogg; codecs=opus")).isEqualTo("ogg");
        assertThat(AdminChatController.extension("something/odd")).isEqualTo("bin");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMessageMetaWillNotShowUsStillAppearsInTheChatInPlainWords() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        String payload = """
            {"entry":[{"changes":[{"value":{"contacts":[{"profile":{"name":"Bana"},"wa_id":"%s"}],
              "messages":[{"from":"%s","id":"wamid.%s","type":"unsupported","unsupported":{"type":"edit"},
                "errors":[{"code":131051,"title":"Message type unknown"}]}]}}]}]}
            """.formatted(waId, waId, UUID.randomUUID());
        webhook.receive(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        var msgs = (java.util.List<java.util.Map<String, Object>>) chat.get(waId).get("messages");
        assertThat(msgs).anyMatch(m -> String.valueOf(m.get("body")).contains("WhatsApp doesn't show us: edit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aReactionShowsItsEmojiAndGetsNoReply() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        String ours = provider.sendText(waId, "Back in stock 👓\n*Rayban* — K450");
        long before = sentTo(waId);
        String payload = """
            {"entry":[{"changes":[{"value":{"contacts":[{"profile":{"name":"BTS"},"wa_id":"%s"}],
              "messages":[{"from":"%s","id":"wamid.%s","type":"reaction",
                "reaction":{"message_id":"%s","emoji":"👍"}}]}}]}]}
            """.formatted(waId, waId, UUID.randomUUID(), ours);
        webhook.receive(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        var msgs = (java.util.List<java.util.Map<String, Object>>) chat.get(waId).get("messages");
        assertThat(msgs).anyMatch(m -> "Reacted 👍 to: \"Back in stock 👓 *Rayban* — K450\"".equals(m.get("body")));
        assertThat(sentTo(waId)).isEqualTo(before); // no menu fired back at a thumbs-up
    }

    @Test
    @SuppressWarnings("unchecked")
    void aWhatsAppSystemNoticeShowsItsWordsAndGetsNoReply() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        long before = sentTo(waId);
        String payload = """
            {"entry":[{"changes":[{"value":{"contacts":[{"profile":{"name":"Changer"},"wa_id":"%s"}],
              "messages":[{"from":"%s","id":"wamid.%s","type":"system",
                "system":{"body":"User A changed from %s to 260970000000","wa_id":"260970000000","type":"user_changed_number"}}]}}]}]}
            """.formatted(waId, waId, UUID.randomUUID(), waId);
        webhook.receive(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        var msgs = (java.util.List<java.util.Map<String, Object>>) chat.get(waId).get("messages");
        assertThat(msgs).anyMatch(m -> String.valueOf(m.get("body")).startsWith("WhatsApp: User A changed from"));
        assertThat(sentTo(waId)).isEqualTo(before); // no menu sent back
    }

    @Test
    @SuppressWarnings("unchecked")
    void locationContactAndUnknownMessagesReadPlainlyAndGetNoMenu() {
        String waId = "26097" + (300000 + (int) (Math.random() * 600000));
        long before = sentTo(waId);
        String[] bodies = {
            "\"type\":\"location\",\"location\":{\"latitude\":-15.41,\"longitude\":28.28,\"name\":\"Manda Hill\"}",
            "\"type\":\"contacts\",\"contacts\":[{\"name\":{\"formatted_name\":\"Mum\"},\"phones\":[{\"phone\":\"+260971111111\"}]}]",
            "\"type\":\"poll_vote\""};
        for (String body : bodies) {
            String payload = """
                {"entry":[{"changes":[{"value":{"contacts":[{"profile":{"name":"Types"},"wa_id":"%s"}],
                  "messages":[{"from":"%s","id":"wamid.%s",%s}]}}]}]}
                """.formatted(waId, waId, UUID.randomUUID(), body);
            webhook.receive(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        }
        var msgs = (java.util.List<java.util.Map<String, Object>>) chat.get(waId).get("messages");
        assertThat(msgs).anyMatch(m -> String.valueOf(m.get("body")).startsWith("📍 Shared a location: Manda Hill")
                && String.valueOf(m.get("body")).contains("maps.google.com/?q=-15.41,28.28"));
        assertThat(msgs).anyMatch(m -> String.valueOf(m.get("body")).contains("Mum · +260971111111"));
        assertThat(msgs).anyMatch(m -> String.valueOf(m.get("body")).contains("can't show (poll_vote)"));
        assertThat(msgs).noneMatch(m -> String.valueOf(m.get("body")).startsWith("⁠"));
        assertThat(sentTo(waId)).isEqualTo(before); // a person answers these, not the menu
    }

    @Autowired com.specskart.whatsapp.WhatsAppMessageRepository chatLog;

    @Test
    @SuppressWarnings("unchecked")
    void staffReplyQuoteSendAFileReactAndSeeTicksLikeWhatsApp() {
        String waId = newLead();
        customerSays(waId, "do you have round frames?");
        var msgs = (java.util.List<java.util.Map<String, Object>>) chat.get(waId).get("messages");
        UUID question = (UUID) msgs.stream().filter(m -> "INBOUND".equals(m.get("direction"))).findFirst().orElseThrow().get("id");

        // swipe-to-reply
        msgs = (java.util.List<java.util.Map<String, Object>>) chat.send(waId,
                new AdminChatController.SendRequest("Yes, from K300", question)).get("messages");
        var reply = msgs.get(msgs.size() - 1);
        assertThat(reply).containsEntry("replyTo", "do you have round frames?").containsEntry("status", "sent");

        // a file from the phone
        var file = new org.springframework.mock.web.MockMultipartFile("file", "price-list.pdf", "application/pdf", new byte[] {1, 2});
        try {
            msgs = (java.util.List<java.util.Map<String, Object>>) chat.attach(waId, file, null, null).get("messages");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        assertThat(msgs.get(msgs.size() - 1)).containsEntry("type", "document").containsEntry("body", "price-list.pdf");
        assertThat((String) msgs.get(msgs.size() - 1).get("mediaId")).isNotBlank();

        // a reaction on their message
        msgs = (java.util.List<java.util.Map<String, Object>>) chat.react(waId, question,
                new AdminChatController.ReactRequest("👍")).get("messages");
        assertThat(msgs.get(msgs.size() - 1).get("body")).isEqualTo("Reacted 👍 to: \"do you have round frames?\"");

        // ticks move forward only: read, then a late "delivered" doesn't undo it
        String wamid = chatLog.findById((UUID) reply.get("id")).orElseThrow().getWaMessageId();
        for (String st : new String[] {"read", "delivered"}) {
            String payload = """
                {"entry":[{"changes":[{"value":{"statuses":[{"id":"%s","status":"%s","recipient_id":"%s"}]}}]}]}
                """.formatted(wamid, st, waId);
            webhook.receive(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        }
        assertThat(chatLog.findById((UUID) reply.get("id")).orElseThrow().getStatus()).isEqualTo("read");
        assertThat(chat.read(waId)).containsEntry("ok", true);
    }

    @Test
    void theSentMessageIndexKeepsWhatTheCustomerSaw() {
        assertThat(com.specskart.whatsapp.MetaWhatsAppProvider.summary(java.util.Map.of("type", "interactive",
                "interactive", java.util.Map.of("body", java.util.Map.of("text", "Pick a day"))))).isEqualTo("Pick a day");
        assertThat(com.specskart.whatsapp.MetaWhatsAppProvider.summary(java.util.Map.of("type", "template",
                "template", java.util.Map.of("name", "order_update")))).isEqualTo("(template order_update)");
    }
}
