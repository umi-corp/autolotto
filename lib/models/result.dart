import 'package:hive/hive.dart';

part 'result.g.dart';

@HiveType(typeId: 2)
class WinningNumbers extends HiveObject {
  @HiveField(0)
  final int round;

  @HiveField(1)
  final List<int> numbers;

  @HiveField(2)
  final int bonus;

  @HiveField(3)
  final String date;

  @HiveField(4)
  final int prize1st;

  @HiveField(5)
  final int prize2nd;

  @HiveField(6)
  final int prize3rd;

  WinningNumbers({
    required this.round,
    required this.numbers,
    required this.bonus,
    required this.date,
    this.prize1st = 0,
    this.prize2nd = 0,
    this.prize3rd = 0,
  });
}
