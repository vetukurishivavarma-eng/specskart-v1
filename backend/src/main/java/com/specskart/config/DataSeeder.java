package com.specskart.config;

import com.specskart.auth.Role;
import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import com.specskart.campaign.*;
import com.specskart.recommendation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    ApplicationRunner seed(UserRepository users, PasswordEncoder encoder, CampaignRepository campaigns,
                           FaceShapeRepository faceShapes, FrameCategoryRepository frameCategories,
                           RecommendationRepository recs, Environment env) {
        return args -> {
            seedUsers(users, encoder, env);
            seedFrameCatalog(faceShapes, frameCategories, recs);
            seedRules(faceShapes, frameCategories, recs);
            if (campaigns.count() == 0 && isDemo(env)) seedCampaigns(campaigns);
        };
    }

    private boolean isDemo(Environment env) {
        for (String p : env.getActiveProfiles()) {
            if (p.equals("dev") || p.equals("mock")) return true;
        }
        return env.getActiveProfiles().length == 0;
    }

    private void seedUsers(UserRepository users, PasswordEncoder encoder, Environment env) {
        if (users.count() > 0) return;
        String adminPw = env.getProperty("SPECSKART_ADMIN_PASSWORD", "admin12345");
        User admin = new User();
        admin.setEmail(env.getProperty("SPECSKART_ADMIN_EMAIL", "admin@specskart.local"));
        admin.setPasswordHash(encoder.encode(adminPw));
        admin.setFullName("Store Admin");
        admin.setRole(Role.ADMIN);
        users.save(admin);

        if (isDemo(env)) {
            User agent = new User();
            agent.setEmail("agent@specskart.local");
            agent.setPasswordHash(encoder.encode("agent12345"));
            agent.setFullName("Sales Agent");
            agent.setRole(Role.AGENT);
            users.save(agent);
        }
        log.info("seeded users (admin={})", admin.getEmail());
    }

    private void seedFrameCatalog(FaceShapeRepository faceShapes, FrameCategoryRepository frameCategories,
                                  RecommendationRepository recs) {
        Map<String, String[]> shapes = Map.of(
                "OVAL", new String[]{"Oval", "Balanced proportions with a gently rounded jaw and slightly wider cheekbones."},
                "ROUND", new String[]{"Round", "Soft curves with similar face width and length and a rounded jaw."},
                "SQUARE", new String[]{"Square", "A strong, angular jaw with forehead, cheeks and jaw close in width."},
                "RECTANGLE", new String[]{"Rectangle / Oblong", "Longer than it is wide, with an angular jaw and even widths."},
                "HEART", new String[]{"Heart", "A wider forehead that tapers to a narrower jaw and chin."},
                "DIAMOND", new String[]{"Diamond", "Cheekbones are the widest point, with a narrower forehead and jaw."},
                "TRIANGLE", new String[]{"Triangle", "A wider jaw that narrows towards the forehead."});
        shapes.forEach((code, v) -> {
            if (!faceShapes.existsByCodeIgnoreCase(code)) {
                FaceShape s = new FaceShape();
                s.setCode(code);
                s.setDisplayName(v[0]);
                s.setDescription(v[1]);
                faceShapes.save(s);
            }
        });

        Map<String, String[]> cats = Map.ofEntries(
                Map.entry("RECTANGLE", new String[]{"Rectangular", "Angular frames that add contrast to softer faces."}),
                Map.entry("SQUARE_FRAME", new String[]{"Square", "Bold, structured frames with clean corners."}),
                Map.entry("WAYFARER", new String[]{"Wayfarer", "A timeless trapezoid shape that flatters most faces."}),
                Map.entry("AVIATOR", new String[]{"Aviator", "Teardrop lenses with a light metal brow bar."}),
                Map.entry("GEOMETRIC", new String[]{"Geometric", "Hexagonal and angular statement frames."}),
                Map.entry("BROWLINE", new String[]{"Browline", "Emphasis along the top rim, drawing the eye upward."}),
                Map.entry("ROUND_FRAME", new String[]{"Round", "Circular frames that soften angular features."}),
                Map.entry("OVAL_FRAME", new String[]{"Oval", "Gently curved frames with balanced width."}),
                Map.entry("CATEYE", new String[]{"Cat-eye", "Upswept outer corners that lift the face."}),
                Map.entry("OVERSIZED", new String[]{"Oversized", "Large frames that shorten a longer face."}),
                Map.entry("THIN_RIM", new String[]{"Thin-rim", "Minimal metal or rimless frames for a light look."}));
        cats.forEach((code, v) -> {
            if (!frameCategories.existsByCodeIgnoreCase(code)) {
                FrameCategory c = new FrameCategory();
                c.setCode(code);
                c.setDisplayName(v[0]);
                c.setDescription(v[1]);
                frameCategories.save(c);
            }
        });
    }

    private void seedRules(FaceShapeRepository faceShapes, FrameCategoryRepository frameCategories,
                           RecommendationRepository recs) {
        if (recs.count() > 0) return;
        Map<String, List<String>> positive = Map.of(
                "OVAL", List.of("RECTANGLE", "SQUARE_FRAME", "AVIATOR", "WAYFARER", "GEOMETRIC"),
                "ROUND", List.of("RECTANGLE", "SQUARE_FRAME", "BROWLINE", "GEOMETRIC", "WAYFARER"),
                "SQUARE", List.of("ROUND_FRAME", "OVAL_FRAME", "AVIATOR", "THIN_RIM"),
                "RECTANGLE", List.of("OVERSIZED", "WAYFARER", "AVIATOR", "ROUND_FRAME"),
                "HEART", List.of("AVIATOR", "OVAL_FRAME", "ROUND_FRAME", "THIN_RIM"),
                "DIAMOND", List.of("OVAL_FRAME", "CATEYE", "BROWLINE", "ROUND_FRAME"),
                "TRIANGLE", List.of("BROWLINE", "CATEYE", "OVERSIZED", "GEOMETRIC"));
        Map<String, List<String>> caution = Map.of(
                "ROUND", List.of("ROUND_FRAME"),
                "SQUARE", List.of("SQUARE_FRAME", "GEOMETRIC"),
                "RECTANGLE", List.of("THIN_RIM"),
                "HEART", List.of("BROWLINE"));

        positive.forEach((shapeCode, catCodes) -> {
            var shape = faceShapes.findByCodeIgnoreCase(shapeCode).orElseThrow();
            int p = 10;
            for (String cc : catCodes) {
                var cat = frameCategories.findByCodeIgnoreCase(cc).orElseThrow();
                FaceShapeFrameRecommendation r = new FaceShapeFrameRecommendation();
                r.setFaceShapeId(shape.getId());
                r.setFrameCategoryId(cat.getId());
                r.setStance("POSITIVE");
                r.setPriority(p);
                r.setRecommendationReason("Recommended for " + shape.getDisplayName().toLowerCase() + " faces.");
                recs.save(r);
                p += 10;
            }
        });
        caution.forEach((shapeCode, catCodes) -> {
            var shape = faceShapes.findByCodeIgnoreCase(shapeCode).orElseThrow();
            for (String cc : catCodes) {
                var cat = frameCategories.findByCodeIgnoreCase(cc).orElseThrow();
                FaceShapeFrameRecommendation r = new FaceShapeFrameRecommendation();
                r.setFaceShapeId(shape.getId());
                r.setFrameCategoryId(cat.getId());
                r.setStance("CAUTION");
                r.setPriority(500);
                r.setRecommendationReason("Use carefully — can echo the face's own proportions.");
                recs.save(r);
            }
        });
        log.info("seeded {} recommendation rules", recs.count());
    }

    private void seedCampaigns(CampaignRepository campaigns) {
        campaigns.save(campaign("Facebook September Frames Campaign", Platform.FACEBOOK, "fb-sep-frames",
                "sep_frames_fb", 500.0));
        campaigns.save(campaign("Instagram Reels — Face Finder", Platform.INSTAGRAM, "ig-reels-ff",
                "ig_face_finder", 300.0));
        campaigns.save(campaign("Google Search — Prescription Glasses", Platform.GOOGLE, "g-search-rx",
                "google_rx", 400.0));
        log.info("seeded demo campaigns");
    }

    private Campaign campaign(String name, Platform platform, String extId, String utmCampaign, double budget) {
        Campaign c = new Campaign();
        c.setName(name);
        c.setPlatform(platform);
        c.setExternalCampaignId(extId);
        c.setBudget(budget);
        c.setStatus(CampaignStatus.ACTIVE);
        c.setDestination("WhatsApp");
        c.setUtm(new UtmData(platform.name().toLowerCase(), "cpc", utmCampaign, null, null));
        return c;
    }
}
