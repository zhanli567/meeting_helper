package com.company.meetinghelper.venue.repository;

import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueTemplateRepository extends JpaRepository<VenueTemplateEntity, String> {
    List<VenueTemplateEntity> findAllByDeletedFalseOrderByPresetDescNameAsc();

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, String id);
}
