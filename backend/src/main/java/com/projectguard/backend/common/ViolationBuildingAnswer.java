package com.projectguard.backend.common;

/**
 * 사용자가 건축물대장에서 직접 확인한 "위반건축물" 표시 여부. 위반건축물 여부는 건축물대장 공공 API
 * (건축HUB 표제부·기본개요·층별개요 등)에 들어 있지 않아 자동으로 알 수 없다 (docs/결정사항.md 47번).
 */
public enum ViolationBuildingAnswer {
    /** 위반건축물 표시가 있음. */
    MARKED,
    /** 표시가 없음을 확인함. */
    NOT_MARKED,
    /** 확인하지 않음. */
    UNKNOWN
}
