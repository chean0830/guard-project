import 'package:flutter/material.dart';

/// 웹(frontend-web, Tailwind)과 같은 색을 쓴다: 흰 바탕 + 주황(orange-500) 포인트 + zinc 회색.
class AppColors {
  static const orange50 = Color(0xFFFFF7ED);
  static const orange100 = Color(0xFFFFEDD5);
  static const orange400 = Color(0xFFFB923C);
  static const orange500 = Color(0xFFF97316);
  static const orange600 = Color(0xFFEA580C);
  static const orange700 = Color(0xFFC2410C);

  static const zinc50 = Color(0xFFFAFAFA);
  static const zinc100 = Color(0xFFF4F4F5);
  static const zinc200 = Color(0xFFE4E4E7);
  static const zinc300 = Color(0xFFD4D4D8);
  static const zinc400 = Color(0xFFA1A1AA);
  static const zinc500 = Color(0xFF71717A);
  static const zinc600 = Color(0xFF52525B);
  static const zinc700 = Color(0xFF3F3F46);
  static const zinc800 = Color(0xFF27272A);
  static const zinc900 = Color(0xFF18181B);
  static const zinc950 = Color(0xFF09090B);

  static const red50 = Color(0xFFFEF2F2);
  static const red200 = Color(0xFFFECACA);
  static const red500 = Color(0xFFEF4444);
  static const red600 = Color(0xFFDC2626);
  static const red700 = Color(0xFFB91C1C);
  static const red800 = Color(0xFF991B1B);
  static const red950 = Color(0xFF450A0A);

  static const amber50 = Color(0xFFFFFBEB);
  static const amber200 = Color(0xFFFDE68A);
  static const amber500 = Color(0xFFF59E0B);
  static const amber900 = Color(0xFF78350F);

  static const emerald50 = Color(0xFFECFDF5);
  static const emerald300 = Color(0xFF6EE7B7);
  static const emerald600 = Color(0xFF059669);
  static const emerald900 = Color(0xFF064E3B);

  static const blue600 = Color(0xFF2563EB);
}

ThemeData buildAppTheme() {
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: AppColors.orange500,
      primary: AppColors.orange500,
      surface: Colors.white,
    ),
    scaffoldBackgroundColor: Colors.white,
  );

  OutlineInputBorder border(Color color, [double width = 1]) => OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide(color: color, width: width),
      );

  return base.copyWith(
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.white,
      foregroundColor: AppColors.zinc950,
      elevation: 0,
      scrolledUnderElevation: 0.5,
      centerTitle: false,
      titleTextStyle: TextStyle(
          fontSize: 16, fontWeight: FontWeight.w700, color: AppColors.zinc950, letterSpacing: -0.2),
    ),
    inputDecorationTheme: InputDecorationTheme(
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      hintStyle: const TextStyle(color: AppColors.zinc400, fontSize: 14),
      enabledBorder: border(AppColors.zinc300),
      disabledBorder: border(AppColors.zinc200),
      focusedBorder: border(AppColors.orange400, 1.5),
      errorBorder: border(AppColors.red500),
      focusedErrorBorder: border(AppColors.red500, 1.5),
      filled: true,
      fillColor: Colors.white,
    ),
    dividerTheme: const DividerThemeData(color: AppColors.zinc200, thickness: 1, space: 1),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    textTheme: base.textTheme.apply(bodyColor: AppColors.zinc950, displayColor: AppColors.zinc950),
  );
}
