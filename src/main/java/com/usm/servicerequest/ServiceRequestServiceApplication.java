package com.usm.servicerequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServiceRequestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRequestServiceApplication.class, args);
    }
}
