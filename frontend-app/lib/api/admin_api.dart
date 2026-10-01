import 'api_client.dart';
import 'payment_api.dart' show PaymentHistoryItem;

/// 관리자 API (`/api/admin/...`): 변호사 가입 승인, 신고 처리, 회원 이용 정지, 결제 환불.
class AdminApi {
  final ApiClient _api;

  AdminApi(this._api);

  // ── 변호사 가입 승인 ──

  /// status: PENDING(승인 대기) / APPROVED / REJECTED
  Future<List<AdminLawyer>> lawyers(String status) async {
    final list = await _api.authedJson('GET', '/api/admin/lawyers?status=$status') as List;
    return list.map((e) => AdminLawyer.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<({List<int> bytes, String contentType})> lawyerDocument(int lawyerId, int documentId) =>
      _api.authedBytes('/api/admin/lawyers/$lawyerId/documents/$documentId');

  Future<void> approveLawyer(int id) => _api.authedJson('POST', '/api/admin/lawyers/$id/approve');

  Future<void> rejectLawyer(int id, String reason) =>
      _api.authedJson('POST', '/api/admin/lawyers/$id/reject', body: {'reason': reason});

  // ── 신고 ──

  /// status: PENDING(처리 대기) / ACTIONED(정지 처리됨) / DISMISSED(기각됨)
  Future<List<AdminReport>> reports(String status) async {
    final list = await _api.authedJson('GET', '/api/admin/reports?status=$status') as List;
    return list.map((e) => AdminReport.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 신고된 대화방의 원문 전체.
  Future<List<ReportMessage>> reportMessages(int reportId) async {
    final list = await _api.authedJson('GET', '/api/admin/reports/$reportId/messages') as List;
    return list.map((e) => ReportMessage.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 기준 충족 → 신고 대상 이용 정지 (같은 대상의 다른 대기 신고도 함께 처리된다).
  Future<void> actionReport(int id) => _api.authedJson('POST', '/api/admin/reports/$id/action');

  Future<void> dismissReport(int id) => _api.authedJson('POST', '/api/admin/reports/$id/dismiss');

  // ── 회원 관리 ──

  /// type: USER(회원) / LAWYER(변호사). 20명씩 페이지로 나눠 온다.
  Future<MemberPage> members(String type, {int page = 0, String query = ''}) async {
    final path = type == 'USER' ? 'users' : 'lawyers';
    final q = query.trim().isEmpty ? '' : '&q=${Uri.encodeQueryComponent(query.trim())}';
    final json = await _api.authedJson('GET', '/api/admin/members/$path?page=$page$q') as Map<String, dynamic>;
    return MemberPage.fromJson(json);
  }

  Future<void> setMemberBlocked(String type, int id, bool blocked, {String? reason}) {
    final path = type == 'USER' ? 'users' : 'lawyers';
    return _api.authedJson('POST', '/api/admin/members/$path/$id/${blocked ? 'block' : 'unblock'}',
        body: blocked ? {'reason': reason ?? ''} : null);
  }

  // ── 결제 ──

  /// status가 null이면 전체 결제.
  Future<List<AdminPayment>> payments(String? status) async {
    final list = await _api.authedJson('GET', '/api/admin/payments${status == null ? '' : '?status=$status'}') as List;
    return list.map((e) => AdminPayment.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 환불 승인 → 토스에서 결제가 취소된다.
  Future<void> approveRefund(String orderId) => _api.authedJson('POST', '/api/admin/payments/$orderId/refund/approve');

  Future<void> rejectRefund(String orderId, String reason) =>
      _api.authedJson('POST', '/api/admin/payments/$orderId/refund/reject', body: {'reason': reason});
}

DateTime? _date(Object? v) => v == null ? null : DateTime.parse(v as String);

class AdminLawyerDocument {
  final int id;
  final String fileName;
  final String contentType;

  AdminLawyerDocument({required this.id, required this.fileName, required this.contentType});

  factory AdminLawyerDocument.fromJson(Map<String, dynamic> json) => AdminLawyerDocument(
        id: (json['id'] as num).toInt(),
        fileName: json['fileName'] as String,
        contentType: json['contentType'] as String? ?? 'application/octet-stream',
      );
}

class AdminLawyer {
  final int id;
  final String email;
  final String name;
  final String? lawFirm;
  final String barNumber;
  final String status;
  final String? rejectionReason;
  final DateTime? createdAt;
  final List<AdminLawyerDocument> documents;

  AdminLawyer({
    required this.id,
    required this.email,
    required this.name,
    this.lawFirm,
    required this.barNumber,
    required this.status,
    this.rejectionReason,
    this.createdAt,
    required this.documents,
  });

  factory AdminLawyer.fromJson(Map<String, dynamic> json) => AdminLawyer(
        id: (json['id'] as num).toInt(),
        email: json['email'] as String,
        name: json['name'] as String,
        lawFirm: json['lawFirm'] as String?,
        barNumber: json['barNumber'] as String? ?? '-',
        status: json['status'] as String,
        rejectionReason: json['rejectionReason'] as String?,
        createdAt: _date(json['createdAt']),
        documents: [
          for (final d in (json['documents'] as List? ?? const [])) AdminLawyerDocument.fromJson(d as Map<String, dynamic>)
        ],
      );
}

class AdminReport {
  final int id;
  final int consultationId;
  final String reasonLabel;
  final String? detail;
  final String status;
  final DateTime? createdAt;
  final String reporterType;
  final String reporterName;

  /// USER 또는 LAWYER
  final String targetType;
  final String targetName;
  final String? targetEmail;
  final bool targetBlocked;
  final int targetReportCount;

  AdminReport({
    required this.id,
    required this.consultationId,
    required this.reasonLabel,
    this.detail,
    required this.status,
    this.createdAt,
    required this.reporterType,
    required this.reporterName,
    required this.targetType,
    required this.targetName,
    this.targetEmail,
    required this.targetBlocked,
    required this.targetReportCount,
  });

  factory AdminReport.fromJson(Map<String, dynamic> json) => AdminReport(
        id: (json['id'] as num).toInt(),
        consultationId: (json['consultationId'] as num).toInt(),
        reasonLabel: json['reasonLabel'] as String,
        detail: json['detail'] as String?,
        status: json['status'] as String,
        createdAt: _date(json['createdAt']),
        reporterType: json['reporterType'] as String,
        reporterName: json['reporterName'] as String,
        targetType: json['targetType'] as String,
        targetName: json['targetName'] as String,
        targetEmail: json['targetEmail'] as String?,
        targetBlocked: json['targetBlocked'] as bool,
        targetReportCount: (json['targetReportCount'] as num).toInt(),
      );
}

class ReportMessage {
  final String senderType;
  final String content;
  final DateTime? createdAt;

  ReportMessage({required this.senderType, required this.content, this.createdAt});

  factory ReportMessage.fromJson(Map<String, dynamic> json) => ReportMessage(
        senderType: json['senderType'] as String,
        content: json['content'] as String,
        createdAt: _date(json['createdAt']),
      );
}

/// 회원 관리 목록의 한 사람 (회원·변호사 공용).
class AdminMember {
  final int id;
  final String email;
  final String? name;

  /// 회원: 가입 방식(LOCAL/KAKAO 등), 변호사: 소속
  final String? subtitle;
  final bool blocked;
  final String? blockedReason;
  final int reportCount;

  AdminMember({
    required this.id,
    required this.email,
    this.name,
    this.subtitle,
    required this.blocked,
    this.blockedReason,
    required this.reportCount,
  });

  factory AdminMember.fromJson(Map<String, dynamic> json) => AdminMember(
        id: (json['id'] as num).toInt(),
        email: json['email'] as String,
        name: json['name'] as String?,
        subtitle: (json['lawFirm'] ?? json['provider']) as String?,
        blocked: json['blocked'] as bool,
        blockedReason: json['blockedReason'] as String?,
        reportCount: (json['reportCount'] as num).toInt(),
      );
}

class MemberPage {
  final List<AdminMember> items;
  final int page;
  final int totalPages;
  final int totalElements;

  MemberPage({required this.items, required this.page, required this.totalPages, required this.totalElements});

  factory MemberPage.fromJson(Map<String, dynamic> json) => MemberPage(
        items: [for (final e in json['items'] as List) AdminMember.fromJson(e as Map<String, dynamic>)],
        page: (json['page'] as num).toInt(),
        totalPages: (json['totalPages'] as num).toInt(),
        totalElements: (json['totalElements'] as num).toInt(),
      );
}

class AdminPayment {
  final PaymentHistoryItem payment;
  final int userId;
  final String userEmail;

  AdminPayment({required this.payment, required this.userId, required this.userEmail});

  factory AdminPayment.fromJson(Map<String, dynamic> json) => AdminPayment(
        payment: PaymentHistoryItem.fromJson(json['payment'] as Map<String, dynamic>),
        userId: (json['userId'] as num).toInt(),
        userEmail: json['userEmail'] as String,
      );
}
