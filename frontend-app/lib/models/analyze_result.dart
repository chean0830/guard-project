/// 백엔드 `POST /api/analyze` 응답(AnalyzeResponse)을 그대로 옮긴 모델.
class AnalyzeResult {
  final RegistryAnalysis registry;
  final int? marketPrice;
  final BuildingInfo? buildingInfo;
  final List<RiskSignal> riskSignals;
  final bool hasHighRisk;
  final List<ChecklistItem> checklist;
  final String disclaimer;

  AnalyzeResult({
    required this.registry,
    required this.marketPrice,
    required this.buildingInfo,
    required this.riskSignals,
    required this.hasHighRisk,
    required this.checklist,
    required this.disclaimer,
  });

  factory AnalyzeResult.fromJson(Map<String, dynamic> json) => AnalyzeResult(
        registry: RegistryAnalysis.fromJson(json['registry'] as Map<String, dynamic>),
        marketPrice: (json['marketPrice'] as num?)?.toInt(),
        buildingInfo: json['buildingInfo'] == null
            ? null
            : BuildingInfo.fromJson(json['buildingInfo'] as Map<String, dynamic>),
        riskSignals: (json['riskSignals'] as List)
            .map((e) => RiskSignal.fromJson(e as Map<String, dynamic>))
            .toList(),
        hasHighRisk: json['hasHighRisk'] as bool,
        checklist: (json['checklist'] as List)
            .map((e) => ChecklistItem.fromJson(e as Map<String, dynamic>))
            .toList(),
        disclaimer: json['disclaimer'] as String,
      );
}

class RegistryAnalysis {
  final String? address;
  final String? uniqueNumber;
  final List<OwnershipEntry> ownershipHistory;
  final List<MortgageEntry> mortgages;
  final List<SeizureEntry> seizures;
  final int totalActiveMortgageAmount;

  RegistryAnalysis({
    required this.address,
    required this.uniqueNumber,
    required this.ownershipHistory,
    required this.mortgages,
    required this.seizures,
    required this.totalActiveMortgageAmount,
  });

  factory RegistryAnalysis.fromJson(Map<String, dynamic> json) => RegistryAnalysis(
        address: json['address'] as String?,
        uniqueNumber: json['uniqueNumber'] as String?,
        ownershipHistory: (json['ownershipHistory'] as List)
            .map((e) => OwnershipEntry.fromJson(e as Map<String, dynamic>))
            .toList(),
        mortgages: (json['mortgages'] as List)
            .map((e) => MortgageEntry.fromJson(e as Map<String, dynamic>))
            .toList(),
        seizures: (json['seizures'] as List)
            .map((e) => SeizureEntry.fromJson(e as Map<String, dynamic>))
            .toList(),
        totalActiveMortgageAmount: (json['totalActiveMortgageAmount'] as num).toInt(),
      );
}

class OwnershipEntry {
  final int rank;
  final String type;
  final String? ownerName;
  final String? receivedDate;
  final bool cancelled;

  OwnershipEntry({
    required this.rank,
    required this.type,
    required this.ownerName,
    required this.receivedDate,
    required this.cancelled,
  });

  factory OwnershipEntry.fromJson(Map<String, dynamic> json) => OwnershipEntry(
        rank: json['rank'] as int,
        type: json['type'] as String,
        ownerName: json['ownerName'] as String?,
        receivedDate: json['receivedDate'] as String?,
        cancelled: json['cancelled'] as bool,
      );
}

class MortgageEntry {
  final int rank;
  final int maxClaimAmount;
  final String? debtorName;
  final String? mortgageeName;
  final String? receivedDate;
  final bool cancelled;

  MortgageEntry({
    required this.rank,
    required this.maxClaimAmount,
    required this.debtorName,
    required this.mortgageeName,
    required this.receivedDate,
    required this.cancelled,
  });

  factory MortgageEntry.fromJson(Map<String, dynamic> json) => MortgageEntry(
        rank: json['rank'] as int,
        maxClaimAmount: (json['maxClaimAmount'] as num).toInt(),
        debtorName: json['debtorName'] as String?,
        mortgageeName: json['mortgageeName'] as String?,
        receivedDate: json['receivedDate'] as String?,
        cancelled: json['cancelled'] as bool,
      );
}

class SeizureEntry {
  final int rank;
  final String type;
  final String? receivedDate;
  final bool cancelled;

  SeizureEntry({
    required this.rank,
    required this.type,
    required this.receivedDate,
    required this.cancelled,
  });

  factory SeizureEntry.fromJson(Map<String, dynamic> json) => SeizureEntry(
        rank: json['rank'] as int,
        type: json['type'] as String,
        receivedDate: json['receivedDate'] as String?,
        cancelled: json['cancelled'] as bool,
      );
}

class BuildingInfo {
  final String? buildingName;
  final String? mainPurpose;
  final String? structureType;
  final String? useApprovalDate;
  final double? totalFloorAreaSqm;

  BuildingInfo({
    required this.buildingName,
    required this.mainPurpose,
    required this.structureType,
    required this.useApprovalDate,
    required this.totalFloorAreaSqm,
  });

  factory BuildingInfo.fromJson(Map<String, dynamic> json) => BuildingInfo(
        buildingName: json['buildingName'] as String?,
        mainPurpose: json['mainPurpose'] as String?,
        structureType: json['structureType'] as String?,
        useApprovalDate: json['useApprovalDate'] as String?,
        totalFloorAreaSqm: (json['totalFloorAreaSqm'] as num?)?.toDouble(),
      );
}

class RiskSignal {
  final String code;
  final String title;
  final String severity; // HIGH, CAUTION, INFO
  final String source; // FACTUAL, LAW, GOVERNMENT_GUIDELINE
  final String sourceDescription;
  final String detail;

  RiskSignal({
    required this.code,
    required this.title,
    required this.severity,
    required this.source,
    required this.sourceDescription,
    required this.detail,
  });

  factory RiskSignal.fromJson(Map<String, dynamic> json) => RiskSignal(
        code: json['code'] as String,
        title: json['title'] as String,
        severity: json['severity'] as String,
        source: json['source'] as String,
        sourceDescription: json['sourceDescription'] as String,
        detail: json['detail'] as String,
      );
}

class ChecklistItem {
  final String title;
  final String description;

  ChecklistItem({required this.title, required this.description});

  factory ChecklistItem.fromJson(Map<String, dynamic> json) => ChecklistItem(
        title: json['title'] as String,
        description: json['description'] as String,
      );
}
