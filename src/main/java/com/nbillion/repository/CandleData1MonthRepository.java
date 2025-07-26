package com.nbillion.repository;

import com.nbillion.model.CandleData1Month;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleData1MonthRepository extends JpaRepository<CandleData1Month, Long> {
    
    @Query("SELECT c FROM CandleData1Month c WHERE c.symbol = :symbol ORDER BY c.yearMonth DESC")
    Page<CandleData1Month> findBySymbolOrderByYearMonthDesc(@Param("symbol") String symbol, Pageable pageable);
    
    @Query("SELECT c FROM CandleData1Month c WHERE c.symbol = :symbol AND c.yearMonth BETWEEN :startYearMonth AND :endYearMonth ORDER BY c.yearMonth DESC")
    List<CandleData1Month> findBySymbolAndYearMonthRange(@Param("symbol") String symbol, 
                                                        @Param("startYearMonth") YearMonth startYearMonth, 
                                                        @Param("endYearMonth") YearMonth endYearMonth);
    
    @Query("SELECT c FROM CandleData1Month c WHERE c.symbol = :symbol ORDER BY c.yearMonth DESC LIMIT 1")
    Optional<CandleData1Month> findLatestBySymbol(@Param("symbol") String symbol);
    
    @Query("SELECT c FROM CandleData1Month c WHERE c.symbol = :symbol ORDER BY c.yearMonth DESC LIMIT :count")
    List<CandleData1Month> findRecentBySymbol(@Param("symbol") String symbol, @Param("count") int count);
    
    @Query("SELECT COUNT(c) FROM CandleData1Month c WHERE c.symbol = :symbol")
    long countBySymbol(@Param("symbol") String symbol);
    
    @Query("DELETE FROM CandleData1Month c WHERE c.yearMonth < :cutoffYearMonth")
    void deleteOldData(@Param("cutoffYearMonth") YearMonth cutoffYearMonth);
    
    // 1년치 데이터만 유지하기 위한 메서드
    @Query("SELECT c FROM CandleData1Month c WHERE c.symbol = :symbol ORDER BY c.yearMonth DESC")
    List<CandleData1Month> findAllBySymbolOrderByYearMonthDesc(@Param("symbol") String symbol);
} 