package com.interswitch.capstone.ledger_mock;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @GetMapping("/{accountId}/balance")
    public Map<String, Object> getBalance(@PathVariable String accountId) {
        return Map.of(
            "accountId", accountId,
            "balance", new BigDecimal("10000.00"),
            "currency", "NGN"
        );
    }

    @PostMapping("/{accountId}/refund")
    public Map<String, Object> refund(@PathVariable String accountId, @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        System.out.println("✅ [LEDGER MOCK] Refunded " + amount + " to account " + accountId);
        return Map.of(
            "status", "SUCCESS",
            "message", "Refund processed successfully"
        );
    }

}