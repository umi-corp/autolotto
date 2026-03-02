import 'package:hive/hive.dart';

part 'purchase.g.dart';

@HiveType(typeId: 1)
class Purchase extends HiveObject {
  @HiveField(0)
  final int round;

  @HiveField(1)
  final DateTime date;

  @HiveField(2)
  final List<List<int>> numbers;

  @HiveField(3)
  final int autoCount;

  @HiveField(4)
  final int manualCount;

  @HiveField(5)
  final int amount;

  @HiveField(6)
  bool checked;

  @HiveField(7)
  String? rank; // 당첨 등수

  @HiveField(8)
  int prize; // 당첨금

  @HiveField(9)
  List<String>? gameRanks; // 게임별 등수 ["5등", "낙첨", ...]

  @HiveField(10)
  List<int>? gamePrizes; // 게임별 당첨금

  Purchase({
    required this.round,
    required this.date,
    required this.numbers,
    required this.autoCount,
    required this.manualCount,
    required this.amount,
    this.checked = false,
    this.rank,
    this.prize = 0,
    this.gameRanks,
    this.gamePrizes,
  });

  int get totalGames => autoCount + manualCount;
}
