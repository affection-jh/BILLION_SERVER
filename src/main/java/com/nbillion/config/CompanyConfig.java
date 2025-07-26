package com.nbillion.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 회사 초기화 데이터 설정
 */
@Component
@Getter
public class CompanyConfig {

    private final List<CompanyData> companies;

    public CompanyConfig() {
        System.out.println("CompanyConfig 생성자 시작");
        List<CompanyData> loadedCompanies;
        try {
            loadedCompanies = loadCompaniesFromJson();
            System.out.println("Companies.json 로딩 성공: " + loadedCompanies.size() + "개 회사");
        } catch (Exception e) {
            // 파일 로드 실패 시 기본 데이터 사용
            System.err.println("Warning: companies.json 로드 실패, 기본 데이터 사용: " + e.getMessage());
            loadedCompanies = createDefaultCompanies();
        }
        this.companies = loadedCompanies;
        System.out.println("🏁 CompanyConfig 생성자 완료: " + this.companies.size() + "개 회사");
    }

    @Getter
    public static class CompanyData {
        private String symbol;
        private String name;
        private String sector;
        private String description;
        
        @JsonProperty("initialPrice")
        private double initialPrice;
        
        @JsonProperty("isTrending")
        private boolean isTrending;
        
        @JsonProperty("isRecommended")
        private boolean isRecommended;
    }

    private List<CompanyData> loadCompaniesFromJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("companies.json");


        try (InputStream inputStream = resource.getInputStream()) {
            System.out.println("company.json 파일 읽기 시작...");
            List<CompanyData> companies = mapper.readValue(inputStream, new TypeReference<List<CompanyData>>() {});
            return companies;
        } catch (Exception e) {
            System.err.println("❌ JSON 파싱 오류: " + e.getMessage());
            throw e;
        }
    }

    private List<CompanyData> createDefaultCompanies() {
        System.out.println("기본 회사 데이터 생성");
        List<CompanyData> defaultCompanies = new ArrayList<>();
        
        // 기본 회사 데이터 생성
        CompanyData company = new CompanyData();
        company.symbol = "N Billion";
        company.name = "Default Company";
        company.sector = "Technology";
        company.description = "Default company for testing";
        company.initialPrice = 100.0;
        company.isTrending = false;
        company.isRecommended = false;
        
        defaultCompanies.add(company);
        return defaultCompanies;
    }
} 