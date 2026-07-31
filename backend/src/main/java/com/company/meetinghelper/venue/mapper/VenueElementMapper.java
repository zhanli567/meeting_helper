package com.company.meetinghelper.venue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.venue.entity.VenueElementEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * VenueElementMapper 接口。
 */
public interface VenueElementMapper extends BaseMapper<VenueElementEntity> {
    @Insert({
            "<script>",
            "insert into t_venue_elements (",
            "id, venue_template_id, element_kind, element_name, start_row, start_column,",
            "row_span, column_span, fill_color,",
            "created_by_id, created_by_name, created_at,",
            "updated_by_id, updated_by_name, updated_at, row_version",
            ") values",
            "<foreach collection='entities' item='item' separator=','>",
            "(",
            "#{item.id}, #{item.venueTemplateId}, #{item.elementKind},",
            "#{item.elementName}, #{item.startRow}, #{item.startColumn},",
            "#{item.rowSpan}, #{item.columnSpan}, #{item.fillColor},",
            "#{item.createdById}, #{item.createdByName}, #{item.createdAt},",
            "#{item.updatedById}, #{item.updatedByName}, #{item.updatedAt},",
            "#{item.rowVersion}",
            ")",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("entities") List<VenueElementEntity> entities);
}
