package com.nbillion.repository;

import com.nbillion.model.CandleData30Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleData30MinRepository extends JpaRepository<CandleData30Min, Long> {
    
    @Query("SELECT c FROM CandleData30Min c WHERE c.symbol = :symbol ORDER BY c.timestamp DESC")
    Page<CandleData30Min> findBySymbolOrderByTimestampDesc(@Param("symbol") String symbol, Pageable pageable);
    
    @Query("SELECT c FROM CandleData30Min c WHERE c.symbol = :symbol AND c.timestamp BETWEEN :startTime AND :endTime ORDER BY c.timestamp DESC")
    List<CandleData30Min> findBySymbolAndTimeRange(@Param("symbol") String symbol, 
                                                   @Param("startTime") LocalDateTime startTime, 
                                                   @Param("endTime") LocalDateTime endTime);
    
    @Query("SELECT c FROM CandleData30Min c WHERE c.symbol = :symbol ORDER BY c.timestamp DESC LIMIT 1")
    Optional<CandleData30Min> findLatestBySymbol(@Param("symbol") String symbol);
    
    @Query("SELECT c FROM CandleData30Min c WHERE c.symbol = :symbol ORDER BY c.timestamp DESC LIMIT :count")
    List<CandleData30Min> findRecentBySymbol(@Param("symbol") String symbol, @Param("count") int count);
    
    @Query("SELECT COUNT(c) FROM CandleData30Min c WHERE c.symbol = :symbol")
    long countBySymbol(@Param("symbol") String symbol);
    
    @Query("DELETE FROM CandleData30Min c WHERE c.timestamp < :cutoffTime")
    void deleteOldData(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    // 1년치 데이터만 유지하기 위한 메서드
    @Query("SELECT c FROM CandleData30Min c WHERE c.symbol = :symbol ORDER BY c.timestamp DESC")
    List<CandleData30Min> findAllBySymbolOrderByTimestampDesc(@Param("symbol") String symbol);
} 