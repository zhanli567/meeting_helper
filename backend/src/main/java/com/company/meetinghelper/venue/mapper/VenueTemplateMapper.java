package com.company.meetinghelper.venue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface VenueTemplateMapper extends BaseMapper<VenueTemplateEntity> {
    @Select("""
            select *
            from t_venue_templates
            where id = #{id}
            for update
            """)
    VenueTemplateEntity selectByIdForUpdate(@Param("id") String id);

    @Update("""
            update t_venue_templates
            set location = #{template.location},
                location_key = #{template.locationKey},
                campus = #{template.campus},
                main_screen_resolution = #{template.mainScreenResolution},
                stage_dimensions = #{template.stageDimensions},
                manual_capacity = #{template.manualCapacity},
                contact_info = #{template.contactInfo},
                booking_url = #{template.bookingUrl},
                meeting_room_functions = #{template.meetingRoomFunctions},
                services_provided = #{template.servicesProvided},
                description = #{template.description},
                remarks = #{template.remarks},
                updated_by_id = #{template.updatedById},
                updated_by_name = #{template.updatedByName},
                updated_at = #{template.updatedAt},
                row_version = row_version + 1
            where id = #{template.id} and row_version = #{expectedVersion}
            """)
    int updateInfoWithVersion(
            @Param("template") VenueTemplateEntity template,
            @Param("expectedVersion") long expectedVersion
    );

    @Update("""
            update t_venue_templates
            set grid_rows = #{template.gridRows},
                grid_columns = #{template.gridColumns},
                seat_count = #{template.seatCount},
                updated_by_id = #{template.updatedById},
                updated_by_name = #{template.updatedByName},
                updated_at = #{template.updatedAt},
                row_version = row_version + 1
            where id = #{template.id} and row_version = #{expectedVersion}
            """)
    int updateLayoutWithVersion(
            @Param("template") VenueTemplateEntity template,
            @Param("expectedVersion") long expectedVersion
    );
}
