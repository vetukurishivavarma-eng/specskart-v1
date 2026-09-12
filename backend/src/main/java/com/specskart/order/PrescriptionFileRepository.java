package com.specskart.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PrescriptionFileRepository extends JpaRepository<PrescriptionFile, UUID> {
}
