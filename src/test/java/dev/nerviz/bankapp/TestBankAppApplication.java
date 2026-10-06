package dev.nerviz.bankapp;

import org.springframework.boot.SpringApplication;

public class TestBankAppApplication {

    public static void main(String[] args) {
        SpringApplication.from(BankAppApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
