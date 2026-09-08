package com.rastudio.commerce.returns;
/** Mirrors the status ENUM on return_request in database/schema.sql. */
public enum ReturnStatus { REQUESTED, APPROVED_AWAITING_ITEM, REJECTED, RECEIVED_INSPECTING, REFUNDED, REPLACED, CLOSED }
