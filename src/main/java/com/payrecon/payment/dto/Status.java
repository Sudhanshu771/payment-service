package com.payrecon.payment.dto;

public record Status(String service,String status) {
}

// "record" is a Java feature (Java 16+) for classes that only hold data. That one line gives you all of this automatically

//public final class Status {
//    private final String service;
//    private final String status;
//
//    public Status(String service, String status) {   // constructor
//        this.service = service;
//        this.status = status;
//    }
//
//    public String service() { return service; }      // getters (no "get" prefix)
//    public String status()  { return status; }
//
//    // plus equals(), hashCode() and toString(), all generated
//}