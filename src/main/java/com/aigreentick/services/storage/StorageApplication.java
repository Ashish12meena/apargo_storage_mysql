package com.aigreentick.services.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class StorageApplication {	
	public static void main(String[] args) {	
		SpringApplication.run(StorageApplication.class, args);
	}
}
