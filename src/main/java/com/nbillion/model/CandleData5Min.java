package com.nbillion.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 5분봉 데이터 엔티티
 */
@Entity
@Table(name = "candle_data_5min", indexes = {
    @Index(name = "idx_symbol_timestamp_5min", columnList = "symbol, timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandleData5Min {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
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
    
    public CandleData5Min(String symbol, LocalDateTime timestamp, double open, double high, double low, double close, long volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
} 