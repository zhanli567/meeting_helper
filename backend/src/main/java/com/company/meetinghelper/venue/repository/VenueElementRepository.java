package com.company.meetinghelper.venue.repository;

import com.company.meetinghelper.venue.entity.VenueElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueElementRepository extends JpaRepository<VenueElementEntity, String> {
    /**
     * 查询场馆模板中的全部有效元素。
     *
     * @param venueTemplateId 场馆模板ID
     * @return 按网格位置排列的场馆元素
     */
    List<VenueElementEntity> findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(String venueTemplateId);
}
