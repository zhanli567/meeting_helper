package com.company.meetinghelper.venue.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.venue.entity.VenueElementEntity;
import com.company.meetinghelper.venue.mapper.VenueElementMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class VenueElementRepository extends AbstractMyBatisRepository<VenueElementEntity> {
    private final VenueElementMapper elementMapper;

    public VenueElementRepository(VenueElementMapper elementMapper) {
        super(elementMapper);
        this.elementMapper = elementMapper;
    }

    /**
     * 查询场馆模板中的全部有效元素。
     *
     * @param venueTemplateId 场馆模板ID
     * @return 按网格位置排列的场馆元素
     */
    public List<VenueElementEntity> findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(
            String venueTemplateId
    ) {
        return elementMapper.selectList(new LambdaQueryWrapper<VenueElementEntity>()
                .eq(VenueElementEntity::getVenueTemplateId, venueTemplateId)
                .orderByAsc(VenueElementEntity::getGridRow)
                .orderByAsc(VenueElementEntity::getGridColumn));
    }
}
