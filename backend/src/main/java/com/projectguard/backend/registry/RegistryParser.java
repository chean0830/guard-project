package com.projectguard.backend.registry;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDFBox로 추출한 등기부등본 원문 텍스트를 갑구(소유권)/을구(근저당권) 항목으로 구조화한다.
 *
 * 등기부등본은 표 형태 문서라 PDFBox가 셀 줄바꿈까지 그대로 줄바꿈으로 뽑아낸다.
 * 그래서 한 "항목"이 여러 줄에 걸쳐 나오는데, 각 항목은 항상 줄 맨 앞이
 * "{순위번호} {등기목적}" 형태로 시작한다는 규칙을 이용해 항목 단위로 다시 묶는다.
 */
@Component
public class RegistryParser {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("\\[집합건물]\\s*(.+)");
    private static final Pattern UNIQUE_NUMBER_PATTERN = Pattern.compile("고유번호\\s*(\\S+)");
    private static final Pattern TITLE_PATTERN = Pattern.compile("등기사항전부증명서");

    // OCR(카메라 촬영)은 "【 갑 구 】" 같은 괄호 문구를 "갑"/"구]"처럼 줄바꿈으로 쪼개 인식하는 경우가 있어,
    // 옆에 항상 같이 나오는 고정 부제("소유권에 관한 사항" 등)도 보조 판별 기준으로 함께 둔다.
    private static final Pattern GAPGU_HEADER = Pattern.compile("갑\\s*구|소유권에\\s*관한\\s*사항");
    private static final Pattern EULGU_HEADER = Pattern.compile("을\\s*구|소유권\\s*이외의\\s*권리에\\s*관한\\s*사항");
    private static final Pattern SUMMARY_MARKER = Pattern.compile("주요\\s*등기사항\\s*요약");

    private static final Pattern ENTRY_START = Pattern.compile("^(\\d{1,3})\\s+(\\S.*)$");
    private static final Pattern TARGET_RANK = Pattern.compile("(\\d+)번");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}년\\d{1,2}월\\d{1,2}일)");
    private static final Pattern CLAIM_AMOUNT_PATTERN = Pattern.compile("채권최고액\\s*금\\s*([\\d,]+)\\s*원");
    private static final Pattern DEBTOR_PATTERN = Pattern.compile("채무자\\s+(\\S+)");
    private static final Pattern MORTGAGEE_PATTERN = Pattern.compile("근저당권자\\s+(\\S+)");
    private static final Pattern OWNER_PATTERN = Pattern.compile("소유자\\s+(\\S+)");

    public RegistryAnalysis parse(String rawText) {
        if (!TITLE_PATTERN.matcher(rawText).find()) {
            throw new NotRegistryDocumentException(
                    "등기부등본(등기사항전부증명서)이 아닌 것 같습니다. 등기부등본 PDF를 업로드해주세요.");
        }

        String mainBody = cutBeforeSummary(rawText);
        String address = firstMatch(mainBody, ADDRESS_PATTERN);
        String uniqueNumber = firstMatch(mainBody, UNIQUE_NUMBER_PATTERN);

        List<String> lines = List.of(mainBody.split("\\r?\\n"));

        int gapguHeaderIdx = findLineIndex(lines, GAPGU_HEADER);
        int eulguHeaderIdx = findLineIndex(lines, EULGU_HEADER);

        List<String> gapguEntries = gapguHeaderIdx >= 0
                ? buildEntryBlocks(lines, gapguHeaderIdx + 1, eulguHeaderIdx >= 0 ? eulguHeaderIdx : lines.size())
                : List.of();
        List<String> eulguEntries = eulguHeaderIdx >= 0
                ? buildEntryBlocks(lines, eulguHeaderIdx + 1, lines.size())
                : List.of();

        Map<Integer, OwnershipEntry> ownershipByRank = new HashMap<>();
        for (String block : gapguEntries) {
            parseGapguEntry(block).ifPresent(entry -> ownershipByRank.put(entry.rank(), entry));
        }
        applyGapguCancellations(gapguEntries, ownershipByRank);

        Map<Integer, MortgageEntry> mortgageByRank = new HashMap<>();
        for (String block : eulguEntries) {
            parseEulguMortgage(block).ifPresent(entry -> mortgageByRank.put(entry.rank(), entry));
        }
        applyEulguCancellations(eulguEntries, mortgageByRank);
        backfillMortgagePartiesFromGlobalScan(eulguEntries, mortgageByRank);

        Map<Integer, SeizureEntry> seizureByRank = new HashMap<>();
        for (String block : gapguEntries) {
            parseSeizure(block).ifPresent(entry -> seizureByRank.put(entry.rank(), entry));
        }
        applySeizureCancellations(gapguEntries, seizureByRank);

        List<OwnershipEntry> ownershipHistory = new ArrayList<>(ownershipByRank.values());
        ownershipHistory.sort((a, b) -> Integer.compare(a.rank(), b.rank()));

        List<MortgageEntry> mortgages = new ArrayList<>(mortgageByRank.values());
        mortgages.sort((a, b) -> Integer.compare(a.rank(), b.rank()));

        List<SeizureEntry> seizures = new ArrayList<>(seizureByRank.values());
        seizures.sort((a, b) -> Integer.compare(a.rank(), b.rank()));

        long totalActiveMortgageAmount = mortgages.stream()
                .filter(m -> !m.cancelled())
                .mapToLong(MortgageEntry::maxClaimAmount)
                .sum();

        return new RegistryAnalysis(address, uniqueNumber, ownershipHistory, mortgages, seizures, totalActiveMortgageAmount);
    }

    private String cutBeforeSummary(String rawText) {
        Matcher m = SUMMARY_MARKER.matcher(rawText);
        if (m.find()) {
            return rawText.substring(0, m.start());
        }
        return rawText;
    }

    private String firstMatch(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private int findLineIndex(List<String> lines, Pattern headerPattern) {
        for (int i = 0; i < lines.size(); i++) {
            if (headerPattern.matcher(lines.get(i)).find()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 섹션 범위(start~end) 안에서, "순위번호로 시작하는 줄"을 항목의 시작으로 보고
     * 그다음 항목이 시작되기 전까지의 줄을 모두 이어 붙여 하나의 항목 블록으로 만든다.
     * 표 헤더, 페이지 머리말/꼬리말 같은 잡음 줄은 건너뛴다.
     */
    private List<String> buildEntryBlocks(List<String> lines, int start, int end) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;

        for (int i = start; i < end; i++) {
            String line = lines.get(i).trim();
            if (isNoiseLine(line)) {
                continue;
            }
            Matcher entryStart = ENTRY_START.matcher(line);
            if (entryStart.matches()) {
                if (current != null) {
                    blocks.add(current.toString());
                }
                current = new StringBuilder(line);
            } else if (current != null) {
                current.append(' ').append(line);
            }
        }
        if (current != null) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    private boolean isNoiseLine(String line) {
        if (line.isEmpty()) return true;
        if (line.contains("【")) return true;
        if (line.startsWith("순위번호")) return true;
        if (line.startsWith("열 람 용") || line.startsWith("열람일시")) return true;
        if (line.matches("^\\d+/\\d+$")) return true;
        if (line.startsWith("[집합건물]")) return true;
        if (line.startsWith("고유번호")) return true;
        if (line.startsWith("관할등기소")) return true;
        if (line.startsWith("*")) return true;
        if (line.matches(".*이\\s*하\\s*여\\s*백.*")) return true;
        return false;
    }

    private java.util.Optional<OwnershipEntry> parseGapguEntry(String block) {
        Matcher entryStart = ENTRY_START.matcher(block);
        if (!entryStart.find()) return java.util.Optional.empty();
        int rank = Integer.parseInt(entryStart.group(1));
        String purpose = entryStart.group(2);

        OwnershipType type;
        if (purpose.contains("말소")) {
            return java.util.Optional.empty(); // 말소 항목은 대상 항목에 반영, 별도 소유권 항목으로 만들지 않음
        } else if (purpose.contains("소유권보존")) {
            type = OwnershipType.OWNERSHIP_PRESERVATION;
        } else if (purpose.contains("소유권이전")) {
            type = OwnershipType.OWNERSHIP_TRANSFER;
        } else {
            return java.util.Optional.empty(); // 압류류/기타는 별도 파서에서 처리
        }

        String receivedDate = firstMatch(block, DATE_PATTERN);
        String ownerName = firstMatch(block, OWNER_PATTERN);
        return java.util.Optional.of(new OwnershipEntry(rank, type, ownerName, receivedDate, false));
    }

    private java.util.Optional<SeizureEntry> parseSeizure(String block) {
        Matcher entryStart = ENTRY_START.matcher(block);
        if (!entryStart.find()) return java.util.Optional.empty();
        int rank = Integer.parseInt(entryStart.group(1));
        String purpose = entryStart.group(2);

        if (purpose.contains("말소")) return java.util.Optional.empty();

        SeizureType type;
        if (purpose.contains("가압류")) {
            type = SeizureType.PROVISIONAL_SEIZURE;
        } else if (purpose.contains("압류")) {
            type = SeizureType.SEIZURE;
        } else if (purpose.contains("경매개시결정")) {
            type = SeizureType.AUCTION_COMMENCEMENT;
        } else if (purpose.contains("가처분")) {
            type = SeizureType.PROVISIONAL_DISPOSITION;
        } else {
            return java.util.Optional.empty();
        }

        String receivedDate = firstMatch(block, DATE_PATTERN);
        return java.util.Optional.of(new SeizureEntry(rank, type, receivedDate, false));
    }

    private java.util.Optional<MortgageEntry> parseEulguMortgage(String block) {
        Matcher entryStart = ENTRY_START.matcher(block);
        if (!entryStart.find()) return java.util.Optional.empty();
        int rank = Integer.parseInt(entryStart.group(1));
        String purpose = entryStart.group(2);

        if (purpose.contains("말소")) return java.util.Optional.empty();
        if (!purpose.contains("근저당권설정")) return java.util.Optional.empty();

        String receivedDate = firstMatch(block, DATE_PATTERN);
        String amountStr = firstMatch(block, CLAIM_AMOUNT_PATTERN);
        long amount = amountStr != null ? Long.parseLong(amountStr.replace(",", "")) : 0L;
        String debtorName = firstMatch(block, DEBTOR_PATTERN);
        String mortgageeName = firstMatch(block, MORTGAGEE_PATTERN);

        return java.util.Optional.of(new MortgageEntry(rank, amount, debtorName, mortgageeName, receivedDate, false));
    }

    /**
     * OCR(카메라 촬영)은 표의 우측 컬럼("채무자"/"근저당권자")을 좌측 컬럼과 다른 블록으로
     * 묶어버리는 경우가 있어, 해당 값이 엉뚱한(주로 뒤따르는 말소) 항목의 블록에 붙어버리고
     * 원래 항목에는 채무자/근저당권자가 비어버리는 문제가 생긴다. 블록 단위 파싱으로 값을 못
     * 채운 항목은, 을구 전체 텍스트에서 "채무자"/"근저당권자"가 등장하는 순서를 근저당권설정
     * 항목의 순위 순서와 매칭해 보완한다 — 등기부등본은 한 근저당권설정 항목당 채무자/근저당권자가
     * 정확히 하나씩만 나오므로, 등장 순서가 곧 순위 순서와 같다는 점을 이용한다.
     */
    private void backfillMortgagePartiesFromGlobalScan(List<String> eulguEntries, Map<Integer, MortgageEntry> byRank) {
        boolean needsBackfill = byRank.values().stream()
                .anyMatch(m -> m.debtorName() == null || m.mortgageeName() == null);
        if (!needsBackfill) {
            return;
        }

        String fullText = String.join(" ", eulguEntries);
        List<String> debtors = findAll(fullText, DEBTOR_PATTERN);
        List<String> mortgagees = findAll(fullText, MORTGAGEE_PATTERN);

        List<Integer> ranksInOrder = new ArrayList<>(byRank.keySet());
        ranksInOrder.sort(Integer::compareTo);

        for (int i = 0; i < ranksInOrder.size(); i++) {
            MortgageEntry entry = byRank.get(ranksInOrder.get(i));
            if (entry.debtorName() != null && entry.mortgageeName() != null) {
                continue;
            }
            String debtor = entry.debtorName() != null ? entry.debtorName() : (i < debtors.size() ? debtors.get(i) : null);
            String mortgagee = entry.mortgageeName() != null
                    ? entry.mortgageeName() : (i < mortgagees.size() ? mortgagees.get(i) : null);
            byRank.put(entry.rank(), new MortgageEntry(
                    entry.rank(), entry.maxClaimAmount(), debtor, mortgagee, entry.receivedDate(), entry.cancelled()));
        }
    }

    private List<String> findAll(String text, Pattern pattern) {
        List<String> results = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            results.add(m.group(1));
        }
        return results;
    }

    private void applyEulguCancellations(List<String> entries, Map<Integer, MortgageEntry> byRank) {
        for (String block : entries) {
            Matcher entryStart = ENTRY_START.matcher(block);
            if (!entryStart.find()) continue;
            String purpose = entryStart.group(2);
            if (!purpose.contains("말소")) continue;

            Matcher targetMatcher = TARGET_RANK.matcher(purpose);
            if (!targetMatcher.find()) continue;
            int targetRank = Integer.parseInt(targetMatcher.group(1));
            MortgageEntry target = byRank.get(targetRank);
            if (target != null) {
                byRank.put(targetRank, new MortgageEntry(
                        target.rank(), target.maxClaimAmount(), target.debtorName(),
                        target.mortgageeName(), target.receivedDate(), true));
            }
        }
    }

    private void applyGapguCancellations(List<String> entries, Map<Integer, OwnershipEntry> byRank) {
        for (String block : entries) {
            Matcher entryStart = ENTRY_START.matcher(block);
            if (!entryStart.find()) continue;
            String purpose = entryStart.group(2);
            if (!purpose.contains("말소")) continue;

            Matcher targetMatcher = TARGET_RANK.matcher(purpose);
            if (!targetMatcher.find()) continue;
            int targetRank = Integer.parseInt(targetMatcher.group(1));
            OwnershipEntry target = byRank.get(targetRank);
            if (target != null) {
                byRank.put(targetRank, new OwnershipEntry(
                        target.rank(), target.type(), target.ownerName(), target.receivedDate(), true));
            }
        }
    }

    private void applySeizureCancellations(List<String> entries, Map<Integer, SeizureEntry> byRank) {
        for (String block : entries) {
            Matcher entryStart = ENTRY_START.matcher(block);
            if (!entryStart.find()) continue;
            String purpose = entryStart.group(2);
            if (!purpose.contains("말소")) continue;

            Matcher targetMatcher = TARGET_RANK.matcher(purpose);
            if (!targetMatcher.find()) continue;
            int targetRank = Integer.parseInt(targetMatcher.group(1));
            SeizureEntry target = byRank.get(targetRank);
            if (target != null) {
                byRank.put(targetRank, new SeizureEntry(
                        target.rank(), target.type(), target.receivedDate(), true));
            }
        }
    }
}
