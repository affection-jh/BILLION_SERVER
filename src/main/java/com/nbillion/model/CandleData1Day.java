package com.nbillion.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

/**
 * 1일봉 데이터 엔티티
 */
@Entity
@Table(name = "candle_data_1day", indexes = {
    @Index(name = "idx_symbol_date_1day", columnList = "symbol, date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandleData1Day {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDate date;
    
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
    
    public CandleData1Day(String symbol, LocalDate date, double open, double high, double low, double close, long volume) {
        this.symbol = symbol;
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
} 