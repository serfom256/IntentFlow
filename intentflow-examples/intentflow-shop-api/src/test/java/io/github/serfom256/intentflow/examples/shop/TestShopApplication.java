package io.github.serfom256.intentflow.examples.shop;

import org.springframework.boot.SpringApplication;

public class TestShopApplication {

    public static void main(String[] args) {
        SpringApplication.from(ShopApplication::main).with(PostgresContainerConfig.class).run(args);
    }
}
