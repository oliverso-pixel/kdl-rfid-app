package com.kdl.rfidinventory.data.model

/**
 * 上貨步驟
 */
enum class LoadingStep {
    SELECT_MODE,        // 選擇模式 (車+格 或 散貨)
    SELECT_WAREHOUSE,   // 選擇倉庫
    SELECT_ROUTE,       // 選擇路線
    ROUTE_DETAIL,
    SELECT_ITEM,        // 選擇貨物
    SCANNING,           // 掃描上貨
    SUMMARY             // 匯總確認
}