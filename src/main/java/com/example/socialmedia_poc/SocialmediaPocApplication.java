package com.example.socialmedia_poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SocialmediaPocApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocialmediaPocApplication.class, args);
	}

}
