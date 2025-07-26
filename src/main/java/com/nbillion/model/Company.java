package com.nbillion.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "companies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    @Id
    private String symbol;      // 종목 코드 (PK)

    private String name;        // 회사명
    private String sector;      // 산업 분야
    private String description; // 회사 설명

    private Double open;
    private Double close;
    private Long volume;
    private Long turnover;      // 거래대금

    private Double per;
    private Double pbr;
    private Double psr;

    @JsonProperty("market_cap")
    private Long marketCap;         // 시가총액
    
    @JsonProperty("dividend_yield")
    private Double dividendYield;   // 배당 수익률
    
    private Double roe;             // 자기자본이익률
}