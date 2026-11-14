import UIKit
import SwiftUI
import CryptoKit
import CommonCrypto
import Security
import Shared

/// Tela principal: o app inteiro é desenhado pelo código compartilhado (Compose Multiplatform).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(crypto: AppleCrypto())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

/// Criptografia do arquivo .sono (AES-256-GCM + PBKDF2-SHA256), compatível com a versão Android.
/// O texto cifrado é seguido da tag de 16 bytes, mesmo layout do Java.
final class AppleCrypto: SwiftCrypto {
    func randomBytes(count: Int32) -> Data {
        var data = Data(count: Int(count))
        let status = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, Int(count), $0.baseAddress!) }
        precondition(status == errSecSuccess, "Falha ao gerar bytes aleatórios")
        return data
    }

    func pbkdf2Sha256(password: String, salt: Data, iterations: Int32, keyBytes: Int32) -> Data {
        var key = Data(count: Int(keyBytes))
        let pw = Array(password.utf8)
        let status = key.withUnsafeMutableBytes { keyPtr in
            salt.withUnsafeBytes { saltPtr in
                pw.withUnsafeBufferPointer { pwPtr in
                    pwPtr.baseAddress!.withMemoryRebound(to: CChar.self, capacity: pw.count) { pwChars in
                        CCKeyDerivationPBKDF(
                            CCPBKDFAlgorithm(kCCPBKDF2),
                            pwChars, pw.count,
                            saltPtr.bindMemory(to: UInt8.self).baseAddress, salt.count,
                            CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                            UInt32(iterations),
                            keyPtr.bindMemory(to: UInt8.self).baseAddress, Int(keyBytes)
                        )
                    }
                }
            }
        }
        precondition(status == kCCSuccess, "Falha no PBKDF2")
        return key
    }

    func aesGcmSeal(key: Data, iv: Data, aad: Data, plain: Data) -> Data {
        let box = try! AES.GCM.seal(plain, using: SymmetricKey(data: key), nonce: try! AES.GCM.Nonce(data: iv), authenticating: aad)
        return box.ciphertext + box.tag
    }

    func aesGcmOpen(key: Data, iv: Data, aad: Data, cipherAndTag: Data) -> Data? {
        guard cipherAndTag.count >= 16, let nonce = try? AES.GCM.Nonce(data: iv) else { return nil }
        let ciphertext = cipherAndTag.prefix(cipherAndTag.count - 16)
        let tag = cipherAndTag.suffix(16)
        guard let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag) else { return nil }
        return try? AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: aad)
    }
}
