package com.kdl.rfidinventory.util.rfid;

/**
 * Kotlin 與 Java 之間的橋接介面
 */
public interface RfidDataListener {
    /**
     * 當掃描到標籤時調用
     */
    void onTagScanned(RFIDTag tag);

    /**
     * 當掃描結束時調用（僅單次模式）
     */
    void onScanEnded();
}
