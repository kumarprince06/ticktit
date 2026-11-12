package com.ticktit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync   // lets @Async methods (e.g. EmailService) run in the background
public class TicktitBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicktitBackendApplication.class, args);
	}

}
