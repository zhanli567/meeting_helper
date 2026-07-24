package com.company.meetinghelper.venue.preset;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PresetVenueStore {
    private final Map<String, PresetVenueDefinition> definitions;

    /**
     * 加载并校验代码预置场馆目录。
     */
    public PresetVenueStore() {
        definitions = PresetVenueCatalog.definitions().stream()
                .collect(Collectors.toMap(
                        PresetVenueDefinition::id,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("预置场馆ID重复：" + left.id());
                        },
                        LinkedHashMap::new
                ));
    }

    /**
     * 查询全部由代码维护的预置场馆。
     *
     * @return 不可变的预置场馆列表
     */
    public List<PresetVenueDefinition> findAll() {
        return List.copyOf(definitions.values());
    }

    /**
     * 根据稳定标识查询预置场馆。
     *
     * @param id 预置场馆标识
     * @return 预置场馆，不存在时返回空
     */
    public Optional<PresetVenueDefinition> findById(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    /**
     * 判断名称是否与任一预置场馆重复。
     *
     * @param name 待检查名称
     * @return 重名时返回true
     */
    public boolean existsByNameIgnoreCase(String name) {
        return definitions.values().stream().anyMatch(value -> value.name().equalsIgnoreCase(name));
    }
}
