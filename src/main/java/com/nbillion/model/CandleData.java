package com.nbillion.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "candle_data")
@Data
public class CandleData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;      // 종목 코드

    @Column(nullable = false)
    private Long timestamp;    // UTC 기준 밀리초 단위 타임스탬프

    @Column(nullable = false)
    private String period;     // "1min", "5min", "1day" 등

    @Column(nullable = false)
    private Double open;

    @Column(nullable = false)
    private Double high;

    @Column(nullable = false)
    private Double low;

    @Column(nullable = false)
    private Double close;

    @Column(nullable = false)
    private Long volume;       // 거래량

    public CandleData(Long timestamp, Double open, Double high, Double low, Double close, Long volume) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

}