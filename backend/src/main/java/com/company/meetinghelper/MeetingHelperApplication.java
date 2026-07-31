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
/**
 * MeetingHelperApplication 类。
 */
public class MeetingHelperApplication {

/**
 * main 方法。
 * @param args args 参数。
 */
public static void main(String[] args) {
		SpringApplication.run(MeetingHelperApplication.class, args);
	}

}
