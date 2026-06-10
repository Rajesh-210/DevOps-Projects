package com.example.twitterapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "management.metrics.binders.processor.enabled=false"
})
class TwitterAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
