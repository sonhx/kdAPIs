package com.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com")
@EnableScheduling
public class KdApIsApplication {

	public static void main(String[] args) {
		SpringApplication.run(KdApIsApplication.class, args);
	}

}
