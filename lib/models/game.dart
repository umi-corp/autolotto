import 'package:hive/hive.dart';

part 'game.g.dart';

@HiveType(typeId: 0)
class Game extends HiveObject {
  @HiveField(0)
  final List<int> numbers;

  @HiveField(1)
  final bool isAuto;

  @HiveField(2)
  final String slot; // A~E

  Game({required this.numbers, required this.isAuto, required this.slot});
}
