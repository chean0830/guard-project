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

    private static final Pattern GAPGU_HEADER = Pattern.compile("갑\\s*구");
    private static final Pattern EULGU_HEADER = Pattern.compile("을\\s*구");
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
