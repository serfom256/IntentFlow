package io.github.serfom256.intentflow.examples.shop.web;

import io.github.serfom256.intentflow.examples.shop.search.SearchResult;
import io.github.serfom256.intentflow.examples.shop.search.SearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public SearchResult<?> search(@RequestParam("q") @NotBlank @Size(max = 500) String query,
            @RequestParam(defaultValue = "false") boolean debug) {
        return searchService.search(query, debug);
    }
}
