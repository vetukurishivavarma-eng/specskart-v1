package com.specskart.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FaceShapeRepository extends JpaRepository<FaceShape, UUID> {
    Optional<FaceShape> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
