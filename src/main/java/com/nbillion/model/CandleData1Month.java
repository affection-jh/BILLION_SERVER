package com.nbillion.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.YearMonth;

/**
 * 1개월봉 데이터 엔티티
 */
@Entity
@Table(name = "candle_data_1month", indexes = {
    @Index(name = "idx_symbol_yearmonth_1month", columnList = "symbol, yearMonth")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandleData1Month {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private YearMonth yearMonth;
    
    @Column(nullable = false)
    private double open;
    
    @Column(nullable = false)
    private double high;
    
    @Column(nullable = false)
    private double low;
    
    @Column(nullable = false)
    private double close;
    
    @Column(nullable = false)
    private long volume;
    
    public CandleData1Month(String symbol, YearMonth yearMonth, double open, double high, double low, double close, long volume) {
        this.symbol = symbol;
        this.yearMonth = yearMonth;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
} 