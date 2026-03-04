import 'package:flutter/material.dart';
import 'l10n/app_localizations.dart';
import 'screens/home_screen.dart';
import 'screens/number_screen.dart';
import 'screens/history_screen.dart';
import 'screens/settings_screen.dart';

class AppShell extends StatefulWidget {
  const AppShell({super.key});

  static _AppShellState? of(BuildContext context) =>
      context.findAncestorStateOfType<_AppShellState>();

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _currentIndex = 0;
  late final PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void switchTab(int index) {
    _pageController.animateToPage(
      index,
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeInOut,
    );
  }

  final _screens = const [
    HomeScreen(),
    NumberScreen(),
    HistoryScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: PageView(
        controller: _pageController,
        onPageChanged: (i) => setState(() => _currentIndex = i),
        children: _screens,
      ),
      bottomNavigationBar: BottomNavigationBar(
        type: BottomNavigationBarType.fixed,
        selectedItemColor: const Color(0xFF2D5BFF),
        unselectedItemColor: Colors.grey[400],
        backgroundColor: Colors.white,
        elevation: 8,
        currentIndex: _currentIndex,
        onTap: switchTab,
        items: [
          BottomNavigationBarItem(icon: const Icon(Icons.home_rounded), label: AppLocalizations.of(context)!.bottomNavHome),
          BottomNavigationBarItem(icon: const Icon(Icons.confirmation_number_rounded), label: AppLocalizations.of(context)!.bottomNavNumbers),
          BottomNavigationBarItem(icon: const Icon(Icons.history_rounded), label: AppLocalizations.of(context)!.bottomNavHistory),
          BottomNavigationBarItem(icon: const Icon(Icons.settings_rounded), label: AppLocalizations.of(context)!.bottomNavSettings),
        ],
      ),
    );
  }
}
