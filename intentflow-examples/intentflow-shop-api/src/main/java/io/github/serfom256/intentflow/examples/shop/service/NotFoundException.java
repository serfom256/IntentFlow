package io.github.serfom256.intentflow.examples.shop.service;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {

    private final String entity;
    private final long id;

    public NotFoundException(String entity, long id) {
        super(entity + " " + id + " not found");
        this.entity = entity;
        this.id = id;
    }
}
