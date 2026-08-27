using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.Text;
using UniversalMobileDesktop.Protocol;

namespace UniversalMobileDesktop.FileTransfer;

public sealed class FileTransferManager : IDisposable
{
    private readonly string receiveDirectory;
    private readonly ConcurrentDictionary<Guid, FileStream> incoming = new();
    public event Action<string>? FileReceived;

    public FileTransferManager(string? receiveDirectory = null)
    {
        this.receiveDirectory = receiveDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "Desktop Mod Received");
        Directory.CreateDirectory(this.receiveDirectory);
    }

    public IEnumerable<Packet> CreatePackets(string path, int chunkSize = 64 * 1024)
    {
        var info = new FileInfo(path);
        if (!info.Exists) throw new FileNotFoundException("The dropped file no longer exists.", path);
        var id = Guid.NewGuid();
        var name = Encoding.UTF8.GetBytes(info.Name);
        if (name.Length > 4096) throw new InvalidDataException("File name is too long.");
        using var metadata = new MemoryStream();
        using (var writer = new BinaryWriter(metadata, Encoding.UTF8, true))
        {
            writer.Write(id.ToByteArray());
            Span<byte> nameLength = stackalloc byte[2]; BinaryPrimitives.WriteUInt16BigEndian(nameLength, checked((ushort)name.Length));
            writer.Write(nameLength); writer.Write(name);
            Span<byte> length = stackalloc byte[8]; BinaryPrimitives.WriteInt64BigEndian(length, info.Length); writer.Write(length);
        }
        yield return new Packet(PacketType.FileMetadata, metadata.ToArray());
        using var input = info.OpenRead();
        var buffer = new byte[chunkSize];
        int count;
        while ((count = input.Read(buffer, 0, buffer.Length)) > 0)
        {
            using var chunk = new MemoryStream(16 + count);
            using var writer = new BinaryWriter(chunk, Encoding.UTF8, true);
            writer.Write(id.ToByteArray()); writer.Write(buffer, 0, count);
            yield return new Packet(PacketType.FileChunk, chunk.ToArray());
        }
        yield return new Packet(PacketType.FileComplete, id.ToByteArray());
    }

    public void Receive(Packet packet)
    {
        using var reader = new BinaryReader(new MemoryStream(packet.Payload, false), Encoding.UTF8);
        var idBytes = reader.ReadBytes(16);
        if (idBytes.Length != 16) throw new InvalidDataException("Missing transfer identifier.");
        var id = new Guid(idBytes);
        switch (packet.Type)
        {
            case PacketType.FileMetadata:
                var nameBytes = reader.ReadBytes(2);
                if (nameBytes.Length != 2) throw new InvalidDataException("Invalid file metadata.");
                var nameLength = BinaryPrimitives.ReadUInt16BigEndian(nameBytes);
                var name = Path.GetFileName(Encoding.UTF8.GetString(reader.ReadBytes(nameLength)));
                _ = BinaryPrimitives.ReadInt64BigEndian(reader.ReadBytes(8));
                if (incoming.TryRemove(id, out var old)) old.Dispose();
                incoming[id] = new FileStream(UniquePath(string.IsNullOrWhiteSpace(name) ? "received-file" : name), FileMode.CreateNew);
                break;
            case PacketType.FileChunk:
                if (incoming.TryGetValue(id, out var destination)) reader.BaseStream.CopyTo(destination);
                break;
            case PacketType.FileComplete:
                if (incoming.TryRemove(id, out var completed)) { var path = completed.Name; completed.Dispose(); FileReceived?.Invoke(path); }
                break;
        }
    }

    private string UniquePath(string name)
    {
        var candidate = Path.Combine(receiveDirectory, name);
        for (var suffix = 1; File.Exists(candidate); suffix++) candidate = Path.Combine(receiveDirectory, $"{Path.GetFileNameWithoutExtension(name)} ({suffix}){Path.GetExtension(name)}");
        return candidate;
    }

    public void Dispose() { foreach (var stream in incoming.Values) stream.Dispose(); incoming.Clear(); }
}
