package com.yuriytkach.demo.stream28.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.yuriytkach.demo.stream28.app.config.ApiProperties;

@SpringBootApplication
@EnableConfigurationProperties({ ApiProperties.class })
public class Stream28DemoAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(Stream28DemoAppApplication.class, args);
	}

}
