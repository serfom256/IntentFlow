package io.github.serfom256.intentflow.examples.shop.web;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.search.SearchResult;
import io.github.serfom256.intentflow.examples.shop.search.SearchService;
import io.github.serfom256.intentflow.examples.shop.service.CustomerService;
import io.github.serfom256.intentflow.examples.shop.service.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final SearchService searchService;

    @GetMapping
    public PageResponse<Customer> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return customerService.list(page, size);
    }

    @GetMapping("/search")
    public SearchResult<?> search(@RequestParam("q") @NotBlank @Size(max = 500) String query,
            @RequestParam(defaultValue = "false") boolean debug) {
        return searchService.search(query, "customers", debug);
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable long id) {
        return customerService.get(id);
    }

    @PostMapping
    public ResponseEntity<Customer> create(@Valid @RequestBody CustomerRequest request) {
        Customer created = customerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public Customer update(@PathVariable long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
