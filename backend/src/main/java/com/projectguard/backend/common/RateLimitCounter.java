package com.projectguard.backend.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/** 하루 단위 사용 횟수(IP별 무료 분석, 이메일별 비밀번호 찾기 메일 등). 키에는 원문 대신 해시를 쓴다. */
@Entity
@Table(name = "rate_limit_counters", uniqueConstraints = @UniqueConstraint(columnNames = {"counterKey", "usage_day"}))
public class RateLimitCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String counterKey;

    // day·count는 DB 예약어라 컬럼 이름을 따로 준다.
    @Column(name = "usage_day", nullable = false)
    private LocalDate day;

    @Column(name = "usage_count", nullable = false)
    private int count = 0;

    protected RateLimitCounter() {
    }

    public RateLimitCounter(String counterKey, LocalDate day) {
        this.counterKey = counterKey;
        this.day = day;
    }

    public void increment() {
        this.count++;
    }

    public Long getId() {
        return id;
    }

    public int getCount() {
        return count;
    }
}
