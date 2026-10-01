import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

/// 입장권으로 여는 실시간 알림 소켓을 유지한다 (상담 목록·대화방 공통).
/// 끊기면 3초 뒤 다시 연결하고, 연결될 때마다 [onConnected]를 불러 끊긴 사이의 변화를 채우게 한다.
class LiveSocket {
  final Future<WebSocket> Function() connectSocket;
  final void Function(Object? data) onData;
  final Future<void> Function()? onConnected;

  /// 지금 연결돼 있는지 (화면의 "실시간 연결됨" 표시용).
  final ValueNotifier<bool> connected = ValueNotifier(false);

  WebSocket? _socket;
  Timer? _retry;
  bool _closed = false;

  LiveSocket({required this.connectSocket, required this.onData, this.onConnected});

  /// 연결을 시작한다. 연결에 실패하면 [onError]를 부르고 재시도한다 (로그인 만료 처리 등에 쓴다).
  Future<void> start({void Function(Object error)? onError}) async {
    if (_closed) return;
    try {
      final socket = await connectSocket();
      if (_closed) {
        socket.close();
        return;
      }
      _socket = socket;
      socket.listen(onData, onDone: _scheduleRetry, onError: (_) => _scheduleRetry(), cancelOnError: true);
      connected.value = true;
      await onConnected?.call();
    } catch (e) {
      onError?.call(e);
      _scheduleRetry(onError: onError);
    }
  }

  void _scheduleRetry({void Function(Object error)? onError}) {
    if (_closed) return;
    connected.value = false;
    _socket = null;
    _retry?.cancel();
    _retry = Timer(const Duration(seconds: 3), () => start(onError: onError));
  }

  void close() {
    _closed = true;
    _retry?.cancel();
    _socket?.close();
    connected.dispose();
  }
}
