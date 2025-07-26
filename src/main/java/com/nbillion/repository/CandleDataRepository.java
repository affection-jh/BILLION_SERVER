package com.nbillion.repository;

import com.nbillion.model.CandleData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandleDataRepository extends JpaRepository<CandleData, Long> {

    /**
     * 특정 종목의 1분봉 데이터를 최신순으로 페이징 조회
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.period = '1min' ORDER BY c.timestamp DESC")
    Page<CandleData> find1MinCandlesBySymbol(@Param("symbol") String symbol, Pageable pageable);

    /**
     * 특정 종목의 특정 기간 데이터를 최신순으로 페이징 조회
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.period = :period ORDER BY c.timestamp DESC")
    Page<CandleData> findCandlesBySymbolAndPeriod(@Param("symbol") String symbol, @Param("period") String period, Pageable pageable);

    /**
     * 특정 종목의 최근 N개 1분봉 데이터 조회 (실시간 차트용)
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.period = '1min' ORDER BY c.timestamp DESC")
    List<CandleData> findRecent1MinCandlesBySymbol(@Param("symbol") String symbol, Pageable pageable);

    /**
     * 특정 종목의 특정 기간 내 데이터 조회
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.period = :period AND c.timestamp BETWEEN :startTime AND :endTime ORDER BY c.timestamp DESC")
    List<CandleData> findCandlesBySymbolAndTimeRange(
            @Param("symbol") String symbol, 
            @Param("period") String period, 
            @Param("startTime") Long startTime, 
            @Param("endTime") Long endTime);

    /**
     * 특정 종목의 가장 최근 캔들 데이터 조회
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.period = '1min' ORDER BY c.timestamp DESC")
    List<CandleData> findLatestCandleBySymbol(@Param("symbol") String symbol, Pageable pageable);

    /**
     * 특정 종목의 특정 기간 데이터 개수 조회
     */
    @Query("SELECT COUNT(c) FROM CandleData c WHERE c.symbol = :symbol AND c.period = :period")
    long countBySymbolAndPeriod(@Param("symbol") String symbol, @Param("period") String period);

    /**
     * 오래된 데이터 삭제 (성능 최적화용)
     */
    @Query("DELETE FROM CandleData c WHERE c.timestamp < :cutoffTime")
    void deleteOldData(@Param("cutoffTime") Long cutoffTime);
} 