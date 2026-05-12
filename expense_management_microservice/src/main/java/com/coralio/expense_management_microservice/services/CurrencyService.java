package com.coralio.expense_management_microservice.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CurrencyService {

    private static final String API_URL = "https://api.exchangerate.host/latest?base=TND";
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Double> rateCache = new ConcurrentHashMap<>();
    private Instant lastUpdate = Instant.MIN;
    private static final long CACHE_DURATION_HOURS = 1;

    // Fixed: use Map.ofEntries for more than 10 entries
    private static final Map<String, Double> FALLBACK_RATES = Map.ofEntries(
            Map.entry("USD", 0.32),
            Map.entry("EUR", 0.30),
            Map.entry("GBP", 0.25),
            Map.entry("JPY", 48.0),
            Map.entry("CAD", 0.43),
            Map.entry("CHF", 0.29),
            Map.entry("CNY", 2.31),
            Map.entry("AED", 1.17),
            Map.entry("SAR", 1.20),
            Map.entry("QAR", 1.16),
            Map.entry("KWD", 0.098),
            Map.entry("BHD", 0.12),
            Map.entry("OMR", 0.123),
            Map.entry("JOD", 0.226),
            Map.entry("LYD", 1.54),
            Map.entry("EGP", 15.2),
            Map.entry("MAD", 3.18),
            Map.entry("DZD", 43.0),
            Map.entry("TRY", 10.3)
    );

    public double getExchangeRate(String targetCurrency) {
        if (targetCurrency == null) {
            log.warn("Target currency is null, returning 1.0 (TND)");
            return 1.0;
        }
        if ("TND".equalsIgnoreCase(targetCurrency)) {
            return 1.0;
        }

        refreshCacheIfNeeded();

        Double rate = rateCache.get(targetCurrency.toUpperCase());
        if (rate == null) {
            log.warn("No exchange rate found for {}, falling back to TND (1.0)", targetCurrency);
            return 1.0;
        }
        log.debug("Exchange rate TND -> {} = {}", targetCurrency, rate);
        return rate;
    }

    private synchronized void refreshCacheIfNeeded() {
        long hoursSinceLastUpdate = Duration.between(lastUpdate, Instant.now()).toHours();
        if (hoursSinceLastUpdate < CACHE_DURATION_HOURS) {
            log.debug("Cache still fresh ({} hours old)", hoursSinceLastUpdate);
            return;
        }

        log.info("Refreshing exchange rates from API: {}", API_URL);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(API_URL, Map.class);
            if (response != null && response.containsKey("rates")) {
                Map<String, Double> rates = (Map<String, Double>) response.get("rates");
                if (rates != null && !rates.isEmpty()) {
                    rateCache.clear();
                    rateCache.putAll(rates);
                    lastUpdate = Instant.now();
                    log.info("Exchange rates refreshed successfully. Available: {}", rateCache.keySet());
                    return;
                }
            }
            throw new RuntimeException("Invalid API response: missing 'rates' field");
        } catch (Exception e) {
            log.error("Failed to refresh exchange rates from API: {}", e.getMessage());
            if (FALLBACK_RATES != null && !FALLBACK_RATES.isEmpty()) {
                log.warn("Using fallback static exchange rates (may be outdated)");
                rateCache.clear();
                rateCache.putAll(FALLBACK_RATES);
                lastUpdate = Instant.now().minus(Duration.ofMinutes(55));
            } else {
                log.error("No fallback rates available – currency conversions will default to 1.0");
            }
        }
    }

    public void forceRefresh() {
        lastUpdate = Instant.MIN;
        refreshCacheIfNeeded();
    }

    public Double getCachedRate(String currencyCode) {
        refreshCacheIfNeeded();
        return rateCache.get(currencyCode.toUpperCase());
    }
}