// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'purchase.dart';

// **************************************************************************
// TypeAdapterGenerator
// **************************************************************************

class PurchaseAdapter extends TypeAdapter<Purchase> {
  @override
  final int typeId = 1;

  @override
  Purchase read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return Purchase(
      round: fields[0] as int,
      date: fields[1] as DateTime,
      numbers: (fields[2] as List)
          .map((dynamic e) => (e as List).cast<int>())
          .toList(),
      autoCount: fields[3] as int,
      manualCount: fields[4] as int,
      amount: fields[5] as int,
      checked: fields[6] as bool,
      rank: fields[7] as String?,
      prize: fields[8] as int,
      gameRanks: (fields[9] as List?)?.cast<String>(),
      gamePrizes: (fields[10] as List?)?.cast<int>(),
    );
  }

  @override
  void write(BinaryWriter writer, Purchase obj) {
    writer
      ..writeByte(11)
      ..writeByte(0)
      ..write(obj.round)
      ..writeByte(1)
      ..write(obj.date)
      ..writeByte(2)
      ..write(obj.numbers)
      ..writeByte(3)
      ..write(obj.autoCount)
      ..writeByte(4)
      ..write(obj.manualCount)
      ..writeByte(5)
      ..write(obj.amount)
      ..writeByte(6)
      ..write(obj.checked)
      ..writeByte(7)
      ..write(obj.rank)
      ..writeByte(8)
      ..write(obj.prize)
      ..writeByte(9)
      ..write(obj.gameRanks)
      ..writeByte(10)
      ..write(obj.gamePrizes);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is PurchaseAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}
