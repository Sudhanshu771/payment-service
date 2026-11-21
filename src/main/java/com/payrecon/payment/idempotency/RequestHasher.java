package com.payrecon.payment.idempotency;

import com.payrecon.payment.dto.CreatePaymentRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestHasher {

    private RequestHasher() { }   // nobody creates objects of this class; we only call hash()

    public static String hash(CreatePaymentRequest req) {

        // 1. Put all the payment details into one line of text
        String text = req.amount().stripTrailingZeros().toPlainString()
                + "|" + req.currency()
                + "|" + req.payerAccount()
                + "|" + req.payeeAccount()
                + "|" + req.reference();

        // 2. Turn that text into a SHA-256 code
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(text.getBytes(StandardCharsets.UTF_8));

            // 3. Bytes → readable text like "a3f9c2..."
            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);   // never happens in practice
        }
    }
}