package com.nbillion.repository;

import com.nbillion.model.CandleData1Week;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleData1WeekRepository extends JpaRepository<CandleData1Week, Long> {
    
    @Query("SELECT c FROM CandleData1Week c WHERE c.symbol = :symbol ORDER BY c.date DESC")
    Page<CandleData1Week> findBySymbolOrderByDateDesc(@Param("symbol") String symbol, Pageable pageable);
    
    @Query("SELECT c FROM CandleData1Week c WHERE c.symbol = :symbol AND c.date BETWEEN :startDate AND :endDate ORDER BY c.date DESC")
    List<CandleData1Week> findBySymbolAndDateRange(@Param("symbol") String symbol, 
                                                   @Param("startDate") LocalDate startDate, 
                                                   @Param("endDate") LocalDate endDate);
    
    @Query("SELECT c FROM CandleData1Week c WHERE c.symbol = :symbol ORDER BY c.date DESC LIMIT 1")
    Optional<CandleData1Week> findLatestBySymbol(@Param("symbol") String symbol);
    
    @Query("SELECT c FROM CandleData1Week c WHERE c.symbol = :symbol ORDER BY c.date DESC LIMIT :count")
    List<CandleData1Week> findRecentBySymbol(@Param("symbol") String symbol, @Param("count") int count);
    
    @Query("SELECT COUNT(c) FROM CandleData1Week c WHERE c.symbol = :symbol")
    long countBySymbol(@Param("symbol") String symbol);
    
    @Query("DELETE FROM CandleData1Week c WHERE c.date < :cutoffDate")
    void deleteOldData(@Param("cutoffDate") LocalDate cutoffDate);
    
    // 1년치 데이터만 유지하기 위한 메서드
    @Query("SELECT c FROM CandleData1Week c WHERE c.symbol = :symbol ORDER BY c.date DESC")
    List<CandleData1Week> findAllBySymbolOrderByDateDesc(@Param("symbol") String symbol);
} 