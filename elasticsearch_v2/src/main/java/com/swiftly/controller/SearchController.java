package com.swiftly.controller;

import com.swiftly.model.Product;
import com.swiftly.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class SearchController {

    private final SearchService searchService;

    // Add product
    @PostMapping("/save_product")
    public Product addProduct(@RequestBody Product p) {
        return searchService.addProduct(p);
    }

    // 1. Simple search
    @GetMapping("/search")
    public Map<String, Object> simpleSearch(@RequestParam String keyword) {
        return searchService.simpleSearch(keyword);
    }

    // 2. Faceted search (supports multiple brand & color via repeated params)
    @GetMapping("/faceted-search")
    public Map<String, Object> facetedSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) List<String> color,
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) Double ratingMin,
            @RequestParam(required = false) Double ratingMax
    ) {
        return searchService.facetedSearch(keyword, category, brand, color, priceMin, priceMax, ratingMin, ratingMax);
    }

    // 3. Synonym search
    @GetMapping("/synonym-search")
    public Map<String, Object> synonymSearch(@RequestParam String keyword) {
        return searchService.synonymSearch(keyword);
    }

    // 4. Enhanced search with pagination
    @GetMapping("/enhanced-search")
    public Map<String, Object> enhancedSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> brands,
            @RequestParam(required = false) List<String> colors,
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) Double ratingMin,
            @RequestParam(required = false) Double ratingMax,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return searchService.enhancedSearch(keyword, category, brands, colors, priceMin, priceMax, ratingMin, ratingMax, limit, offset);
    }
}
