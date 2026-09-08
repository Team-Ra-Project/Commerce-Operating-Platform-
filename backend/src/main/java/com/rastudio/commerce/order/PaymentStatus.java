package com.rastudio.commerce.order;
/** Mirrors the payment_status ENUM on customer_order in database/schema.sql. */
public enum PaymentStatus { PAID, PENDING, REFUNDED, FAILED }
