package com.company.meetinghelper.seating.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.seating.entity.PlanVersionEntity;
import com.company.meetinghelper.seating.mapper.PlanVersionMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PlanVersionRepository extends AbstractMyBatisRepository<PlanVersionEntity> {
    private final PlanVersionMapper versionMapper;

    public PlanVersionRepository(PlanVersionMapper versionMapper) {
        super(versionMapper);
        this.versionMapper = versionMapper;
    }

    /**
     * 查询排座方案的全部有效版本。
     *
     * @param planId 排座方案ID
     * @return 按版本号倒序排列的版本
     */
    public List<PlanVersionEntity> findAllByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId) {
        return versionMapper.selectList(new LambdaQueryWrapper<PlanVersionEntity>()
                .eq(PlanVersionEntity::getPlanId, planId)
                .orderByDesc(PlanVersionEntity::getVersionNo));
    }

    /**
     * 查询排座方案最新的有效版本。
     *
     * @param planId 排座方案ID
     * @return 最新版本
     */
    public Optional<PlanVersionEntity> findFirstByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId) {
        return Optional.ofNullable(versionMapper.selectOne(new LambdaQueryWrapper<PlanVersionEntity>()
                .eq(PlanVersionEntity::getPlanId, planId)
                .orderByDesc(PlanVersionEntity::getVersionNo)
                .last("limit 1")));
    }

    /**
     * 判断排座方案内是否已存在同名有效版本。
     *
     * @param planId 排座方案ID
     * @param versionName 版本名称
     * @return 存在同名版本时返回true
     */
    public boolean existsByPlanIdAndVersionNameIgnoreCaseAndDeletedFalse(String planId, String versionName) {
        return versionMapper.selectCount(new LambdaQueryWrapper<PlanVersionEntity>()
                .eq(PlanVersionEntity::getPlanId, planId)
                .apply("lower(version_name) = lower({0})", versionName)) > 0;
    }
}
