import 'dart:convert';
import 'dart:developer' as dev;
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../utils/constants.dart';
import '../utils/crypto.dart';

class AuthService {
  late final Dio _dio;
  bool _isLoggedIn = false;
  final Map<String, Map<String, String>> _domainCookies = {};
  final List<String> _debugLog = [];

  bool get isLoggedIn => _isLoggedIn;
  Dio get dio => _dio;
  String get debugInfo => kDebugMode ? _debugLog.join('\n') : '';

  void _log(String msg) {
    if (!kDebugMode) return;
    _debugLog.add('[${DateTime.now().toString().substring(11, 19)}] $msg');
    dev.log(msg, name: 'Auth');
  }

  AuthService() {
    _dio = Dio(BaseOptions(
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      followRedirects: false,
      validateStatus: (status) => status != null && status < 400,
      headers: ApiConstants.defaultHeaders,
    ));

    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        final host = options.uri.host;
        final cookieStr = _buildCookieHeader(host);
        if (cookieStr.isNotEmpty) {
          options.headers['Cookie'] = cookieStr;
        }
        _log('→ ${options.method} ${options.uri}');
        _log('  Cookie[$host]: ${cookieStr.isNotEmpty ? _summarizeCookies(cookieStr) : "(none)"}');
        handler.next(options);
      },
      onResponse: (response, handler) {
        _log('← ${response.statusCode} ${response.requestOptions.uri}');
        
        // 응답 타입/크기 로깅
        final data = response.data;
        if (data is String) {
          _log('  Body: String(${data.length} chars) ${data.substring(0, data.length.clamp(0, 100))}');
        } else if (data is Map) {
          _log('  Body: Map keys=${data.keys.toList()}');
        }
        
        // Set-Cookie 상세 로깅
        final setCookies = response.headers['set-cookie'];
        if (setCookies != null) {
          _log('  Set-Cookie: ${setCookies.length}개');
          for (var i = 0; i < setCookies.length; i++) {
            _log('    [$i] ${setCookies[i].substring(0, setCookies[i].length.clamp(0, 120))}');
          }
        } else {
          _log('  Set-Cookie: 없음');
        }
        
        // Location 헤더 (리다이렉트)
        final location = response.headers['location'];
        if (location != null) {
          _log('  Location: ${location.first}');
        }
        
        _parseCookies(response);
        handler.next(response);
      },
      onError: (error, handler) {
        _log('✖ ${error.response?.statusCode} ${error.requestOptions.uri}');
        _log('  Error: ${error.type} ${error.message}');
        if (error.response != null) {
          final data = error.response!.data;
          if (data is String) {
            _log('  Body: ${data.substring(0, data.length.clamp(0, 200))}');
          }
          // 에러 응답에서도 쿠키 파싱
          _parseCookies(error.response!);
        }
        handler.next(error);
      },
    ));
  }

  String _summarizeCookies(String cookieStr) {
    // "JSESSIONID=abc123; WMONID=xyz" → "JSESSIONID(6), WMONID(3)"
    return cookieStr.split('; ').map((c) {
      final parts = c.split('=');
      final name = parts[0];
      final valLen = parts.length > 1 ? parts.sublist(1).join('=').length : 0;
      return '$name($valLen)';
    }).join(', ');
  }

  String _buildCookieHeader(String host) {
    final merged = <String, String>{};
    _domainCookies.forEach((domain, cookies) {
      if (domain.startsWith('.')) {
        final suffix = domain.substring(1);
        if (host == suffix || host.endsWith('.$suffix')) {
          merged.addAll(cookies);
        }
      }
    });
    final hostCookies = _domainCookies[host];
    if (hostCookies != null) merged.addAll(hostCookies);
    if (merged.isEmpty) return '';
    return merged.entries.map((e) => '${e.key}=${e.value}').join('; ');
  }

  void _parseCookies(Response response) {
    final setCookies = response.headers['set-cookie'];
    if (setCookies == null) return;
    final requestHost = response.requestOptions.uri.host;
    for (final cookie in setCookies) {
      final mainPart = cookie.split(';')[0];
      final eqIdx = mainPart.indexOf('=');
      if (eqIdx < 1) continue;
      final name = mainPart.substring(0, eqIdx).trim();
      final value = mainPart.substring(eqIdx + 1).trim();
      String domain = requestHost;
      final lowerCookie = cookie.toLowerCase();
      final domainIdx = lowerCookie.indexOf('domain=');
      if (domainIdx != -1) {
        final afterDomain = cookie.substring(domainIdx + 7);
        final endIdx = afterDomain.indexOf(';');
        domain = (endIdx != -1 ? afterDomain.substring(0, endIdx) : afterDomain).trim();
      }
      _domainCookies.putIfAbsent(domain, () => {});
      _domainCookies[domain]![name] = value;
      _log('  📌 Stored: $domain → $name(${value.length})');
    }
  }

  Future<Response> _followRedirects(Response response) async {
    var resp = response;
    var count = 0;
    while (count < 10 &&
        (resp.statusCode == 301 || resp.statusCode == 302 || resp.statusCode == 303)) {
      final location = resp.headers['location']?.first;
      if (location == null) break;
      final uri = Uri.parse(location);
      final url = uri.isAbsolute ? location : '${resp.requestOptions.uri.origin}$location';
      _log('↪ REDIRECT #$count → $url');
      resp = await _dio.get(url);
      count++;
    }
    return resp;
  }

  void _logCookieState(String label) {
    _log('=== $label: 쿠키 현황 ===');
    if (_domainCookies.isEmpty) {
      _log('  (비어있음)');
    }
    _domainCookies.forEach((domain, cookies) {
      _log('  $domain: ${cookies.entries.map((e) => '${e.key}(${e.value.length})').join(', ')}');
    });
  }

  Future<bool> login(String userId, String password) async {
    try {
      _domainCookies.clear();
      _debugLog.clear();
      _log('========== 로그인 시작 ==========');

      _log('\n--- Step 1: 메인 페이지 ---');
      var resp = await _dio.get(ApiConstants.baseUrl);
      await _followRedirects(resp);
      _logCookieState('메인 후');

      _log('\n--- Step 2: 로그인 페이지 ---');
      resp = await _dio.get(ApiConstants.loginPageUrl);
      await _followRedirects(resp);

      _log('\n--- Step 3: RSA 키 ---');
      final rsaResp = await _dio.get(
        ApiConstants.rsaModulusUrl,
        options: Options(headers: {
          'Accept': 'application/json',
          'X-Requested-With': 'XMLHttpRequest',
          'Referer': ApiConstants.loginPageUrl,
        }),
      );
      final rsaData = rsaResp.data;
      if (rsaData['data'] == null) throw Exception('RSA 키를 가져올 수 없습니다.');

      final modulus = rsaData['data']['rsaModulus'] as String;
      final exponent = rsaData['data']['publicExponent'] as String;
      _log('RSA modulus: ${modulus.substring(0, 20)}... exp: $exponent');
      
      final encryptedId = RsaCrypto.encrypt(userId, modulus, exponent);
      final encryptedPw = RsaCrypto.encrypt(password, modulus, exponent);
      _log('암호화 완료: id(${encryptedId.length}) pw(${encryptedPw.length})');

      _log('\n--- Step 4: 로그인 POST ---');
      final loginResp = await _dio.post(
        ApiConstants.loginUrl,
        data: 'userId=$encryptedId&userPswdEncn=$encryptedPw&inpUserId=${Uri.encodeComponent(userId)}',
        options: Options(
          contentType: Headers.formUrlEncodedContentType,
          headers: {
            'Origin': ApiConstants.baseUrl,
            'Referer': ApiConstants.loginPageUrl,
          },
        ),
      );
      await _followRedirects(loginResp);
      _logCookieState('로그인 POST 후');

      final hasSession = _domainCookies.values.any((c) => c.containsKey('JSESSIONID'));
      _log('JSESSIONID 존재: $hasSession');
      if (!hasSession) {
        _log('JSESSIONID 없음 — main 방문 시도');
        final mainResp = await _dio.get(ApiConstants.mainUrl);
        await _followRedirects(mainResp);
        _logCookieState('main 방문 후');
      }

      _log('\n--- Step 5: game645 (ol 세션) ---');
      await _dio.get(ApiConstants.mainUrl);
      resp = await _dio.get(ApiConstants.game645Url);
      await _followRedirects(resp);
      _logCookieState('game645 후');

      // ol 도메인에 JSESSIONID 있는지 최종 확인
      final olCookies = _domainCookies['ol.dhlottery.co.kr'];
      final olHasSession = olCookies?.containsKey('JSESSIONID') ?? false;
      _log('ol JSESSIONID 존재: $olHasSession');
      if (olCookies != null) {
        _log('ol 쿠키 키: ${olCookies.keys.toList()}');
      }

      _log('\n--- Step 6: 로그인 검증 ---');
      final verified = await _verifyLogin();
      if (!verified) {
        _isLoggedIn = false;
        _log('========== 로그인 실패 (인증 거부) ==========');
        throw Exception('INVALID_CREDENTIALS');
      }

      _isLoggedIn = true;
      _log('========== 로그인 성공 ==========');
      return true;
    } catch (e, stack) {
      _isLoggedIn = false;
      _log('========== 로그인 실패 ==========');
      _log('Error: $e');
      _log('Stack: ${stack.toString().split('\n').take(5).join('\n')}');
      _logCookieState('실패 시점');
      rethrow;
    }
  }

  /// 실제 로그인 여부 검증 (mypage API 호출)
  Future<bool> _verifyLogin() async {
    try {
      final resp = await _dio.get(
        '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
        options: Options(
          headers: {'X-Requested-With': 'XMLHttpRequest'},
          validateStatus: (status) => status != null,
        ),
      );
      _log('검증 응답: ${resp.statusCode}');
      return resp.statusCode == 200;
    } catch (e) {
      _log('검증 실패: $e');
      return false;
    }
  }

  /// 예치금 잔액 조회
  Future<int> getBalance() async {
    if (!_isLoggedIn) return 0;
    try {
      final resp = await _dio.get(
        '${ApiConstants.baseUrl}/mypage/selectUserMndp.do',
        options: Options(headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Referer': '${ApiConstants.baseUrl}/mypage/home',
        }),
      );
      var data = resp.data;
      if (data is String && data.trimLeft().startsWith('{')) {
        data = jsonDecode(data);
      }
      if (data is Map) {
        return data['data']?['userMndp']?['crntEntrsAmt'] ?? 0;
      }
    } catch (e) {
      _log('잔액 조회 실패: $e');
    }
    return 0;
  }

  void logout() {
    _domainCookies.clear();
    _isLoggedIn = false;
  }
}
