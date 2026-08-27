package com.codaxistech.argus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // heartbeat do SSE
public class ArgusApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArgusApplication.class, args);
	}

}
