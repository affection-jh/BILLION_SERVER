package com.nbillion.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 실시간 시세 메시지 구조
 * 예시 JSON:
 * {
 *   "type": "ticker",
 *   "source": "home",
 *   "data": [
 *     ["XPL", 132.4, 4.62],
 *     ["ZNT", 87.1, -1.35]
 *   ]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TickerMessage {

    private String type = "ticker";
    private String source; // "home", "all", "detail"
    private List<List<Object>> data;
}