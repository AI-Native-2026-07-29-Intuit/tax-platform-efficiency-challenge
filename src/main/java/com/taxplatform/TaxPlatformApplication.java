package com.taxplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/** Tax Calculation Platform. */
@SpringBootApplication
@EnableCaching
public class TaxPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxPlatformApplication.class, args);
    }
}
