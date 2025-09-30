package com.swiftly.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import com.swiftly.model.Product;
import com.swiftly.repository.ElasticSearchRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticSearchRepository repo;
    private final ElasticsearchOperations elasticOps;

    // Add / save product
    public Product addProduct(Product product) {
        return repo.save(product);
    }

    // Simple search: name (basic)
    public Map<String, Object> simpleSearch(String keyword) {
        Criteria criteria = new Criteria("name").matches(keyword)
                .or(new Criteria("synonyms").matches(keyword));
        CriteriaQuery q = new CriteriaQuery(criteria).setPageable(PageRequest.of(0, 50));
        SearchHits<Product> hits = elasticOps.search(q, Product.class);
        return buildPagedResponse(hits, Collections.emptyMap());
    }

    // Synonym search: name OR synonyms
    public Map<String, Object> synonymSearch(String keyword) {
        Criteria criteria = new Criteria("name").matches(keyword)
                .or(new Criteria("synonyms").matches(keyword));
        // compute facets from top 1000 matches, return first page 50
        return searchAndBuildWithFacets(criteria, 50, 0, 1000);
    }

    // Faceted search: accepts multiple brands/colors + ranges; returns products + facets
    public Map<String, Object> facetedSearch(String keyword,
                                             String category,
                                             List<String> brands,
                                             List<String> colors,
                                             Double priceMin,
                                             Double priceMax,
                                             Double ratingMin,
                                             Double ratingMax) {

        Criteria criteria = new Criteria();

        if (keyword != null && !keyword.isEmpty()) {
            criteria = criteria.and(
                    new Criteria("name").matches(keyword)
                            .or(new Criteria("description").matches(keyword))
                            .or(new Criteria("synonyms").matches(keyword))
            );
        }
        if (category != null && !category.isEmpty()) criteria = criteria.and(new Criteria("category").is(category));
        if (brands != null && !brands.isEmpty()) criteria = criteria.and(new Criteria("brand").in(brands));
        if (colors != null && !colors.isEmpty()) criteria = criteria.and(new Criteria("color").in(colors));
        if (priceMin != null || priceMax != null) {
            Criteria price = new Criteria("price");
            if (priceMin != null) price = price.greaterThanEqual(priceMin);
            if (priceMax != null) price = price.lessThanEqual(priceMax);
            criteria = criteria.and(price);
        }
        if (ratingMin != null || ratingMax != null) {
            Criteria rating = new Criteria("rating");
            if (ratingMin != null) rating = rating.greaterThanEqual(ratingMin);
            if (ratingMax != null) rating = rating.lessThanEqual(ratingMax);
            criteria = criteria.and(rating);
        }

        // compute facets over up to 1000 results, return first 50 by default
        return searchAndBuildWithFacets(criteria, 50, 0, 1000);
    }

    // Enhanced (best) search combining all: supports brands/colors lists + ranges + pagination
    public Map<String, Object> enhancedSearch(String keyword,
                                              String category,
                                              List<String> brands,
                                              List<String> colors,
                                              Double priceMin,
                                              Double priceMax,
                                              Double ratingMin,
                                              Double ratingMax,
                                              int limit,
                                              int offset) {
        Criteria criteria = new Criteria();

        if (keyword != null && !keyword.isEmpty()) {
            criteria = criteria.and(
                    new Criteria("name").matches(keyword)
                            .or(new Criteria("description").matches(keyword))
                            .or(new Criteria("synonyms").matches(keyword))
            );
        }
        if (category != null && !category.isEmpty()) criteria = criteria.and(new Criteria("category").is(category));
        if (brands != null && !brands.isEmpty()) criteria = criteria.and(new Criteria("brand").in(brands));
        if (colors != null && !colors.isEmpty()) criteria = criteria.and(new Criteria("color").in(colors));
        if (priceMin != null || priceMax != null) {
            Criteria price = new Criteria("price");
            if (priceMin != null) price = price.greaterThanEqual(priceMin);
            if (priceMax != null) price = price.lessThanEqual(priceMax);
            criteria = criteria.and(price);
        }
        if (ratingMin != null || ratingMax != null) {
            Criteria rating = new Criteria("rating");
            if (ratingMin != null) rating = rating.greaterThanEqual(ratingMin);
            if (ratingMax != null) rating = rating.lessThanEqual(ratingMax);
            criteria = criteria.and(rating);
        }

        // compute facets over up to 1000 matches, return requested page
        return searchAndBuildWithFacets(criteria, limit, offset, 1000);
    }

    // -------------------------
    // Helper: run the query twice:
    //  - once to fetch facet candidates (up to facetLimit)
    //  - once to fetch page (limit/offset)
    // -------------------------
    private Map<String, Object> searchAndBuildWithFacets(Criteria criteria, int limit, int offset, int facetLimit) {
        CriteriaQuery facetQuery = new CriteriaQuery(criteria).setPageable(PageRequest.of(0, Math.min(1000, facetLimit)));
        SearchHits<Product> facetHits = elasticOps.search(facetQuery, Product.class);
        List<Product> facetProducts = facetHits.stream().map(SearchHit::getContent).toList();

        CriteriaQuery pageQuery = new CriteriaQuery(criteria).setPageable(PageRequest.of(offset, limit));
        SearchHits<Product> pageHits = elasticOps.search(pageQuery, Product.class);
        List<Product> pageProducts = pageHits.stream().map(SearchHit::getContent).toList();

        Map<String, Object> facets = computeFacets(facetProducts);

        Map<String, Object> response = new HashMap<>();
        response.put("total", pageHits.getTotalHits());
        response.put("products", pageProducts);
        response.put("facets", facets);
        return response;
    }

    // Basic simple response builder for other queries
    private Map<String, Object> buildPagedResponse(SearchHits<Product> hits, Map<String, Object> facets) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("total", hits.getTotalHits());
        resp.put("products", hits.stream().map(SearchHit::getContent).toList());
        resp.put("facets", facets);
        return resp;
    }

    // Compute facet counts for brand, color, category, price ranges, rating ranges
    private Map<String, Object> computeFacets(List<Product> products) {
        Map<String, Long> brandCounts = products.stream()
                .filter(p -> p.getBrand() != null)
                .collect(Collectors.groupingBy(Product::getBrand, Collectors.counting()));

        Map<String, Long> colorCounts = products.stream()
                .filter(p -> p.getColor() != null)
                .collect(Collectors.groupingBy(Product::getColor, Collectors.counting()));

        Map<String, Long> categoryCounts = products.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));

        // price buckets
        Map<String, Long> priceBuckets = new LinkedHashMap<>();
        priceBuckets.put("<200", 0L);
        priceBuckets.put("200-499", 0L);
        priceBuckets.put("500-999", 0L);
        priceBuckets.put("1000-1999", 0L);
        priceBuckets.put(">=2000", 0L);

        for (Product p : products) {
            Double price = p.getPrice();
            if (price == null) continue;
            if (price < 200) priceBuckets.compute("<200", (k,v) -> v+1);
            else if (price < 500) priceBuckets.compute("200-499", (k,v) -> v+1);
            else if (price < 1000) priceBuckets.compute("500-999", (k,v) -> v+1);
            else if (price < 2000) priceBuckets.compute("1000-1999", (k,v) -> v+1);
            else priceBuckets.compute(">=2000", (k,v) -> v+1);
        }

        // rating buckets
        Map<String, Long> ratingBuckets = new LinkedHashMap<>();
        ratingBuckets.put("1.0-2.0", 0L);
        ratingBuckets.put("2.1-3.0", 0L);
        ratingBuckets.put("3.1-4.0", 0L);
        ratingBuckets.put("4.1-5.0", 0L);

        for (Product p : products) {
            Double r = p.getRating();
            if (r == null) continue;
            if (r <= 2.0) ratingBuckets.compute("1.0-2.0", (k,v) -> v+1);
            else if (r <= 3.0) ratingBuckets.compute("2.1-3.0", (k,v) -> v+1);
            else if (r <= 4.0) ratingBuckets.compute("3.1-4.0", (k,v) -> v+1);
            else ratingBuckets.compute("4.1-5.0", (k,v) -> v+1);
        }

        Map<String, Object> facets = new HashMap<>();
        facets.put("brand", brandCounts);
        facets.put("color", colorCounts);
        facets.put("category", categoryCounts);
        facets.put("price_ranges", priceBuckets);
        facets.put("rating_ranges", ratingBuckets);
        return facets;
    }
}
