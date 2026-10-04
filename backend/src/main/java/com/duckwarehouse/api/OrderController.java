package com.duckwarehouse.api;

import com.duckwarehouse.pricing.QuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final QuoteService quotes;

    public OrderController(QuoteService quotes) { this.quotes = quotes; }

    @PostMapping("/quote")
    public QuoteService.Quote quote(@Valid @RequestBody Requests.Order request) { return quotes.quote(request); }
}
