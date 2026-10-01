import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import '../config.dart';
import '../models/analyze_result.dart';

/// 백엔드 호출 실패. 백엔드는 오류 메시지를 본문에 평문으로 내려준다.
class ApiException implements Exception {
  final int statusCode;
  final String message;

  ApiException(this.statusCode, this.message);

  bool get loginRequired => statusCode == 401;
  bool get paymentRequired => statusCode == 402;

  @override
  String toString() => message;
}

/// 화면에 보여줄 오류 문구. 네트워크 오류만 "서버 연결 실패"로 안내하고,
/// 그 밖의 오류는 원인을 숨기지 않도록 로그에 남기고 내용을 그대로 보여준다.
String describeError(Object error, StackTrace stack) {
  if (error is ApiException) return error.message;
  if (error is SocketException || error is http.ClientException || error is TimeoutException) {
    return '서버에 연결할 수 없습니다. 백엔드가 켜져 있는지 확인해주세요.';
  }
  debugPrint('예상하지 못한 오류: $error\n$stack');
  return '오류가 발생했습니다: $error';
}

/// 업로드할 등기부 한 장 (촬영 사진 또는 PDF).
class UploadFile {
  final String name;
  final List<int> bytes;
  final String contentType;

  UploadFile({required this.name, required this.bytes, required this.contentType});
}

class AnalyzeRequest {
  final List<UploadFile> files;
  final String propertyType;
  final String contractType;
  final int depositAmount;
  final int? monthlyRent;
  final String? buildingName;
  final double? exclusiveAreaSqm;
  final String? declaredLandlordName;
  final String? declaredAddress;

  AnalyzeRequest({
    required this.files,
    required this.propertyType,
    required this.contractType,
    required this.depositAmount,
    this.monthlyRent,
    this.buildingName,
    this.exclusiveAreaSqm,
    this.declaredLandlordName,
    this.declaredAddress,
  });
}

/// 로그인한 계정의 종류. 회원·변호사·관리자는 백엔드에서 로그인 API와 토큰이 서로 다르다.
enum AccountRole {
  user('/api/auth'),
  lawyer('/api/lawyer/auth'),
  admin('/api/admin/auth');

  final String authPath;

  const AccountRole(this.authPath);
}

class Session {
  final AccountRole role;
  final String email;

  /// 변호사 계정만 이름이 있다.
  final String? name;

  Session({required this.role, required this.email, this.name});
}

/// 백엔드는 쿠키가 아니라 `Authorization: Bearer <token>`으로 로그인을 확인한다.
/// 토큰은 기기 보안 저장소(Keystore/Keychain)에 보관한다.
class ApiClient {
  static const _tokenKey = 'auth_token';
  static const _emailKey = 'auth_email';
  static const _roleKey = 'auth_role';

  final http.Client _http;
  final FlutterSecureStorage _storage;

  ApiClient({http.Client? httpClient, FlutterSecureStorage? storage})
      : _http = httpClient ?? http.Client(),
        _storage = storage ?? const FlutterSecureStorage();

  Uri _uri(String path) => Uri.parse('${AppConfig.apiBaseUrl}$path');

  Future<String?> get token => _storage.read(key: _tokenKey);

  Future<Map<String, String>> _authHeaders() async {
    final t = await token;
    return t == null ? {} : {'Authorization': 'Bearer $t'};
  }

  Future<http.Response> _postJson(String path, Map<String, Object?> body) => _http.post(
        _uri(path),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(body),
      );

  /// 일반 로그인 화면. 웹과 같이 관리자도 이 화면에서 로그인한다 —
  /// 회원 로그인이 실패하면 관리자 계정인지 한 번 더 확인하고, 아니면 회원 로그인 오류를 그대로 보여준다.
  Future<Session> login(String email, String password) async {
    final credentials = {'email': email, 'password': password};
    final res = await _postJson('${AccountRole.user.authPath}/login', credentials);
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return _saveSession(res, AccountRole.user);
    }
    http.Response? adminRes;
    try {
      adminRes = await _postJson('${AccountRole.admin.authPath}/login', credentials);
    } catch (_) {
      // 관리자 확인이 실패해도 회원 로그인 오류를 보여주면 된다.
    }
    if (adminRes?.statusCode == 200) return _saveSession(adminRes!, AccountRole.admin);
    _throwIfFailed(res);
    throw StateError('unreachable');
  }

  /// 변호사 로그인. 관리자 승인이 끝난 계정만 로그인된다.
  Future<Session> lawyerLogin(String email, String password) async {
    final res = await _postJson('${AccountRole.lawyer.authPath}/login', {'email': email, 'password': password});
    return _saveSession(res, AccountRole.lawyer);
  }

  /// 회원가입 1단계: 이메일로 6자리 인증번호를 보낸다.
  Future<void> requestSignupCode(String email) async {
    _throwIfFailed(await _postJson('/api/auth/signup/code', {'email': email}));
  }

  /// 회원가입 2단계: 인증번호를 확인하고 가입용 1회용 토큰을 받는다.
  Future<String> verifySignupCode(String email, String code) async {
    final res = await _postJson('/api/auth/signup/verify', {'email': email, 'code': code});
    _throwIfFailed(res);
    return (jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>)['verificationToken'] as String;
  }

  /// 회원가입 3단계: 가입하면 바로 로그인된 상태가 된다.
  Future<Session> signup(String email, String password, String verificationToken) async {
    final res = await _postJson('/api/auth/signup', {
      'email': email,
      'password': password,
      'verificationToken': verificationToken,
    });
    return _saveSession(res, AccountRole.user);
  }

  Future<Session> _saveSession(http.Response res, AccountRole role) async {
    _throwIfFailed(res);
    final body = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    await _storage.write(key: _tokenKey, value: body['token'] as String);
    await _storage.write(key: _emailKey, value: body['email'] as String);
    await _storage.write(key: _roleKey, value: role.name);
    return Session(role: role, email: body['email'] as String, name: body['name'] as String?);
  }

  Future<AccountRole> _savedRole() async {
    final saved = await _storage.read(key: _roleKey);
    return AccountRole.values.firstWhere((r) => r.name == saved, orElse: () => AccountRole.user);
  }

  /// 저장된 토큰이 아직 유효하면 로그인 정보를, 아니면 null을 돌려준다.
  Future<Session?> currentSession() async {
    if (await token == null) return null;
    final role = await _savedRole();
    final res = await _http.get(_uri('${role.authPath}/me'), headers: await _authHeaders());
    if (res.statusCode != 200) {
      await _clearToken();
      return null;
    }
    final body = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return Session(role: role, email: body['email'] as String, name: body['name'] as String?);
  }

  Future<void> logout() async {
    try {
      final role = await _savedRole();
      await _http.post(_uri('${role.authPath}/logout'), headers: await _authHeaders());
    } finally {
      await _clearToken();
    }
  }

  /// 로그인 토큰을 붙여 JSON API를 호출한다. 응답 본문이 없으면 null을 돌려준다.
  /// 401이면 로그인이 풀린 것이므로 저장된 토큰을 지운다.
  Future<Object?> authedJson(String method, String path, {Object? body}) async {
    final request = http.Request(method, _uri(path))..headers.addAll(await _authHeaders());
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    final res = await http.Response.fromStream(await _http.send(request));
    if (res.statusCode == 401) await _clearToken();
    _throwIfFailed(res);
    final text = utf8.decode(res.bodyBytes);
    return text.trim().isEmpty ? null : jsonDecode(text);
  }

  /// 실시간 알림 소켓 주소 (http → ws, https → wss).
  Uri webSocketUri(String path, Map<String, String> query) {
    final base = Uri.parse(AppConfig.apiBaseUrl);
    return base.replace(scheme: base.scheme == 'https' ? 'wss' : 'ws', path: path, queryParameters: query);
  }

  Future<AnalyzeResult> analyze(AnalyzeRequest request) async {
    final multipart = http.MultipartRequest('POST', _uri('/api/analyze'))
      ..headers.addAll(await _authHeaders())
      ..fields['propertyType'] = request.propertyType
      ..fields['contractType'] = request.contractType
      ..fields['depositAmount'] = request.depositAmount.toString();

    void optional(String key, Object? value) {
      if (value != null && value.toString().trim().isNotEmpty) {
        multipart.fields[key] = value.toString().trim();
      }
    }

    optional('monthlyRent', request.monthlyRent);
    optional('buildingName', request.buildingName);
    optional('exclusiveAreaSqm', request.exclusiveAreaSqm);
    optional('declaredLandlordName', request.declaredLandlordName);
    optional('declaredAddress', request.declaredAddress);

    for (final f in request.files) {
      multipart.files.add(http.MultipartFile.fromBytes(
        'files',
        f.bytes,
        filename: f.name,
        contentType: MediaType.parse(f.contentType),
      ));
    }

    final res = await http.Response.fromStream(await _http.send(multipart));
    if (res.statusCode == 401) await _clearToken();
    _throwIfFailed(res);
    return AnalyzeResult.fromJson(
        jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>);
  }

  Future<void> _clearToken() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _emailKey);
    await _storage.delete(key: _roleKey);
  }

  void _throwIfFailed(http.Response res) {
    if (res.statusCode >= 200 && res.statusCode < 300) return;
    final text = utf8.decode(res.bodyBytes).trim();
    throw ApiException(
      res.statusCode,
      text.isNotEmpty && !text.startsWith('{') ? text : '요청을 처리하지 못했습니다. (${res.statusCode})',
    );
  }
}
