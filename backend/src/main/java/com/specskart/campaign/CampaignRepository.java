package com.specskart.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Optional<Campaign> findByExternalCampaignId(String externalCampaignId);
    Optional<Campaign> findByUtmUtmCampaignIgnoreCase(String utmCampaign);
}
