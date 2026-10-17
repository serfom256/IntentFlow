package io.github.serfom256.intentflow.examples.shop.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
