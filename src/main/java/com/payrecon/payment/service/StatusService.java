package com.payrecon.payment.service;

import com.payrecon.payment.dto.Status;
import org.springframework.stereotype.Service;

// StatusService  →  new Status("payment-service", "UP")  →  Controller returns it  →  Jackson  →  JSON
// Jackson is a Java library that converts Java objects ↔ JSON
@Service
public class StatusService {
    public Status currentStatus(){
        return new Status("payment-status","UP");
    }
}
