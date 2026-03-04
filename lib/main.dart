import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'l10n/app_localizations.dart';
import 'data/database.dart';
import 'providers/providers.dart';
import 'screens/splash_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppDatabase.initialize();

  runApp(const ProviderScope(
    child: AutoLottoApp(),
  ));
}

class AutoLottoApp extends ConsumerWidget {
  const AutoLottoApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locale = ref.watch(appLocaleProvider);

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AutoLotto',
      locale: locale,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF2D5BFF),
        useMaterial3: true,
        listTileTheme: const ListTileThemeData(
          titleTextStyle: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w500,
            color: Colors.black87,
          ),
          subtitleTextStyle: TextStyle(
            fontSize: 13,
            color: Colors.black54,
          ),
        ),
      ),
      home: const SplashScreen(),
    );
  }
}
