package com.rastudio.commerce.order;
/** Mirrors the status ENUM on customer_order in database/schema.sql. */
public enum OrderStatus { NEW, CONFIRMED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED }
