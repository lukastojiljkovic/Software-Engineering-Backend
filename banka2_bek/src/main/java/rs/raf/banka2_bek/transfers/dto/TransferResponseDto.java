package rs.raf.banka2_bek.transfers.dto;

import rs.raf.banka2_bek.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
public class TransferResponseDto {
    private Long id;
    private String orderNumber;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private BigDecimal toAmount;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal commission;
    private String clientFirstName;
    private String clientLastName;
    private PaymentStatus status;
    private LocalDateTime createdAt;

    public TransferResponseDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getFromAccountNumber() { return fromAccountNumber; }

    public void setFromAccountNumber(String fromAccountNumber) {
        this.fromAccountNumber = fromAccountNumber;
    }


    public String getToAccountNumber() { return toAccountNumber; }

    public void setToAccountNumber(String toAccountNumber) {
        this.toAccountNumber = toAccountNumber;
    }


    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getToAmount() { return toAmount; }
    public void setToAmount(BigDecimal toAmount) { this.toAmount = toAmount; }

    public String getFromCurrency() { return fromCurrency; }
    public void setFromCurrency(String fromCurrency) { this.fromCurrency = fromCurrency; }

    public String getToCurrency() { return toCurrency; }
    public void setToCurrency(String toCurrency) { this.toCurrency = toCurrency; }


    public BigDecimal getExchangeRate() { return exchangeRate; }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }


    public BigDecimal getCommission() { return commission; }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }


    public String getClientFirstName() { return clientFirstName; }

    public void setClientFirstName(String clientFirstName) {
        this.clientFirstName = clientFirstName;
    }


    public String getClientLastName() { return clientLastName; }

    public void setClientLastName(String clientLastName) {
        this.clientLastName = clientLastName;
    }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
