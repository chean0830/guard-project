import 'package:flutter/material.dart';

import 'api/api_client.dart';
import 'screens/account_home_screen.dart';
import 'screens/landing_screen.dart';
import 'screens/lawyer/consultation_list_screen.dart';
import 'screens/lawyer_login_screen.dart';
import 'screens/login_screen.dart';
import 'screens/member/member_home_screen.dart';
import 'screens/signup_screen.dart';
import 'theme.dart';

void main() {
  runApp(ProjectGuardApp(api: ApiClient()));
}

class ProjectGuardApp extends StatelessWidget {
  final ApiClient api;

  const ProjectGuardApp({super.key, required this.api});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Project Guard',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      home: AuthGate(api: api),
    );
  }
}

/// 로그인 전에는 서비스 소개(랜딩) → 로그인/회원가입 순서로 보내고,
/// 로그인되어 있으면 계정 종류에 맞는 첫 화면(회원: 분석·상담 탭, 변호사: 상담 문의, 관리자: 계정 홈)을 보여준다.
class AuthGate extends StatefulWidget {
  final ApiClient api;

  const AuthGate({super.key, required this.api});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  late Future<Session?> _session = _loadSession();

  Future<Session?> _loadSession() async {
    try {
      return await widget.api.currentSession();
    } catch (e, stack) {
      describeError(e, stack); // 원인을 로그에 남기고, 랜딩 화면에서 다시 시도한다.
      return null;
    }
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
          return AccountHomeScreen(api: widget.api, session: session, onLoggedOut: _refresh);
        }
        return MemberHomeScreen(api: widget.api, session: session, onLoggedOut: _refresh);
      },
    );
  }
}
