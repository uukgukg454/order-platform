package com.ujjwal.inventory_service.repository;

import com.ujjwal.inventory_service.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {
}
