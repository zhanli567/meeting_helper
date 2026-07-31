package com.company.meetinghelper.common.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.common.context.CurrentUserHolder;
import com.company.meetinghelper.common.entity.AuditedEntity;
import com.company.meetinghelper.common.security.CurrentUser;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 为领域Repository提供统一的MyBatis-Plus基础持久化与审计能力。
 *
 * @param <T> 审计实体类型
 */
public abstract class AbstractMyBatisRepository<T extends AuditedEntity> {
    private final BaseMapper<T> mapper;

    protected AbstractMyBatisRepository(BaseMapper<T> mapper) {
        this.mapper = mapper;
    }

    /**
     * 新增或更新实体。
     *
     * @param entity 待保存实体
     * @return 已保存实体
     */
    public T save(T entity) {
        if (entity.getId() == null) {
            prepareInsert(entity);
            mapper.insert(entity);
            return entity;
        }
        prepareUpdate(entity);
        mapper.updateById(entity);
        return entity;
    }

    /**
     * 保存实体并保持与原Repository调用契约一致。
     *
     * @param entity 待保存实体
     * @return 已保存实体
     */
    public T saveAndFlush(T entity) {
        return save(entity);
    }

    /**
     * 批量新增或更新实体。
     *
     * @param entities 待保存实体集合
     * @return 已保存实体列表
     */
    public List<T> saveAll(Collection<T> entities) {
        List<T> saved = new ArrayList<>(entities.size());
        for (T entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    /**
     * 按主键查询有效实体。
     *
     * @param id 实体主键
     * @return 匹配实体
     */
    public Optional<T> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /**
     * 删除单个实体。
     *
     * @param entity 待删除实体
     */
    public void delete(T entity) {
        mapper.deleteById(entity.getId());
    }

    /**
     * 批量删除实体。
     *
     * @param entities 待删除实体集合
     */
    public void deleteAll(Collection<T> entities) {
        for (T entity : entities) {
            delete(entity);
        }
    }

    /**
     * 保留原JPA Repository的刷新调用契约；MyBatis写操作会立即发送SQL。
     */
    public void flush() {
        // MyBatis 不维护 JPA 那种持久化上下文。
    }

/**
 * mapper 方法。
 * @return 返回结果。
 */
protected BaseMapper<T> mapper() {
        return mapper;
    }

/**
 * prepareInsert 方法。
 * @param entity entity 参数。
 */
protected void prepareInsert(T entity) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String userId = currentUserId();
        String userName = currentUserName();
        entity.setId(UUID.randomUUID().toString());
        if (entity.getCreatedById() == null) {
            entity.setCreatedById(userId);
        }
        if (entity.getCreatedByName() == null) {
            entity.setCreatedByName(userName);
        }
        entity.setCreatedAt(now);
        if (entity.getUpdatedById() == null) {
            entity.setUpdatedById(entity.getCreatedById());
        }
        if (entity.getUpdatedByName() == null) {
            entity.setUpdatedByName(entity.getCreatedByName());
        }
        entity.setUpdatedAt(now);
        entity.setRowVersion(0L);
    }

/**
 * prepareUpdate 方法。
 * @param entity entity 参数。
 */
protected void prepareUpdate(T entity) {
        entity.setUpdatedById(currentUserId());
        entity.setUpdatedByName(currentUserName());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String currentUserId() {
        CurrentUser user = CurrentUserHolder.get();
        return user == null ? "" : Objects.toString(user.userId(), "");
    }

    private String currentUserName() {
        CurrentUser user = CurrentUserHolder.get();
        return user == null ? "" : Objects.toString(user.displayName(), "");
    }
}
