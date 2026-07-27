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

    /**
     * 创建场馆元素仓储。
     *
     * @param elementMapper 场馆元素映射器
     */
    public VenueElementRepository(VenueElementMapper elementMapper) {
        super(elementMapper);
        this.elementMapper = elementMapper;
    }

    /**
     * 查询场馆模板的全部元素。
     *
     * @param venueTemplateId 场馆模板ID
     * @return 按起始位置排序的元素
     */
    public List<VenueElementEntity> findAllByVenueTemplateIdOrderByStartRowAscStartColumnAsc(
            String venueTemplateId
    ) {
        return elementMapper.selectList(new LambdaQueryWrapper<VenueElementEntity>()
                .eq(VenueElementEntity::getVenueTemplateId, venueTemplateId)
                .orderByAsc(VenueElementEntity::getStartRow)
                .orderByAsc(VenueElementEntity::getStartColumn));
    }

    /**
     * 使用单条批量语句新增场馆元素。
     *
     * @param elements 待新增的场馆元素
     */
    public void saveBatch(List<VenueElementEntity> elements) {
        if (elements.isEmpty()) {
            return;
        }
        for (VenueElementEntity element : elements) {
            prepareInsert(element);
        }
        elementMapper.insertBatch(elements);
    }

    /**
     * 物理删除场馆模板的全部元素。
     *
     * @param venueTemplateId 场馆模板ID
     */
    public void deleteAllByVenueTemplateId(String venueTemplateId) {
        elementMapper.delete(new LambdaQueryWrapper<VenueElementEntity>()
                .eq(VenueElementEntity::getVenueTemplateId, venueTemplateId));
    }
}
