package com.company.meetinghelper.venue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueElementRepository extends JpaRepository<VenueElementEntity, String> {
    List<VenueElementEntity> findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(String venueTemplateId);
}

