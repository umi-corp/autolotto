import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../l10n/app_localizations.dart';
import '../providers/providers.dart';
import '../utils/ui_helpers.dart';

/// 자동 구매 번호 설정 화면
class NumberScreen extends ConsumerStatefulWidget {
  const NumberScreen({super.key});

  @override
  ConsumerState<NumberScreen> createState() => _NumberScreenState();
}

class _NumberScreenState extends ConsumerState<NumberScreen> {
  static const _primary = Color(0xFF2D5BFF);

  // 5게임 슬롯: null=미설정, []=자동, [nums]=수동
  final List<List<int>?> _games = List.generate(5, (_) => null);
  int _currentSlot = 0;
  bool _isAuto = false;
  final Set<int> _selected = {};
  bool _saved = true;

  @override
  void initState() {
    super.initState();
    _loadSavedGames();
  }

  Future<void> _loadSavedGames() async {
    try {
      final json = await ref.read(secureStorageProvider).getManualNumbers();
      final parsed = jsonDecode(json) as List;
      if (mounted) {
        setState(() {
          for (var i = 0; i < parsed.length && i < 5; i++) {
            final g = parsed[i];
            if (g == null) {
              _games[i] = null; // 미설정
            } else if (g is List && g.isEmpty) {
              _games[i] = []; // 자동
            } else if (g is List) {
              _games[i] = g.map<int>((e) => e is int ? e : int.parse(e.toString())).toList(); // 수동
            }
          }
          // 첫 미설정 슬롯으로 포커스
          for (var i = 0; i < 5; i++) {
            if (_games[i] == null) {
              _currentSlot = i;
              break;
            }
          }
        });
      }
    } catch (e) {
      debugPrint('저장된 번호 로드 실패: $e');
    }
  }

  void _toggleNumber(int n) {
    setState(() {
      if (_selected.contains(n)) {
        _selected.remove(n);
      } else if (_selected.length < 6) {
        _selected.add(n);
      }
      _saved = false;
    });
  }

  void _confirmSlot() {
    if (!_isAuto && _selected.length != 6) return;
    setState(() {
      _games[_currentSlot] = _isAuto ? [] : (_selected.toList()..sort());
      _selected.clear();
      _saved = false;
      if (_currentSlot < 4) {
        _currentSlot++;
        _isAuto = false;
      }
    });
  }

  void _resetSlot() {
    setState(() {
      _selected.clear();
      _games[_currentSlot] = null;
      _isAuto = false;
      _saved = false;
    });
  }

  int get _configuredCount => _games.where((g) => g != null).length;

  Future<void> _saveConfig() async {
    // TODO: SecureStorage에 번호 설정 저장
    // 게임 수도 설정된 슬롯 수로 자동 반영
    ref.read(autoGamesProvider.notifier).state = _configuredCount;
    await ref.read(secureStorageProvider).setAutoGames(_configuredCount);

    // 수동 번호 JSON 저장
    final manualJson = jsonEncode(_games.toList());
    await ref.read(secureStorageProvider).setManualNumbers(manualJson);

    setState(() => _saved = true);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.snackbarSaveSuccess(
            _configuredCount,
            formatPurchaseScheduleL10n(AppLocalizations.of(context)!, ref.read(autoPurchaseDayProvider), ref.read(autoPurchaseHourProvider), ref.read(autoPurchaseMinuteProvider)))),
          backgroundColor: Colors.green,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final autoEnabled = ref.watch(autoEnabledProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F6FA),
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.numberSetupTitle, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: _primary, elevation: 0, centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 상태 배너
            if (!autoEnabled)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: Colors.orange[50],
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.orange[200]!),
                ),
                child: Row(children: [
                  Icon(Icons.warning_amber_rounded, color: Colors.orange[700], size: 20),
                  const SizedBox(width: 8),
                  Expanded(child: Text(AppLocalizations.of(context)!.bannerEnableAutoPurchase,
                    style: TextStyle(color: Colors.orange[700], fontSize: 13))),
                ]),
              ),

            // 안내
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              margin: const EdgeInsets.only(bottom: 16),
              decoration: BoxDecoration(
                color: _primary.withValues(alpha: 0.05),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                AppLocalizations.of(context)!.numberSetupInstruction,
                style: TextStyle(color: Color(0xFF2D5BFF), fontSize: 13, height: 1.5),
              ),
            ),

            _buildSlotTabs(),
            const SizedBox(height: 8),
            _buildAllAutoButton(),
            const SizedBox(height: 16),
            _buildModeToggle(),
            const SizedBox(height: 16),

            if (!_isAuto) ...[
              _buildNumberGrid(),
              const SizedBox(height: 12),
              _buildSelectedNumbers(),
            ],

            if (_isAuto)
              Container(
                width: double.infinity, padding: const EdgeInsets.all(32),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16),
                  boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)]),
                child: Column(children: [
                  Icon(Icons.casino_rounded, size: 48, color: _primary.withValues(alpha: 0.5)),
                  const SizedBox(height: 12),
                  Text(AppLocalizations.of(context)!.autoNumberTitle, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  Text(AppLocalizations.of(context)!.autoNumberSubtitle, style: TextStyle(color: Colors.grey[500], fontSize: 14)),
                ]),
              ),

            const SizedBox(height: 24),

            // 확정 / 초기화 버튼
            Row(children: [
              Expanded(child: OutlinedButton(
                onPressed: _resetSlot,
                style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  side: BorderSide(color: Colors.grey[300]!)),
                child: Text(AppLocalizations.of(context)!.buttonReset, style: const TextStyle(fontSize: 15)),
              )),
              const SizedBox(width: 12),
              Expanded(flex: 2, child: ElevatedButton(
                onPressed: (_isAuto || _selected.length == 6) ? _confirmSlot : null,
                style: ElevatedButton.styleFrom(backgroundColor: _primary, disabledBackgroundColor: Colors.grey[300],
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)), elevation: 2),
                child: Text(AppLocalizations.of(context)!.buttonConfirmGame(String.fromCharCode(65 + _currentSlot)),
                  style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.bold)),
              )),
            ]),

            const SizedBox(height: 24),
            _buildGameSummary(),
            const SizedBox(height: 16),

            // 설정 저장 버튼
            SizedBox(
              width: double.infinity, height: 52,
              child: ElevatedButton(
                onPressed: _configuredCount == 0 ? null : _saveConfig,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _saved ? Colors.green : const Color(0xFFFF6B35),
                  disabledBackgroundColor: Colors.grey[300],
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)), elevation: 3),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(_saved ? Icons.check_circle : Icons.save_rounded, color: Colors.white, size: 20),
                    const SizedBox(width: 8),
                    Text(
                      _saved ? AppLocalizations.of(context)!.buttonSaveDone : AppLocalizations.of(context)!.buttonSaveGames(_configuredCount),
                      style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  void _setAllAuto() {
    setState(() {
      for (var i = 0; i < 5; i++) {
        _games[i] = []; // 자동
      }
      _selected.clear();
      _isAuto = true;
      _saved = false;
    });
  }

  Widget _buildAllAutoButton() {
    final allAuto = _games.every((g) => g != null && g.isEmpty);
    return Align(
      alignment: Alignment.centerRight,
      child: TextButton.icon(
        onPressed: allAuto ? null : _setAllAuto,
        icon: Icon(Icons.auto_awesome_rounded, size: 16,
          color: allAuto ? Colors.grey[400] : _primary),
        label: Text(AppLocalizations.of(context)!.buttonAllAuto,
          style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600,
            color: allAuto ? Colors.grey[400] : _primary)),
        style: TextButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          minimumSize: Size.zero,
          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        ),
      ),
    );
  }

  Widget _buildSlotTabs() {
    return Row(children: List.generate(5, (i) {
      final isActive = i == _currentSlot;
      final isConfirmed = _games[i] != null;
      return Expanded(child: GestureDetector(
        onTap: () => setState(() { _currentSlot = i; _selected.clear(); _isAuto = false; }),
        child: Container(
          margin: EdgeInsets.only(right: i < 4 ? 6 : 0),
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: isActive ? _primary : (isConfirmed ? _primary.withValues(alpha: 0.1) : Colors.white),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: isActive ? _primary : Colors.grey[300]!)),
          child: Center(child: Text(String.fromCharCode(65 + i),
            style: TextStyle(color: isActive ? Colors.white : (isConfirmed ? _primary : Colors.grey[500]), fontWeight: FontWeight.bold))),
        ),
      ));
    }));
  }

  Widget _buildModeToggle() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(color: Colors.grey[200], borderRadius: BorderRadius.circular(12)),
      child: Row(children: [
        _modeButton(AppLocalizations.of(context)!.modeManual, false),
        _modeButton(AppLocalizations.of(context)!.modeAuto, true),
      ]),
    );
  }

  Widget _modeButton(String label, bool auto) {
    final active = _isAuto == auto;
    return Expanded(child: GestureDetector(
      onTap: () => setState(() { _isAuto = auto; if (auto) _selected.clear(); }),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(color: active ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
          boxShadow: active ? [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4)] : null),
        child: Center(child: Text(label, style: TextStyle(fontWeight: FontWeight.w600, color: active ? _primary : Colors.grey[600]))),
      ),
    ));
  }

  Widget _buildNumberGrid() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)]),
      child: LayoutBuilder(builder: (context, constraints) {
        const cols = 7;
        final spacing = 6.0;
        final ballSize = (constraints.maxWidth - spacing * (cols - 1)) / cols;
        return Wrap(spacing: spacing, runSpacing: spacing, children: List.generate(45, (i) {
          final n = i + 1;
          final isSelected = _selected.contains(n);
          final color = ballColor(n);
          return GestureDetector(
            onTap: () => _toggleNumber(n),
            child: Container(width: ballSize, height: ballSize,
              decoration: BoxDecoration(shape: BoxShape.circle,
                color: isSelected ? color : Colors.grey[100],
                border: isSelected ? null : Border.all(color: Colors.grey[300]!),
                boxShadow: isSelected ? [BoxShadow(color: color.withValues(alpha: 0.4), blurRadius: 6)] : null),
              child: Center(child: Text('$n', style: TextStyle(
                color: isSelected ? Colors.white : Colors.grey[700],
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal, fontSize: 14)))),
          );
        }));
      }),
    );
  }

  Widget _buildSelectedNumbers() {
    return Container(
      width: double.infinity, padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
      child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
        Text(AppLocalizations.of(context)!.selectionCount(_selected.length), style: TextStyle(color: Colors.grey[600], fontSize: 14)),
        ...(_selected.toList()..sort()).map((n) => Padding(
          padding: const EdgeInsets.only(right: 6),
          child: Container(width: 36, height: 36,
            decoration: BoxDecoration(shape: BoxShape.circle, color: ballColor(n)),
            child: Center(child: Text('$n', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)))),
        )),
      ]),
    );
  }

  Widget _buildGameSummary() {
    return Container(
      width: double.infinity, padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10)]),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(AppLocalizations.of(context)!.gameSummaryTitle, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
        const SizedBox(height: 12),
        ...List.generate(5, (i) {
          final g = _games[i];
          final isConfirmed = g != null;
          final l10n = AppLocalizations.of(context)!;
          final label = g == null
              ? (i == _currentSlot ? l10n.gameSummarySelecting : l10n.gameSummaryNotSet)
              : (g.isEmpty ? l10n.gameSummaryAuto : g.join(', '));
          return Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(children: [
              Container(width: 28, height: 28,
                decoration: BoxDecoration(
                  color: isConfirmed ? _primary.withValues(alpha: 0.1) : Colors.grey[100],
                  borderRadius: BorderRadius.circular(8)),
                child: Center(child: Text(String.fromCharCode(65 + i),
                  style: TextStyle(color: isConfirmed ? _primary : Colors.grey[400], fontWeight: FontWeight.bold, fontSize: 13)))),
              const SizedBox(width: 12),
              Expanded(child: Text(label, style: TextStyle(color: isConfirmed ? Colors.black87 : Colors.grey[400], fontSize: 14))),
              if (isConfirmed)
                GestureDetector(
                  onTap: () => setState(() { _games[i] = null; _saved = false; }),
                  child: Icon(Icons.close_rounded, size: 18, color: Colors.grey[400]),
                ),
            ]),
          );
        }),
      ]),
    );
  }
}
