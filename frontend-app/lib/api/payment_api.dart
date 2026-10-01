import 'api_client.dart';

/// 결제 API (`/api/payments/...`). 금액은 항상 서버가 정하고, 승인도 서버가 토스에 금액을 대조한 뒤 한다.
class PaymentApi {
  final ApiClient _api;

  PaymentApi(this._api);

  /// 결제 주문을 만든다. productType: ANALYSIS(등기부 추가 분석 990원) 또는 LAWYER_SELECTION(변호사 직접 선택 2,900원).
  Future<PaymentOrder> createOrder(String productType) async => PaymentOrder.fromJson(
      await _api.authedJson('POST', '/api/payments/orders', body: {'productType': productType}) as Map<String, dynamic>);

  /// 토스 결제창에서 인증이 끝난 결제를 승인한다. 돌려받는 값은 그 상품의 남은 이용권 수.
  Future<int> confirm({required String paymentKey, required String orderId, required int amount}) async {
    final res = await _api.authedJson('POST', '/api/payments/confirm',
        body: {'paymentKey': paymentKey, 'orderId': orderId, 'amount': amount}) as Map<String, dynamic>;
    return (res['credits'] as num).toInt();
  }

  /// 변호사 직접 선택 이용권 수와 1회 가격.
  Future<({int credits, int price})> lawyerSelectionCredits() async {
    final res = await _api.authedJson('GET', '/api/payments/credits') as Map<String, dynamic>;
    return (credits: (res['credits'] as num).toInt(), price: (res['price'] as num).toInt());
  }

  Future<List<PaymentHistoryItem>> history() async {
    final list = await _api.authedJson('GET', '/api/payments/history') as List;
    return list.map((e) => PaymentHistoryItem.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 쓰지 않은 이용권 결제를 바로 취소한다.
  Future<PaymentHistoryItem> cancel(String orderId) async => PaymentHistoryItem.fromJson(
      await _api.authedJson('POST', '/api/payments/$orderId/cancel') as Map<String, dynamic>);

  /// 이미 쓴 이용권은 환불을 요청한다 (관리자 승인 후 환불).
  Future<PaymentHistoryItem> requestRefund(String orderId, String reason) async => PaymentHistoryItem.fromJson(
      await _api.authedJson('POST', '/api/payments/$orderId/refund-request', body: {'reason': reason})
          as Map<String, dynamic>);

  /// 변호사 직접 선택용 목록. 이용권이 없으면 402로 거절된다.
  Future<List<LawyerCard>> directory() async {
    final list = await _api.authedJson('GET', '/api/lawyers/directory') as List;
    return list.map((e) => LawyerCard.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 고른 변호사와 상담을 시작한다 (이용권 1장 사용). 만들어진 상담 id를 돌려준다.
  Future<int> startDirectConsultation(int lawyerId, String message) async {
    final res = await _api.authedJson('POST', '/api/consultations/direct', body: {'lawyerId': lawyerId, 'message': message})
        as Map<String, dynamic>;
    return (res['id'] as num).toInt();
  }
}

class PaymentOrder {
  final String orderId;
  final int amount;
  final String orderName;

  PaymentOrder({required this.orderId, required this.amount, required this.orderName});

  factory PaymentOrder.fromJson(Map<String, dynamic> json) => PaymentOrder(
        orderId: json['orderId'] as String,
        amount: (json['amount'] as num).toInt(),
        orderName: json['orderName'] as String,
      );
}

class PaymentHistoryItem {
  final String orderId;
  final String productType;
  final String orderName;
  final int amount;

  /// READY, PAID, FAILED, CANCELED, REFUND_REQUESTED, REFUNDED
  final String status;
  final DateTime? paidAt;
  final DateTime? canceledAt;
  final bool used;
  final String? refundReason;
  final String? refundRejectedReason;

  PaymentHistoryItem({
    required this.orderId,
    required this.productType,
    required this.orderName,
    required this.amount,
    required this.status,
    this.paidAt,
    this.canceledAt,
    required this.used,
    this.refundReason,
    this.refundRejectedReason,
  });

  /// 결제됐고 아직 안 쓴 이용권 → 바로 취소 가능.
  bool get canCancel => status == 'PAID' && !used;

  /// 결제됐고 이미 쓴 이용권 → 환불 요청 가능 (거절된 적이 있어도 다시 요청할 수 있게 웹과 같이 둔다).
  bool get canRequestRefund => status == 'PAID' && used;

  factory PaymentHistoryItem.fromJson(Map<String, dynamic> json) => PaymentHistoryItem(
        orderId: json['orderId'] as String,
        productType: json['productType'] as String,
        orderName: json['orderName'] as String,
        amount: (json['amount'] as num).toInt(),
        status: json['status'] as String,
        paidAt: json['paidAt'] == null ? null : DateTime.parse(json['paidAt'] as String),
        canceledAt: json['canceledAt'] == null ? null : DateTime.parse(json['canceledAt'] as String),
        used: json['used'] as bool,
        refundReason: json['refundReason'] as String?,
        refundRejectedReason: json['refundRejectedReason'] as String?,
      );
}

class LawyerCard {
  final int id;
  final String name;
  final String? lawFirm;
  final String? specialties;
  final String? introduction;
  final String? headline;
  final int? careerYears;
  final String? feeInfo;
  final String? achievements;

  LawyerCard({
    required this.id,
    required this.name,
    this.lawFirm,
    this.specialties,
    this.introduction,
    this.headline,
    this.careerYears,
    this.feeInfo,
    this.achievements,
  });

  factory LawyerCard.fromJson(Map<String, dynamic> json) => LawyerCard(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String,
        lawFirm: json['lawFirm'] as String?,
        specialties: json['specialties'] as String?,
        introduction: json['introduction'] as String?,
        headline: json['headline'] as String?,
        careerYears: (json['careerYears'] as num?)?.toInt(),
        feeInfo: json['feeInfo'] as String?,
        achievements: json['achievements'] as String?,
      );
}

/// 2900 → "2,900원"
String formatWon(int amount) {
  final digits = amount.toString();
  final buf = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) buf.write(',');
    buf.write(digits[i]);
  }
  return '$buf원';
}
