package com.specskart;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadService;
import com.specskart.recommendation.RecommendationDtos.FrameDto;
import com.specskart.recommendation.RecommendationDtos.StyleProfile;
import com.specskart.recommendation.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class StyleQuizTest {

    @Autowired RecommendationService rec;
    @Autowired LeadService leadService;
    @Autowired LeadRepository leads;

    private List<String> codes(List<FrameDto> f) {
        return f.stream().map(FrameDto::code).toList();
    }

    @Test
    void stylePersistsOnLead() {
        Lead l = new Lead();
        l.setWhatsappWaId("style-" + System.nanoTime());
        l.setName("Quiz Taker");
        l = leads.save(l);

        leadService.saveStyleProfile(l.getId(), "classic", "warm", "mid", "high");

        Lead reloaded = leads.findById(l.getId()).orElseThrow();
        assertThat(reloaded.getStyleVibe()).isEqualTo("classic");
        assertThat(reloaded.getStyleColour()).isEqualTo("warm");
        assertThat(reloaded.getStyleBudget()).isEqualTo("mid");
        assertThat(reloaded.getStyleScreenHours()).isEqualTo("high");
    }

    @Test
    void styleReRanksRecommendationsDeterministically() {
        var base = codes(rec.forFaceShape("OVAL").recommended());

        var classic = codes(rec.forFaceShapeWithStyle("OVAL",
                new StyleProfile("classic", null, null, null)).recommended());
        assertThat(classic).isNotEqualTo(base);
        assertThat(classic.indexOf("WAYFARER")).isLessThan(base.indexOf("WAYFARER")); // lifted

        var bold = codes(rec.forFaceShapeWithStyle("OVAL",
                new StyleProfile("bold", null, null, null)).recommended());
        assertThat(bold.indexOf("GEOMETRIC")).isLessThan(base.indexOf("GEOMETRIC")); // lifted

        // same input => same output
        assertThat(classic).isEqualTo(codes(rec.forFaceShapeWithStyle("OVAL",
                new StyleProfile("classic", null, null, null)).recommended()));

        // empty profile => untouched
        assertThat(codes(rec.forFaceShapeWithStyle("OVAL",
                new StyleProfile(null, null, null, null)).recommended())).isEqualTo(base);
    }
}
