package com.company.meetinghelper.venue.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import com.company.meetinghelper.venue.mapper.VenueTemplateMapper;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

/**
 * Represents the venue template repository class.
 */
@Repository
public class VenueTemplateRepository extends AbstractMyBatisRepository<VenueTemplateEntity> {
    private final VenueTemplateMapper templateMapper;

    /**
     * 创建场馆模板仓储。
     *
     * @param templateMapper 场馆模板映射器
     */
    public VenueTemplateRepository(VenueTemplateMapper templateMapper) {
        super(templateMapper);
        this.templateMapper = templateMapper;
    }

    /**
     * 分页检索全局场馆模板。
     *
     * @param keyword 全字段搜索关键字
     * @param campus 校区筛选值
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 场馆模板分页结果
     */
    public Page<VenueTemplateEntity> findPage(
            String keyword,
            String campus,
            int pageNum,
            int pageSize
    ) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedCampus = campus == null ? "" : campus.trim();
        LambdaQueryWrapper<VenueTemplateEntity> query =
                new LambdaQueryWrapper<VenueTemplateEntity>();
        if (!normalizedKeyword.isEmpty()) {
            query.and(wrapper -> wrapper
                    .like(VenueTemplateEntity::getLocation, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getCampus, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getMainScreenResolution, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getStageDimensions, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getContactInfo, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getBookingUrl, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getMeetingRoomFunctions, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getServicesProvided, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getDescription, normalizedKeyword)
                    .or().like(VenueTemplateEntity::getRemarks, normalizedKeyword));
        }
        if (!normalizedCampus.isEmpty()) {
            query.eq(VenueTemplateEntity::getCampus, normalizedCampus);
        }
        query.orderByDesc(VenueTemplateEntity::getUpdatedAt)
                .orderByAsc(VenueTemplateEntity::getLocation);
        return templateMapper.selectPage(
                new Page<VenueTemplateEntity>(pageNum, pageSize),
                query
        );
    }

    /**
     * 判断规范化地点是否已存在。
     *
     * @param locationKey 规范化地点
     * @return 已存在时返回true
     */
    public boolean existsByLocationKey(String locationKey) {
        return templateMapper.selectCount(new LambdaQueryWrapper<VenueTemplateEntity>()
                .eq(VenueTemplateEntity::getLocationKey, locationKey)) > 0;
    }

    /**
     * 判断除指定模板外规范化地点是否已存在。
     *
     * @param locationKey 规范化地点
     * @param id 排除的场馆模板ID
     * @return 其他模板已使用地点时返回true
     */
    public boolean existsByLocationKeyAndIdNot(String locationKey, String id) {
        return templateMapper.selectCount(new LambdaQueryWrapper<VenueTemplateEntity>()
                .eq(VenueTemplateEntity::getLocationKey, locationKey)
                .ne(VenueTemplateEntity::getId, id)) > 0;
    }

    /**
     * 在当前事务内锁定场馆模板行。
     *
     * @param id 场馆模板ID
     * @return 已加悲观写锁的场馆模板
     */
    public Optional<VenueTemplateEntity> findByIdForUpdate(String id) {
        return Optional.ofNullable(templateMapper.selectByIdForUpdate(id));
    }

    /**
     * 按乐观锁版本更新场馆固定信息。
     *
     * @param template 待更新模板
     * @param expectedVersion 期望版本
     * @return 更新后的模板
     */
    public VenueTemplateEntity updateInfoWithVersion(
            VenueTemplateEntity template,
            long expectedVersion
    ) {
        prepareUpdate(template);
        if (templateMapper.updateInfoWithVersion(template, expectedVersion) == 0) {
            throw staleConflict();
        }
        template.setRowVersion(expectedVersion + 1);
        return template;
    }

    /**
     * 按乐观锁版本更新场馆布局摘要。
     *
     * @param template 待更新模板
     * @param expectedVersion 期望版本
     * @return 更新后的模板
     */
    public VenueTemplateEntity updateLayoutWithVersion(
            VenueTemplateEntity template,
            long expectedVersion
    ) {
        prepareUpdate(template);
        if (templateMapper.updateLayoutWithVersion(template, expectedVersion) == 0) {
            throw staleConflict();
        }
        template.setRowVersion(expectedVersion + 1);
        return template;
    }

    private ApiException staleConflict() {
        return new ApiException(HttpStatus.CONFLICT, "场馆模板已被其他用户修改，请刷新后重试");
    }
}
