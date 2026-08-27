using System.Security.Cryptography;
using System.Text;

namespace UniversalMobileDesktop.Protocol;

public static class PairingSecurity
{
    public static byte[] CreateResponse(string sixDigitCode, byte[] challenge)
    {
        if (sixDigitCode.Length != 6 || !sixDigitCode.All(char.IsDigit))
            throw new ArgumentException("The pairing code must contain six digits.", nameof(sixDigitCode));
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(sixDigitCode));
        return hmac.ComputeHash(challenge);
    }
}
