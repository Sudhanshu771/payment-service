package com.payrecon.payment.controller;

import com.payrecon.payment.dto.Status;
import com.payrecon.payment.service.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// this class handles web requests
// In real apps, a controller shouldn’t contain business logic.
// Flow : Controller  →  Service  →  Repository  →  Database
//        (HTTP in/out)  (rules)     (SQL)
@RestController
public class StatusController {

    private final StatusService statusService;

    // The constructor is the door Spring uses to hand you the StatusService
    // Constructor injection
    public StatusController(StatusService statusService){
        this.statusService = statusService;
    }

    @GetMapping("/hello")
    public String hello(){
        return "Hello Service is Running";
    }

    @GetMapping("/status")
    public Status status() {
        return statusService.currentStatus();
    }
}
