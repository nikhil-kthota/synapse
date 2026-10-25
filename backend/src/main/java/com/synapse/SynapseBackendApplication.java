package com.synapse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SynapseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SynapseBackendApplication.class, args);
	}

}
