package com.company.meetinghelper.venue.repository;

import com.company.meetinghelper.venue.entity.VenueElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueElementRepository extends JpaRepository<VenueElementEntity, String> {
    List<VenueElementEntity> findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(String venueTemplateId);
}
