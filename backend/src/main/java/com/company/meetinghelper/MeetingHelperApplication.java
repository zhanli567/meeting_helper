package com.company.meetinghelper;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
		"com.company.meetinghelper.venue.mapper",
		"com.company.meetinghelper.meeting.mapper",
		"com.company.meetinghelper.participant.mapper",
		"com.company.meetinghelper.seating.mapper"
})
public class MeetingHelperApplication {

	public static void main(String[] args) {
		SpringApplication.run(MeetingHelperApplication.class, args);
	}

}
