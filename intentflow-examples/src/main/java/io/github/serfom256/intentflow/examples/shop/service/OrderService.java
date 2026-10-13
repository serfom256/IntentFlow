package io.github.serfom256.intentflow.examples.shop.service;

import io.github.serfom256.intentflow.examples.shop.dao.OrderDao;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.web.OrderRequest;
import io.github.serfom256.model.page.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderDao orders;
    private final CustomerService customers;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<Order> list(int page, int size) {
        return PageResponse.of(orders.findPage(PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public Order get(long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
    }

    @Transactional
    public Order create(OrderRequest request) {
        requireCustomer(request.customerId());
        Order order = apply(Order.builder(), request).createdAt(OffsetDateTime.now(clock)).build();
        return get(orders.insert(order));
    }

    @Transactional
    public Order update(long id, OrderRequest request) {
        Order existing = get(id);
        requireCustomer(request.customerId());
        orders.update(apply(existing.toBuilder(), request).build());
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (orders.delete(id) == 0) {
            throw new NotFoundException("Order", id);
        }
    }

    private void requireCustomer(long customerId) {
        if (!customers.exists(customerId)) {
            throw new NotFoundException("Customer", customerId);
        }
    }

    private static Order.OrderBuilder apply(Order.OrderBuilder b, OrderRequest r) {
        return b.customerId(r.customerId())
                .status(r.status())
                .total(r.total())
                .itemsCount(r.itemsCount());
    }
}
