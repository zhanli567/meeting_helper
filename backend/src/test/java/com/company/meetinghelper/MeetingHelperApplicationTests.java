package com.company.meetinghelper;

import com.company.meetinghelper.support.PostgreSqlTestDatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = PostgreSqlTestDatabaseInitializer.class)
class MeetingHelperApplicationTests {

	@Test
	void contextLoads() {
	}

}
