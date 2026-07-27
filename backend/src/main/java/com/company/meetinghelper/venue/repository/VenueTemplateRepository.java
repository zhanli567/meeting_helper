package com.company.meetinghelper.venue.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import com.company.meetinghelper.venue.mapper.VenueTemplateMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class VenueTemplateRepository extends AbstractMyBatisRepository<VenueTemplateEntity> {
    private final VenueTemplateMapper templateMapper;

    public VenueTemplateRepository(VenueTemplateMapper templateMapper) {
        super(templateMapper);
        this.templateMapper = templateMapper;
    }

    /**
     * 查询全部有效场馆模板。
     *
     * @return 系统预置优先、名称升序排列的场馆模板
     */
    public List<VenueTemplateEntity> findAllOrderByPresetDescNameAsc() {
        return templateMapper.selectList(new LambdaQueryWrapper<VenueTemplateEntity>()
                .orderByDesc(VenueTemplateEntity::isPreset)
                .orderByAsc(VenueTemplateEntity::getName));
    }

    /**
     * 判断有效场馆模板名称是否已存在。
     *
     * @param name 场馆名称
     * @return 名称已存在时返回true
     */
    public boolean existsByNameIgnoreCase(String name) {
        return templateMapper.selectCount(new LambdaQueryWrapper<VenueTemplateEntity>()
                .apply("lower(name) = lower({0})", name)) > 0;
    }

    /**
     * 判断除指定模板外是否存在同名有效场馆。
     *
     * @param name 场馆名称
     * @param id 排除的场馆模板ID
     * @return 其他同名模板存在时返回true
     */
    public boolean existsByNameIgnoreCaseAndIdNot(String name, String id) {
        return templateMapper.selectCount(new LambdaQueryWrapper<VenueTemplateEntity>()
                .ne(VenueTemplateEntity::getId, id)
                .apply("lower(name) = lower({0})", name)) > 0;
    }
}
