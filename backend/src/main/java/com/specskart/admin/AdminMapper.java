package com.specskart.admin;

import com.specskart.campaign.Campaign;
import com.specskart.lead.Lead;

import java.util.LinkedHashMap;
import java.util.Map;

final class AdminMapper {
    private AdminMapper() {}

    static AdminDtos.LeadRow row(Lead l, String campaignName) {
        return new AdminDtos.LeadRow(l.getId(), l.getName(), l.getWhatsappNumber(),
                l.getAcquisitionSource().name(), l.getCampaignId(), campaignName, l.getFaceShape(),
                l.getRecommendedFrameCategories(), l.getStatus().name(), l.getCreatedAt(),
                l.getLastContactAt(), l.getAssignedToUserId());
    }

    static Map<String, Object> attribution(Lead l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", l.getAcquisitionSource().name());
        m.put("adId", l.getAdId());
        m.put("adSetId", l.getAdSetId());
        m.put("clickId", l.getClickId());
        m.put("referrer", l.getReferrer());
        m.put("landingPage", l.getLandingPage());
        m.put("utmSource", l.getUtm() == null ? null : l.getUtm().utmSource());
        m.put("utmMedium", l.getUtm() == null ? null : l.getUtm().utmMedium());
        m.put("utmCampaign", l.getUtm() == null ? null : l.getUtm().utmCampaign());
        m.put("utmContent", l.getUtm() == null ? null : l.getUtm().utmContent());
        m.put("utmTerm", l.getUtm() == null ? null : l.getUtm().utmTerm());
        m.put("providerMetadata", l.getProviderMetadata());
        return m;
    }

    static AdminDtos.CampaignDto campaign(Campaign c) {
        Map<String, String> utm = new LinkedHashMap<>();
        if (c.getUtm() != null) {
            utm.put("source", c.getUtm().utmSource());
            utm.put("medium", c.getUtm().utmMedium());
            utm.put("campaign", c.getUtm().utmCampaign());
            utm.put("content", c.getUtm().utmContent());
            utm.put("term", c.getUtm().utmTerm());
        }
        return new AdminDtos.CampaignDto(c.getId(), c.getName(), c.getPlatform().name(),
                c.getExternalCampaignId(), c.getStatus().name(), c.getBudget(), c.getDestination(),
                utm, c.getMetadata(),
                c.getStartDate() == null ? null : c.getStartDate().toString(),
                c.getEndDate() == null ? null : c.getEndDate().toString());
    }
}
