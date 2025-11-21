package com.kdl.rfidinventory.util.rfid;

import android.content.Context;
import android.util.Log;

import com.kdl.rfidinventory.util.SoundTool;
import com.ubx.usdk.rfid.aidl.IRfidCallback;
import com.ubx.usdk.rfid.aidl.RfidDate;

import java.util.Set;

/**
 * Java 橋接類 - 實現 USDK 的 IRfidCallback 介面
 * 負責將 Java 回調轉換為 Kotlin 可用的資料
 */
//public class RfidCallbackBridge implements IRfidCallback {
//
//    private static final String TAG = "RfidCallbackBridge";
//
//    private final RfidDataListener dataListener;
//    private final Context context;
//    private final Set<String> seenTags;
//    private final boolean isContinuousMode;
//
//    public RfidCallbackBridge(
//            RfidDataListener listener,
//            Context context,
//            Set<String> seenTags,
//            boolean isContinuousMode
//    ) {
//        this.dataListener = listener;
//        this.context = context;
//        this.seenTags = seenTags;
//        this.isContinuousMode = isContinuousMode;
//    }
//
//    @Override
//    public void onInventoryTag(
//            byte cmd,
//            String pc,
//            String crc,
//            String epc,
//            byte antId,
//            String rssi,
//            String frequency,
//            int phase,
//            int count,
//            String readId
//    ) {
//        Log.d(TAG, "onInventoryTag: EPC=" + epc + ", RSSI=" + rssi);
//
//        // 播放聲音
//        try {
//            Log.e(TAG, "playing sound");
//            SoundTool.getInstance(context).playBeep(1);
//        } catch (Exception e) {
//            Log.e(TAG, "Error playing sound", e);
//        }
//
//        // 單次模式：只掃描一個標籤
//        if (!isContinuousMode) {
//            if (seenTags.isEmpty()) {
//                seenTags.add(epc);
//                notifyTag(epc, rssi);
//            }
//            return;
//        }
//
//        // 連續模式：每個標籤只通知一次
//        if (!seenTags.contains(epc)) {
//            seenTags.add(epc);
//            notifyTag(epc, rssi);
//        }
//    }
//
//    @Override
//    public void onInventoryTagEnd(
//            int antId,
//            int tagCount,
//            int speed,
//            int totalCount,
//            byte cmd
//    ) {
//        Log.d(TAG, "onInventoryTagEnd: tagCount=" + tagCount + ", totalCount=" + totalCount);
//
//        // 單次模式：掃描結束後通知
//        if (!isContinuousMode) {
//            if (dataListener != null) {
//                dataListener.onScanEnded();
//            }
//        }
//    }
//
//    @Override
//    public void onOperationTag(
//            String pc,
//            String crc,
//            String epc,
//            String data,
//            int dataLen,
//            byte antId,
//            byte cmd
//    ) {
//        // 用於讀寫標籤操作的回調
//        Log.d(TAG, "onOperationTag: EPC=" + epc + ", data=" + data);
//    }
//
//    @Override
//    public void onOperationTagEnd(int count) {
//        Log.d(TAG, "onOperationTagEnd: count=" + count);
//    }
//
//    @Override
//    public void refreshSetting(RfidDate rfidDate) {
//        Log.d(TAG, "refreshSetting called");
//        if (rfidDate != null) {
//            try {
//                byte[] powerArray = rfidDate.getbtAryOutputPower();
//                if (powerArray != null && powerArray.length > 0) {
//                    int power = powerArray[0] & 0xFF;
//                    Log.d(TAG, "Current Power: " + power);
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error reading power", e);
//            }
//        }
//    }
//
//    @Override
//    public void onExeCMDStatus(byte cmd, byte status) {
//        Log.d(TAG, "onExeCMDStatus: cmd=" + cmd + ", status=" + status);
//
//        // 可以根據 cmd 和 status 處理不同的命令執行結果
//        if (status == 0x10) { // SUCCESS
//            Log.d(TAG, "Command executed successfully");
//        } else {
//            Log.e(TAG, "Command failed with status: " + status);
//        }
//    }
//
//    private void notifyTag(String epc, String rssi) {
//        try {
//            int rssiValue = parseRssi(rssi);
//            RFIDTag tag = new RFIDTag(epc, rssiValue, System.currentTimeMillis());
//            if (dataListener != null) {
//                dataListener.onTagScanned(tag);
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error notifying tag", e);
//        }
//    }
//
//    private int parseRssi(String rssi) {
//        try {
//            if (rssi == null || rssi.isEmpty()) {
//                return 0;
//            }
//            return Integer.parseInt(rssi);
//        } catch (NumberFormatException e) {
//            Log.e(TAG, "Error parsing RSSI: " + rssi, e);
//            return 0;
//        }
//    }
//}

/**
 * 基於 UHF Code 的 ScanCallback
 */
public class RfidCallbackBridge implements IRfidCallback {

    private static final String TAG = "RfidCallbackBridge";

    private final RfidDataListener dataListener;
    private final Context context;
    private final Set<String> seenTags;
    private final boolean isContinuousMode;

    public RfidCallbackBridge(
            RfidDataListener listener,
            Context context,
            Set<String> seenTags,
            boolean isContinuousMode
    ) {
        this.dataListener = listener;
        this.context = context;
        this.seenTags = seenTags;
        this.isContinuousMode = isContinuousMode;

        Log.d(TAG, "✅ RfidCallbackBridge created - isContinuous: " + isContinuousMode);
    }

    /**
     * 方法 1: 簡化版（UHF Code 使用的）
     */
    @Override
    public void onInventoryTag(String EPC, String Data, String rssi) {
        Log.d(TAG, "📡 onInventoryTag(3 params): EPC=" + EPC + ", RSSI=" + rssi);
        handleTag(EPC, rssi);
    }

    /**
     * 方法 2: 完整版（可能你的 SDK 使用的）
     * 如果編譯錯誤，請註釋掉這個方法
     */
    public void onInventoryTag(
            byte cmd,
            String pc,
            String crc,
            String epc,
            byte antId,
            String rssi,
            String frequency,
            int phase,
            int count,
            String readId
    ) {
        Log.d(TAG, "📡 onInventoryTag(10 params): EPC=" + epc + ", RSSI=" + rssi);
        handleTag(epc, rssi);
    }

    /**
     * 統一處理標籤數據
     */
    private void handleTag(String epc, String rssi) {
        if (epc == null || epc.isEmpty()) {
            Log.w(TAG, "⚠️ Empty EPC received");
            return;
        }

        epc = epc.toUpperCase();
        Log.i(TAG, "🎯 Processing tag: " + epc + ", RSSI: " + rssi);

        // 播放聲音
        try {
            SoundTool.getInstance(context).playBeep(1);
            Log.d(TAG, "🔊 Beep played");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error playing sound", e);
        }

        // 單次模式：只掃描一個標籤
        if (!isContinuousMode) {
            if (seenTags.isEmpty()) {
                seenTags.add(epc);
                notifyTag(epc, rssi);
                Log.d(TAG, "✅ Single mode: Tag notified");
            } else {
                Log.d(TAG, "⏭️ Single mode: Already scanned, ignoring");
            }
            return;
        }

        // 連續模式：每個標籤只通知一次
        if (!seenTags.contains(epc)) {
            seenTags.add(epc);
            notifyTag(epc, rssi);
            Log.d(TAG, "✅ Continuous mode: New tag added (" + seenTags.size() + " total)");
        } else {
            Log.v(TAG, "⏭️ Continuous mode: Duplicate tag ignored");
        }
    }

    /**
     * 方法 3: 掃描結束回調（簡化版）
     */
    @Override
    public void onInventoryTagEnd() {
        Log.d(TAG, "🛑 onInventoryTagEnd() called");
        handleScanEnd();
    }

    /**
     * 方法 4: 掃描結束回調（完整版）
     * 如果編譯錯誤，請註釋掉這個方法
     */
    public void onInventoryTagEnd(
            int antId,
            int tagCount,
            int speed,
            int totalCount,
            byte cmd
    ) {
        Log.d(TAG, "🛑 onInventoryTagEnd(5 params): tagCount=" + tagCount + ", totalCount=" + totalCount);
        handleScanEnd();
    }

    /**
     * 統一處理掃描結束
     */
    private void handleScanEnd() {
        Log.i(TAG, "📊 Scan ended - Total unique tags: " + seenTags.size());

        // 單次模式：掃描結束後通知
        if (!isContinuousMode) {
            if (dataListener != null) {
                dataListener.onScanEnded();
                Log.d(TAG, "✅ Single mode: Scan ended notification sent");
            }
        }
    }

    /**
     * 通知 Kotlin 層
     */
    private void notifyTag(String epc, String rssi) {
        try {
            int rssiValue = parseRssi(rssi);
            RFIDTag tag = new RFIDTag(epc, rssiValue, System.currentTimeMillis());

            if (dataListener != null) {
                dataListener.onTagScanned(tag);
                Log.d(TAG, "✅ Tag notification sent to Kotlin");
            } else {
                Log.e(TAG, "❌ dataListener is null!");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error notifying tag", e);
        }
    }

    /**
     * 解析 RSSI 值
     */
    private int parseRssi(String rssi) {
        try {
            if (rssi == null || rssi.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(rssi);
        } catch (NumberFormatException e) {
            Log.e(TAG, "⚠️ Error parsing RSSI: " + rssi, e);
            return 0;
        }
    }
}