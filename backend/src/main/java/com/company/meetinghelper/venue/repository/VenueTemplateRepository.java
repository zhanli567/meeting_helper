package com.company.meetinghelper.venue.repository;

import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueTemplateRepository extends JpaRepository<VenueTemplateEntity, String> {
    /**
     * 查询全部有效场馆模板。
     *
     * @return 系统预置优先、名称升序排列的场馆模板
     */
    List<VenueTemplateEntity> findAllByDeletedFalseOrderByPresetDescNameAsc();

    /**
     * 判断有效场馆模板名称是否已存在。
     *
     * @param name 场馆名称
     * @return 名称已存在时返回true
     */
    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    /**
     * 判断除指定模板外是否存在同名有效场馆。
     *
     * @param name 场馆名称
     * @param id 排除的场馆模板ID
     * @return 其他同名模板存在时返回true
     */
    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, String id);
}
