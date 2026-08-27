import CryptoKit
import Foundation

public enum PairingSecurity {
    public static func response(code: String, challenge: Data) -> Data {
        let key = SymmetricKey(data: Data(code.utf8))
        return Data(HMAC<SHA256>.authenticationCode(for: challenge, using: key))
    }

    public static func verify(code: String, challenge: Data, response: Data) -> Bool {
        let key = SymmetricKey(data: Data(code.utf8))
        return HMAC<SHA256>.isValidAuthenticationCode(response, authenticating: challenge, using: key)
    }
}
