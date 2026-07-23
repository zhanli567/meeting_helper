package com.company.meetinghelper.venue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueTemplateRepository extends JpaRepository<VenueTemplateEntity, String> {
    List<VenueTemplateEntity> findAllByDeletedFalseOrderByPresetDescNameAsc();

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, String id);
}
