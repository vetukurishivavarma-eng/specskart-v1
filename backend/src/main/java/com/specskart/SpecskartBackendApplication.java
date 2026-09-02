package com.specskart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class SpecskartBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpecskartBackendApplication.class, args);
    }
}
