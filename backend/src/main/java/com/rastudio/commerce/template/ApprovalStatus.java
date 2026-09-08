package com.rastudio.commerce.template;

/** Matches message_template.approval_status. WhatsApp templates flow DRAFT -> PENDING_APPROVAL -> APPROVED/REJECTED;
 *  Email templates go straight DRAFT -> APPROVED (no external review), per the roadmap. */
public enum ApprovalStatus { DRAFT, PENDING_APPROVAL, APPROVED, REJECTED }
