package com.rastudio.commerce.inventory;
/** Mirrors the movement_type ENUM on inventory_movement in database/schema.sql. */
public enum MovementType { STOCK_IN, SALE_RESERVED, SALE_DEDUCTED, RETURN_IN, MANUAL_ADJUSTMENT, RELEASE_RESERVATION }
