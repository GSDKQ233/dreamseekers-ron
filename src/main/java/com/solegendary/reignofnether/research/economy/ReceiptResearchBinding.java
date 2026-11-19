package com.solegendary.reignofnether.research.economy;

import java.util.UUID;
/** Transient queue binding. The original paid receipt, not this queue, is persisted. */
public record ReceiptResearchBinding(UUID receiptId, String payer, UUID epoch,
        UUID placementInstance, long captureRevision) {}
