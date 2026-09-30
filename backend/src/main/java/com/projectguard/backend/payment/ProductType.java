package com.projectguard.backend.payment;

/** 결제 상품. 금액은 항상 서버가 여기서 정한다. */
public enum ProductType {
    LAWYER_SELECTION(2_900, "원하는 변호사 직접 선택 상담 1회"),
    ANALYSIS(990, "등기부등본 추가 분석 1회");

    private final long price;
    private final String orderName;

    ProductType(long price, String orderName) {
        this.price = price;
        this.orderName = orderName;
    }

    public long getPrice() {
        return price;
    }

    public String getOrderName() {
        return orderName;
    }
}
