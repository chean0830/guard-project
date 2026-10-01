import 'package:flutter_test/flutter_test.dart';
import 'package:project_guard/models/analyze_result.dart';

void main() {
  test('백엔드 분석 응답을 모델로 변환한다', () {
    final result = AnalyzeResult.fromJson({
      'registry': {
        'address': '서울특별시 강남구 테스트로 123',
        'uniqueNumber': '1101-2020-000001',
        'ownershipHistory': [
          {'rank': 1, 'type': 'OWNERSHIP_TRANSFER', 'ownerName': '홍길동', 'receivedDate': '2020년1월2일', 'cancelled': false},
        ],
        'mortgages': [
          {
            'rank': 1,
            'maxClaimAmount': 120000000,
            'debtorName': '홍길동',
            'mortgageeName': '테스트은행',
            'receivedDate': '2020년1월2일',
            'cancelled': false,
          },
        ],
        'seizures': [],
        'totalActiveMortgageAmount': 120000000,
      },
      'marketPrice': null,
      'buildingInfo': null,
      'riskSignals': [
        {
          'code': 'HIGH_LTV',
          'title': '근저당이 시세 대비 높아요',
          'severity': 'HIGH',
          'source': 'GOVERNMENT_GUIDELINE',
          'sourceDescription': '국토교통부',
          'detail': '설명',
        },
      ],
      'hasHighRisk': true,
      'checklist': [
        {'title': '임대인 신분증 확인', 'description': '설명'},
      ],
      'disclaimer': '이 서비스는 법률 조언이 아니며 참고용 정보입니다.',
    });

    expect(result.hasHighRisk, isTrue);
    expect(result.marketPrice, isNull);
    expect(result.buildingInfo, isNull);
    expect(result.registry.mortgages.single.maxClaimAmount, 120000000);
    expect(result.registry.ownershipHistory.single.ownerName, '홍길동');
    expect(result.riskSignals.single.severity, 'HIGH');
    expect(result.checklist, hasLength(1));
  });
}
