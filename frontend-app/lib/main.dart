import 'dart:async';

import 'package:flutter/material.dart';

import 'api/api_client.dart';
import 'api/lawyer_api.dart';
import 'api/push_service.dart';
import 'screens/admin/admin_home_screen.dart';
import 'screens/consultation/consultation_thread_screen.dart';
import 'screens/landing_screen.dart';
import 'screens/lawyer/consultation_list_screen.dart';
import 'screens/lawyer/lawyer_admin_messages_screen.dart';
import 'screens/lawyer_login_screen.dart';
import 'screens/login_screen.dart';
import 'screens/member/member_home_screen.dart';
import 'screens/signup_screen.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await PushService.init();
  runApp(ProjectGuardApp(api: ApiClient()));
}

/// 앱을 보고 있을 때 온 푸시를 어느 화면에서든 알림줄로 보여주기 위한 키.
final _messengerKey = GlobalKey<ScaffoldMessengerState>();

class ProjectGuardApp extends StatelessWidget {
  final ApiClient api;

  const ProjectGuardApp({super.key, required this.api});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Project Guard',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      scaffoldMessengerKey: _messengerKey,
      home: AuthGate(api: api),
    );
  }
}

/// 로그인 전에는 서비스 소개(랜딩) → 로그인/회원가입 순서로 보내고,
/// 로그인되어 있으면 계정 종류에 맞는 첫 화면(회원: 분석·상담 탭, 변호사: 상담 문의, 관리자: 관리 콘솔)을 보여준다.
class AuthGate extends StatefulWidget {
  final ApiClient api;

  const AuthGate({super.key, required this.api});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  late Future<Session?> _session = _loadSession();
  Session? _current;
  final _pushSubscriptions = <StreamSubscription<Object?>>[];

  @override
  void initState() {
    super.initState();
    _pushSubscriptions
      ..add(PushService.opened.listen(_openPushTarget))
      ..add(PushService.foreground.listen((event) => _showForegroundPush(event.$1, event.$2)));
  }

  @override
  void dispose() {
    for (final s in _pushSubscriptions) {
      s.cancel();
    }
    super.dispose();
  }

  Future<Session?> _loadSession() async {
    Session? session;
    try {
      session = await widget.api.currentSession();
    } catch (e, stack) {
      describeError(e, stack); // 원인을 로그에 남기고, 랜딩 화면에서 다시 시도한다.
    }
    _current = session;
    if (session != null && session.role != AccountRole.admin) {
      // 회원·변호사만 상담 알림을 받는다. 등록은 기다리지 않는다 (권한 창이 떠도 첫 화면은 바로 보이게).
      unawaited(PushService.register(widget.api));
      final initial = PushService.takeInitialTarget();
      if (initial != null) WidgetsBinding.instance.addPostFrameCallback((_) => _openPushTarget(initial));
    }
    return session;
  }

  /// 알림을 눌렀을 때 그 상담 대화방(관리자 메시지면 변호사 알림함)을 연다. 지금 로그인한 계정이 받는 사람일 때만 연다.
  void _openPushTarget(PushTarget target) {
    final session = _current;
    if (session == null || !mounted) return;
    if (target.isAdminMessage) {
      if (session.role != AccountRole.lawyer || target.recipientType != 'LAWYER') return;
      Navigator.of(context).push(MaterialPageRoute<void>(
        builder: (_) => LawyerAdminMessagesScreen(lawyerApi: LawyerApi(widget.api), onLoggedOut: _onAuthenticated),
      ));
      return;
    }
    final ConsultationChatApi chat;
    if (session.role == AccountRole.user && target.recipientType == 'USER') {
      chat = ConsultationChatApi.member(widget.api);
    } else if (session.role == AccountRole.lawyer && target.recipientType == 'LAWYER') {
      chat = ConsultationChatApi.lawyer(widget.api);
    } else {
      return;
    }
    if (PushService.activeConsultationId == target.consultationId) return;
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => ConsultationThreadScreen(
        chat: chat,
        consultationId: target.consultationId!,
        counterpartName: target.counterpartName,
        onLoggedOut: _onAuthenticated,
      ),
    ));
  }

  void _showForegroundPush(PushTarget target, String? body) {
    _messengerKey.currentState?.showSnackBar(SnackBar(
      content: Text('${target.counterpartName}: ${body ?? '새 메시지가 왔어요'}', maxLines: 2, overflow: TextOverflow.ellipsis),
      action: SnackBarAction(label: '열기', onPressed: () => _openPushTarget(target)),
    ));
  }

  void _refresh() => setState(() {
        _session = _loadSession();
      });

  /// 로그인/회원가입 화면을 모두 닫고 계정 종류에 맞는 첫 화면으로 전환한다.
  void _onAuthenticated() {
    Navigator.of(context).popUntil((route) => route.isFirst);
    _refresh();
  }

  void _openLogin({bool replace = false}) => _open(
        (_) => LoginScreen(
          api: widget.api,
          onLoggedIn: _onAuthenticated,
          onSignup: () => _openSignup(replace: true),
          onLawyerLogin: _openLawyerLogin,
        ),
        replace,
      );

  void _openSignup({bool replace = false}) => _open(
        (_) => SignupScreen(
          api: widget.api,
          onSignedUp: _onAuthenticated,
          onLogin: () => _openLogin(replace: true),
        ),
        replace,
      );

  void _openLawyerLogin() => _open(
        (_) => LawyerLoginScreen(api: widget.api, onLoggedIn: _onAuthenticated),
        false,
      );

  void _open(WidgetBuilder builder, bool replace) {
    final route = MaterialPageRoute<void>(builder: builder);
    replace ? Navigator.of(context).pushReplacement(route) : Navigator.of(context).push(route);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Session?>(
      future: _session,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Scaffold(body: Center(child: CircularProgressIndicator()));
        }
        final session = snapshot.data;
        if (session == null) {
          return LandingScreen(onLogin: _openLogin, onSignup: _openSignup);
        }
        if (session.role == AccountRole.lawyer) {
          return ConsultationListScreen(api: widget.api, session: session, onLoggedOut: _refresh);
        }
        if (session.role == AccountRole.admin) {
          return AdminHomeScreen(api: widget.api, session: session, onLoggedOut: _refresh);
        }
        return MemberHomeScreen(api: widget.api, session: session, onLoggedOut: _refresh);
      },
    );
  }
}
