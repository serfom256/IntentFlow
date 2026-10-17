package io.github.serfom256.intentflow.examples.shop.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
}
