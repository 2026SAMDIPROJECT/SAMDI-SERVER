package com.example.samdi.wallet.domain;

public enum TransactionType {

    DELIVERY_REWARD(true), // 배달 완료 보상
    ITEM_PURCHASE(false), // 아이템 구매
    ROBBERY_PAYMENT(false), // 강도, 진상 관련된 사건으로 인한 지출
    MOTORCYCLE_PURCHASE(false); // 오토바이 구매, 수리

    private final boolean income;

    TransactionType(boolean income) {
        this.income = income;
    }

    // charge/deduct에서 거래 유형과 금액 부호가 어긋나는 것을 막기 위한 판별
    public boolean isIncome() {
        return income;
    }
}
