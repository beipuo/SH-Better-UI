// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/** Fixed version, direction-specific packet bounds and strict UTF-8 before constructing any client state. */
public final class LiveActivityWire {
    public static final int VERSION = 1;
    public static final String CHANNEL_VERSION = Integer.toString(VERSION);
    public static final int MAX_SERVER_BYTES = 32 * 1024;
    public static final int MAX_ACTION_BYTES = 256;

    private static final int SNAPSHOT = 0;
    private static final int CREATE = 1;
    private static final int UPDATE = 2;
    private static final int REMOVE = 3;
    private static final int ACTION_RESULT = 4;

    private LiveActivityWire() {}

    public static byte[] encode(ServerMessage message) {
        return encode(output -> {
            writeSession(output, message.session());
            output.writeLong(message.sequence());
            if (message instanceof Snapshot snapshot) {
                output.writeByte(SNAPSHOT);
                output.writeByte(snapshot.activities().size());
                for (VersionedActivity activity : snapshot.activities()) writeActivity(output, activity);
            } else if (message instanceof Create create) {
                output.writeByte(CREATE);
                writeActivity(output, create.activity());
            } else if (message instanceof Update update) {
                output.writeByte(UPDATE);
                writeActivity(output, update.activity());
            } else if (message instanceof Remove remove) {
                output.writeByte(REMOVE);
                writeId(output, remove.id());
            } else if (message instanceof ActionResult result) {
                output.writeByte(ACTION_RESULT);
                output.writeLong(result.requestId());
                writeId(output, result.activityId());
                writeId(output, result.actionId());
                output.writeByte(result.outcome().ordinal());
            } else {
                throw new IllegalArgumentException("Unrecognized server message");
            }
        });
    }

    public static ServerMessage decodeServer(byte[] bytes) throws IOException {
        return decode(bytes, MAX_SERVER_BYTES, input -> {
            UUID session = readSession(input);
            long sequence = input.readLong();
            return switch (input.readUnsignedByte()) {
                case SNAPSHOT -> {
                    int count = input.readUnsignedByte();
                    if (count > LiveActivityPackets.MAX_ACTIVITIES) throw new IOException("Too many activities");
                    var activities = new ArrayList<VersionedActivity>(count);
                    for (int i = 0; i < count; i++) activities.add(readActivity(input));
                    yield new Snapshot(session, sequence, activities);
                }
                case CREATE -> new Create(session, sequence, readActivity(input));
                case UPDATE -> new Update(session, sequence, readActivity(input));
                case REMOVE -> new Remove(session, sequence, readId(input));
                case ACTION_RESULT -> new ActionResult(session, sequence, input.readLong(), readId(input), readId(input),
                        readEnum(input, Outcome.values()));
                default -> throw new IOException("Unknown server message type");
            };
        });
    }

    public static byte[] encode(Action action) {
        return encode(output -> {
            writeSession(output, action.session());
            output.writeLong(action.requestId());
            writeId(output, action.activityId());
            output.writeLong(action.revision());
            writeId(output, action.actionId());
        });
    }

    public static Action decodeAction(byte[] bytes) throws IOException {
        return decode(bytes, MAX_ACTION_BYTES, input ->
                new Action(readSession(input), input.readLong(), readId(input), input.readLong(), readId(input)));
    }

    private static void writeActivity(DataOutputStream output, VersionedActivity version) throws IOException {
        NetworkLiveActivity activity = version.data();
        output.writeLong(version.revision());
        writeId(output, activity.id());
        writeString(output, activity.title(), NetworkLiveActivity.MAX_TITLE_BYTES);
        writeString(output, activity.detail(), NetworkLiveActivity.MAX_DETAIL_BYTES);
        output.writeByte(activity.status().ordinal());
        output.writeFloat(activity.progress());
        NetworkLiveActivity.ActionOffer action = activity.action();
        output.writeBoolean(action != null);
        if (action != null) {
            writeId(output, action.id());
            writeString(output, action.label(), NetworkLiveActivity.MAX_ACTION_LABEL_BYTES);
        }
    }

    private static VersionedActivity readActivity(DataInputStream input) throws IOException {
        long revision = input.readLong();
        UiId id = readId(input);
        String title = readString(input, NetworkLiveActivity.MAX_TITLE_BYTES);
        String detail = readString(input, NetworkLiveActivity.MAX_DETAIL_BYTES);
        var status = readEnum(input, NetworkLiveActivity.Status.values());
        float progress = input.readFloat();
        NetworkLiveActivity.ActionOffer action = switch (input.readUnsignedByte()) {
            case 0 -> null;
            case 1 -> new NetworkLiveActivity.ActionOffer(readId(input),
                    readString(input, NetworkLiveActivity.MAX_ACTION_LABEL_BYTES));
            default -> throw new IOException("Invalid action offer flag");
        };
        return new VersionedActivity(revision, new NetworkLiveActivity(id, title, detail, status, progress, action));
    }

    private static void writeSession(DataOutputStream output, UUID session) throws IOException {
        output.writeLong(session.getMostSignificantBits());
        output.writeLong(session.getLeastSignificantBits());
    }

    private static UUID readSession(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeId(DataOutputStream output, UiId id) throws IOException {
        writeString(output, id.toString(), NetworkLiveActivity.MAX_ID_BYTES);
    }

    private static UiId readId(DataInputStream input) throws IOException {
        return UiId.parse(readString(input, NetworkLiveActivity.MAX_ID_BYTES));
    }

    private static void writeString(DataOutputStream output, String value, int limit) throws IOException {
        if (value.length() > limit) throw new IllegalArgumentException("Text exceeds packet field limit");
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > limit) throw new IllegalArgumentException("Text exceeds packet field limit");
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input, int limit) throws IOException {
        int length = input.readUnsignedShort();
        if (length > limit) throw new IOException("Text exceeds packet field limit");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream input, E[] values) throws IOException {
        int index = input.readUnsignedByte();
        if (index >= values.length) throw new IOException("Unknown enum value");
        return values[index];
    }

    private static byte[] encode(Encoder writer) {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(VERSION);
            writer.write(output);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    private static <T> T decode(byte[] bytes, int limit, Decoder<T> reader) throws IOException {
        if (bytes.length > limit) throw new IOException("Packet exceeds payload limit");
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readUnsignedByte() != VERSION) throw new IOException("Unsupported live activity version");
            T message = reader.read(input);
            if (input.available() != 0) throw new IOException("Unexpected trailing packet bytes");
            return message;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid live activity payload", invalid);
        }
    }

    private interface Encoder {
        void write(DataOutputStream output) throws IOException;
    }

    private interface Decoder<T> {
        T read(DataInputStream input) throws IOException;
    }
}
