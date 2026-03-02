import 'package:hive_flutter/hive_flutter.dart';
import '../models/game.dart';
import '../models/purchase.dart';
import '../models/result.dart';

class AppDatabase {
  static const _purchaseBox = 'purchases';
  static const _resultBox = 'results';

  static Future<void> initialize() async {
    await Hive.initFlutter();

    // 어댑터 등록
    Hive.registerAdapter(GameAdapter());
    Hive.registerAdapter(PurchaseAdapter());
    Hive.registerAdapter(WinningNumbersAdapter());

    // 박스 열기
    await Hive.openBox<Purchase>(_purchaseBox);
    await Hive.openBox<WinningNumbers>(_resultBox);
  }

  static Box<Purchase> get purchaseBox => Hive.box<Purchase>(_purchaseBox);
  static Box<WinningNumbers> get resultBox => Hive.box<WinningNumbers>(_resultBox);
}
