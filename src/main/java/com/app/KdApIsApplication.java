package com.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com")
public class KdApIsApplication {

	public static void main(String[] args) {
		SpringApplication.run(KdApIsApplication.class, args);
	}

}
