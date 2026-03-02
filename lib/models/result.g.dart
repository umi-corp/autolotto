// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'result.dart';

// **************************************************************************
// TypeAdapterGenerator
// **************************************************************************

class WinningNumbersAdapter extends TypeAdapter<WinningNumbers> {
  @override
  final int typeId = 2;

  @override
  WinningNumbers read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return WinningNumbers(
      round: fields[0] as int,
      numbers: (fields[1] as List).cast<int>(),
      bonus: fields[2] as int,
      date: fields[3] as String,
      prize1st: fields[4] as int,
      prize2nd: fields[5] as int,
      prize3rd: fields[6] as int,
    );
  }

  @override
  void write(BinaryWriter writer, WinningNumbers obj) {
    writer
      ..writeByte(7)
      ..writeByte(0)
      ..write(obj.round)
      ..writeByte(1)
      ..write(obj.numbers)
      ..writeByte(2)
      ..write(obj.bonus)
      ..writeByte(3)
      ..write(obj.date)
      ..writeByte(4)
      ..write(obj.prize1st)
      ..writeByte(5)
      ..write(obj.prize2nd)
      ..writeByte(6)
      ..write(obj.prize3rd);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WinningNumbersAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}
