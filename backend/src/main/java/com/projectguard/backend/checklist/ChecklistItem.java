package com.projectguard.backend.checklist;

/**
 * 등기부등본만으로는 알 수 없어서, 사용자가 직접 확인해야 하는 할 일 항목.
 * (docs/기획서.md 2번 P0 "체크리스트" 참고)
 */
public record ChecklistItem(String title, String description) {
}
