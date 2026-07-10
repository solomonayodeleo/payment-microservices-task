package com.interswitch.capstone.ledger_mock;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


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

}