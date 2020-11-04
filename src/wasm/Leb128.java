package wasm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//https://android.googlesource.com/platform/libcore/+/a7752f4d22097346dd7849b92b9f36d0a0a7a8f3/dex/src/main/java/com/android/dex/Leb128.java
public class Leb128 {

        private Leb128() {
        }
        /**
         * Gets the number of bytes in the unsigned LEB128 encoding of the
         * given value.
         *
         * @param value the value in question
         * @return its write size, in bytes
         */
        public static int unsignedLeb128Size(int value) {
            // TODO: This could be much cleverer.
            int remaining = value >> 7;
            int count = 0;
            while (remaining != 0) {
                remaining >>= 7;
                count++;
            }
            return count + 1;
        }
        /**
         * Gets the number of bytes in the signed LEB128 encoding of the
         * given value.
         *
         * @param value the value in question
         * @return its write size, in bytes
         */
        public static int signedLeb128Size(int value) {
            // TODO: This could be much cleverer.
            int remaining = value >> 7;
            int count = 0;
            boolean hasMore = true;
            int end = ((value & Integer.MIN_VALUE) == 0) ? 0 : -1;
            while (hasMore) {
                hasMore = (remaining != end)
                        || ((remaining & 1) != ((value >> 6) & 1));
                value = remaining;
                remaining >>= 7;
                count++;
            }
            return count;
        }
        /**
         * Reads an signed integer from {@code in}.
         */
        public static int readSignedLeb128(ByteArrayInputStream in) {
            int result = 0;
            int cur;
            int count = 0;
            int signBits = -1;
            do {
                cur = in.read();
                result |= (cur & 0x7f) << (count * 7);
                signBits <<= 7;
                count++;
            } while (((cur & 0x80) == 0x80) && count < 5);
            if ((cur & 0x80) == 0x80) {
                throw new Error("invalid LEB128 sequence");
            }
            // Sign extend if appropriate
            if (((signBits >> 1) & result) != 0 ) {
                result |= signBits;
            }
            return result;
        }
        /**
         * Reads an unsigned integer from {@code in}.
         */
        public static int readUnsignedLeb128(ByteArrayInputStream in) {
            int result = 0;
            int cur;
            int count = 0;
            do {
                cur = in.read();
                result |= (cur & 0x7f) << (count * 7);
                count++;
            } while (((cur & 0x80) == 0x80) && count < 5);
            if ((cur & 0x80) == 0x80) {
                throw new Error("invalid LEB128 sequence");
            }
            return result;
        }
        public static byte[] writeUnsignedLeb128(int value) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int remaining = value >>> 7;
            while (remaining != 0) {
                out.write((byte) ((value & 0x7f) | 0x80));
                value = remaining;
                remaining >>>= 7;
            }
            out.write((byte) (value & 0x7f));
            return out.toByteArray();
        }
        public static byte[] writeSignedLeb128(int value) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int remaining = value >> 7;
            boolean hasMore = true;
            int end = ((value & Integer.MIN_VALUE) == 0) ? 0 : -1;
            while (hasMore) {
                hasMore = (remaining != end)
                        || ((remaining & 1) != ((value >> 6) & 1));
                out.write((byte) ((value & 0x7f) | (hasMore ? 0x80 : 0)));
                value = remaining;
                remaining >>= 7;
            }
            return out.toByteArray();
        }
}
