package com.specskart.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SentMessageRepository extends JpaRepository<SentMessage, String> {
}
