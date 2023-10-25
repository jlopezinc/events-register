package org.jlopezinc.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@RegisterForReflection
public class PaymentInfo {
    BigDecimal amount;
    String byWho;
    Date confirmedAt;
    String paymentFile;
}
