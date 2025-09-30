package com.swiftly.repository;

import com.swiftly.model.Product;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ElasticSearchRepository extends ElasticsearchRepository<Product, Integer> {
}
