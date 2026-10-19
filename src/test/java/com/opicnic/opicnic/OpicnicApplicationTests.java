package com.opicnic.opicnic;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 로컬 MySQL(localhost:3306)이나 실제 API 키가 있어야 도는 테스트. CI에서는 제외(build.gradle)
@Tag("local")
@SpringBootTest
class OpicnicApplicationTests {

	@Test
	void contextLoads() {
	}

}
