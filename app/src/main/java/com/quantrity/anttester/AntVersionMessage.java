package com.quantrity.anttester;

/** Encodes and reads the ANT Version message described in sections 7.1 and 9.5.7.3. */
final class AntVersionMessage {
    private static final int SYNC = 0xa4;
    private static final int ALTERNATE_SYNC = 0xa5;
    private static final int REQUEST_MESSAGE = 0x4d;
    private static final int ANT_VERSION = 0x3e;

    private AntVersionMessage() { }

    static byte[] request() {
        byte[] frame = {(byte) SYNC, 2, REQUEST_MESSAGE, 0, ANT_VERSION, 0};
        frame[frame.length - 1] = checksum(frame, 0, frame.length - 1);
        return frame;
    }

    static byte[] halRequest() {
        // The native HAL forwards a single size/ID/payload packet, as used by ant_app's version test.
        return new byte[] {2, REQUEST_MESSAGE, 0, ANT_VERSION};
    }

    static String readHalVersion(byte[] packet) {
        // The HAL callback omits the sync and checksum bytes.
        if (packet == null || packet.length < 2 || packet[1] != ANT_VERSION
                || (packet[0] & 0xff) != packet.length - 2) {
            return null;
        }
        return readVersion(packet, 2, packet.length);
    }

    static String findVersion(byte[] data, int length) {
        for (int start = 0; start + 4 <= length; start++) {
            int sync = data[start] & 0xff;
            if (sync != SYNC && sync != ALTERNATE_SYNC) continue;

            int end = start + 4 + (data[start + 1] & 0xff);
            if (end > length || checksum(data, start, end) != 0) continue;

            if (data[start + 2] == ANT_VERSION) {
                String version = readVersion(data, start + 3, end - 1);
                if (version != null) return version;
            }
            start = end - 1;
        }
        return null;
    }

    private static String readVersion(byte[] data, int start, int end) {
        StringBuilder version = new StringBuilder();
        for (int i = start; i < end && data[i] != 0; i++) {
            int character = data[i] & 0xff;
            if (character < 0x20 || character > 0x7e) return null;
            version.append((char) character);
        }
        String value = version.toString().trim();
        return value.length() == 0 ? null : value;
    }

    private static byte checksum(byte[] data, int start, int end) {
        byte check = 0;
        for (int i = start; i < end; i++) {
            check ^= data[i];
        }
        return check;
    }
}
