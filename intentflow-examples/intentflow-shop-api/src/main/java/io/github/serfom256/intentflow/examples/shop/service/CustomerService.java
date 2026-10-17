package io.github.serfom256.intentflow.examples.shop.service;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.intentflow.examples.shop.web.CustomerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CrudStore<Customer> customers;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<Customer> list(int page, int size) {
        return PageResponse.of(customers.findPage(page, size), page, size, customers.count());
    }

    @Transactional(readOnly = true)
    public Customer get(long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    @Transactional(readOnly = true)
    public boolean exists(long id) {
        return customers.exists(id);
    }

    @Transactional
    public Customer create(CustomerRequest request) {
        Customer customer = apply(Customer.builder(), request).createdAt(OffsetDateTime.now(clock)).build();
        return get(customers.insert(customer));
    }

    @Transactional
    public Customer update(long id, CustomerRequest request) {
        customers.update(apply(get(id).toBuilder(), request).build());
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (!customers.delete(id)) {
            throw new NotFoundException("Customer", id);
        }
    }

    private static Customer.CustomerBuilder apply(Customer.CustomerBuilder b, CustomerRequest r) {
        return b.fullName(r.name().strip())
                .email(r.email().strip().toLowerCase(Locale.ROOT))
                .city(r.city().strip())
                .country(r.country().toUpperCase(Locale.ROOT))
                .vip(r.vip());
    }
}
