package com.rastudio.commerce.competitor.provider;

import java.math.BigDecimal;

public record CheckedPrice(BigDecimal competitorPrice, String note) {}
