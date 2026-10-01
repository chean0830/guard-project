import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../api/api_client.dart';
import '../../api/payment_api.dart';
import '../../config.dart';
import '../../theme.dart';

/// 결제 결과. 성공하면 서버 승인까지 끝난 상태다.
enum PaymentOutcome { paid, canceled, failed }

/// 토스 결제창을 앱 안(WebView)에서 띄운다 (웹과 같은 토스 결제창 SDK 사용).
///
/// 1) 서버에 주문을 만들고 → 2) 토스 결제창을 띄운 뒤 → 3) 결제창이 successUrl로 돌아오려는 순간을 가로채
/// paymentKey·orderId·amount를 꺼내 → 4) 서버에 승인을 요청한다. 금액은 서버가 자기 주문과 대조한다.
class TossPaymentScreen extends StatefulWidget {
  final PaymentApi paymentApi;
  final String productType;

  const TossPaymentScreen({super.key, required this.paymentApi, required this.productType});

  /// 결제 화면을 띄우고 결과를 돌려준다. 실패하면 이유를 알림으로 보여준다.
  static Future<PaymentOutcome> open(BuildContext context, PaymentApi paymentApi, String productType) async {
    if (AppConfig.tossClientKey.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('결제 설정(TOSS_CLIENT_KEY)이 없어 결제할 수 없어요. 앱 실행 방법을 확인해주세요.')));
      return PaymentOutcome.failed;
    }
    final result = await Navigator.of(context).push<(PaymentOutcome, String?)>(MaterialPageRoute(
      builder: (_) => TossPaymentScreen(paymentApi: paymentApi, productType: productType),
    ));
    final (outcome, message) = result ?? (PaymentOutcome.canceled, null);
    if (message != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    }
    return outcome;
  }

  @override
  State<TossPaymentScreen> createState() => _TossPaymentScreenState();
}

// 결제창이 돌아오는 가짜 주소. 실제로 열지 않고 WebView에서 가로채기만 한다.
const _successUrl = 'https://app.projectguard.local/payment/success';
const _failUrl = 'https://app.projectguard.local/payment/fail';

class _TossPaymentScreenState extends State<TossPaymentScreen> {
  late final WebViewController _web;
  bool _confirming = false;
  bool _done = false;

  @override
  void initState() {
    super.initState();
    _web = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.white)
      ..addJavaScriptChannel('PaymentBridge', onMessageReceived: (m) => _fail(m.message))
      ..setNavigationDelegate(NavigationDelegate(onNavigationRequest: _onNavigation));
    _start();
  }

  Future<void> _start() async {
    try {
      final order = await widget.paymentApi.createOrder(widget.productType);
      if (!mounted) return;
      await _web.loadHtmlString(_paymentPage(order), baseUrl: 'https://app.projectguard.local/');
    } catch (e, stack) {
      _finish(PaymentOutcome.failed, describeError(e, stack));
    }
  }

  /// 토스 결제창이 카드사 앱(intent://, ispmobile:// 등)을 열거나 결과 주소로 돌아올 때를 처리한다.
  NavigationDecision _onNavigation(NavigationRequest request) {
    final uri = Uri.parse(request.url);
    if (request.url.startsWith(_successUrl)) {
      _confirm(uri.queryParameters);
      return NavigationDecision.prevent;
    }
    if (request.url.startsWith(_failUrl)) {
      final code = uri.queryParameters['code'];
      _finish(code == 'PAY_PROCESS_CANCELED' ? PaymentOutcome.canceled : PaymentOutcome.failed,
          code == 'PAY_PROCESS_CANCELED' ? '결제를 취소했어요.' : (uri.queryParameters['message'] ?? '결제에 실패했어요.'));
      return NavigationDecision.prevent;
    }
    if (uri.scheme != 'http' && uri.scheme != 'https' && uri.scheme != 'about' && uri.scheme != 'data') {
      _openExternalApp(request.url);
      return NavigationDecision.prevent;
    }
    return NavigationDecision.navigate;
  }

  /// 카드사·간편결제 앱 실행. 안드로이드 intent:// 주소는 앱이 없으면 마켓 주소(fallback)로 연다.
  Future<void> _openExternalApp(String url) async {
    // url_launcher는 intent:// 를 직접 못 연다 → intent://HOST#Intent;scheme=X;...;end 를 X://HOST 로 바꾼다.
    var target = url;
    if (url.startsWith('intent://')) {
      final scheme = RegExp(r'#Intent;.*?scheme=([^;]+)').firstMatch(url)?.group(1);
      if (scheme != null) target = '$scheme://${url.substring('intent://'.length).split('#Intent').first}';
    }
    try {
      if (await launchUrl(Uri.parse(target), mode: LaunchMode.externalApplication)) return;
    } catch (_) {
      // 아래 fallback으로 넘어간다.
    }
    final fallback = RegExp(r'S\.browser_fallback_url=([^;]+)').firstMatch(url)?.group(1);
    final package = RegExp(r'package=([^;]+)').firstMatch(url)?.group(1);
    final store = fallback != null
        ? Uri.decodeComponent(fallback)
        : package != null
            ? 'market://details?id=$package'
            : null;
    if (store != null) await launchUrl(Uri.parse(store), mode: LaunchMode.externalApplication);
  }

  Future<void> _confirm(Map<String, String> params) async {
    if (_confirming || _done) return;
    final paymentKey = params['paymentKey'];
    final orderId = params['orderId'];
    final amount = int.tryParse(params['amount'] ?? '');
    if (paymentKey == null || orderId == null || amount == null) {
      return _finish(PaymentOutcome.failed, '결제 정보가 올바르지 않습니다.');
    }
    setState(() => _confirming = true);
    try {
      await widget.paymentApi.confirm(paymentKey: paymentKey, orderId: orderId, amount: amount);
      _finish(PaymentOutcome.paid, '결제가 완료됐어요.');
    } catch (e, stack) {
      _finish(PaymentOutcome.failed, describeError(e, stack));
    }
  }

  void _fail(String message) => _finish(PaymentOutcome.failed, message);

  void _finish(PaymentOutcome outcome, String? message) {
    if (_done || !mounted) return;
    _done = true;
    Navigator.of(context).pop((outcome, message));
  }

  /// 웹(analysis-payment-button, lawyer-selection-payment)과 같은 방식으로 결제창을 연다.
  String _paymentPage(PaymentOrder order) {
    final config = jsonEncode({
      'clientKey': AppConfig.tossClientKey,
      'orderId': order.orderId,
      'orderName': order.orderName,
      'amount': order.amount,
      'successUrl': _successUrl,
      'failUrl': _failUrl,
    });
    return '''<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://js.tosspayments.com/v2/standard"></script></head>
<body style="margin:0;font-family:sans-serif">
<script>
(async function () {
  const c = $config;
  try {
    const toss = TossPayments(c.clientKey);
    const payment = toss.payment({ customerKey: TossPayments.ANONYMOUS });
    await payment.requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: c.amount },
      orderId: c.orderId,
      orderName: c.orderName,
      successUrl: c.successUrl,
      failUrl: c.failUrl,
    });
  } catch (e) {
    if (e && e.code === 'USER_CANCEL') { location.href = c.failUrl + '?code=PAY_PROCESS_CANCELED'; return; }
    PaymentBridge.postMessage((e && e.message) || '결제창을 열지 못했어요.');
  }
})();
</script></body></html>''';
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_confirming,
      onPopInvokedWithResult: (didPop, _) {
        // 뒤로가기로 닫으면 취소로 본다 (pop이 이미 결과 없이 일어났다).
        if (didPop) _done = true;
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('결제', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          bottom: const PreferredSize(preferredSize: Size.fromHeight(1), child: Divider()),
        ),
        body: Stack(children: [
          WebViewWidget(controller: _web),
          if (_confirming)
            Container(
              color: Colors.white.withValues(alpha: 0.9),
              alignment: Alignment.center,
              child: const Column(mainAxisSize: MainAxisSize.min, children: [
                CircularProgressIndicator(color: AppColors.orange500),
                SizedBox(height: 12),
                Text('결제를 확인하고 있어요...', style: TextStyle(fontSize: 14, color: AppColors.zinc600)),
              ]),
            ),
        ]),
      ),
    );
  }
}
