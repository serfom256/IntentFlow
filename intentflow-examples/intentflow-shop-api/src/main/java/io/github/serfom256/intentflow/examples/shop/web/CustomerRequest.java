package io.github.serfom256.intentflow.examples.shop.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be a two-letter country code") String country,
        boolean vip) {
}
