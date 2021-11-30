/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.kylin.dict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.apache.kylin.common.util.Bytes;
import org.apache.kylin.common.util.BytesUtil;
import org.apache.kylin.common.util.ClassUtil;
import org.apache.kylin.common.util.Dictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A dictionary based on Trie data structure that maps enumerations of byte[] to
 * int IDs.
 * <p>
 * With Trie the memory footprint of the mapping is kinda minimized at the cost
 * CPU, if compared to HashMap of ID Arrays. Performance test shows Trie is
 * roughly 10 times slower, so there's a cache layer overlays on top of Trie and
 * gracefully fall back to Trie using a weak reference.
 * <p>
 * The implementation is thread-safe.
 *
 * @author yangli9
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TrieDictionary<T> extends CacheDictionary<T> {
    private static final long serialVersionUID = 1L;

    public static final byte[] MAGIC = new byte[]{0x54, 0x72, 0x69, 0x65, 0x44, 0x69, 0x63, 0x74}; // "TrieDict"
    public static final int MAGIC_SIZE_I = MAGIC.length;

    public static final int BIT_IS_LAST_CHILD = 0x80;
    public static final int BIT_IS_END_OF_VALUE = 0x40;

    private static final Logger logger = LoggerFactory.getLogger(TrieDictionary.class);

    private byte[] trieBytes;

    // non-persistent part
    transient private int headSize;
    @SuppressWarnings("unused")
    transient private int bodyLen;
    transient private int sizeChildOffset;
    transient private int sizeNoValuesBeneath;
    transient private int maxValueLength;

    transient private int nValues;
    transient private int sizeOfId;
    transient private long childOffsetMask;
    transient private int firstByteOffset;


    public TrieDictionary() { // default constructor for Writable interface
    }

    public TrieDictionary(byte[] trieBytes) {
        init(trieBytes);
    }

    private void init(byte[] trieBytes) {
        this.trieBytes = trieBytes;
        if (BytesUtil.compareBytes(MAGIC, 0, trieBytes, 0, MAGIC.length) != 0)
            throw new IllegalArgumentException("Wrong file type (magic does not match)");

        try {
            DataInputStream headIn = new DataInputStream(//
                    new ByteArrayInputStream(trieBytes, MAGIC_SIZE_I, trieBytes.length - MAGIC_SIZE_I));
            this.headSize = headIn.readShort();
            this.bodyLen = headIn.readInt();
            this.sizeChildOffset = headIn.read();
            this.sizeNoValuesBeneath = headIn.read();
            this.baseId = headIn.readShort();
            this.maxValueLength = headIn.readShort();
            if (maxValueLength < 0) {
                throw new IllegalStateException("maxValueLength is negative (" + maxValueLength
                        + "). Dict value is too long, whose length is larger than " + Short.MAX_VALUE);
            }

            String converterName = headIn.readUTF();
            if (converterName.isEmpty() == false)
                setConverterByName(converterName);

            this.nValues = BytesUtil.readUnsigned(trieBytes, headSize + sizeChildOffset, sizeNoValuesBeneath);
            this.sizeOfId = BytesUtil.sizeForValue(baseId + nValues + 1L); // note baseId could raise 1 byte in ID space, +1 to reserve all 0xFF for NULL case
            this.childOffsetMask = ~((long) (BIT_IS_LAST_CHILD | BIT_IS_END_OF_VALUE) << ((sizeChildOffset - 1) * 8));
            this.firstByteOffset = sizeChildOffset + sizeNoValuesBeneath + 1; // the offset from begin of node to its first value byte
            enableCache();
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            else
                throw new RuntimeException(e);
        }
    }

    protected void setConverterByName(String converterName) throws Exception {
        this.bytesConvert = ClassUtil.forName(converterName, BytesConverter.class).getDeclaredConstructor().newInstance();
    }

    @Override
    public int getMinId() {
        return baseId;
    }

    @Override
    public int getMaxId() {
        return baseId + nValues - 1;
    }

    @Override
    public int getSizeOfId() {
        return sizeOfId;
    }

    @Override
    public int getSizeOfValue() {
        return maxValueLength;
    }

    public int getStorageSizeInBytes() {
        return trieBytes.length;
    }

    @Override
    protected int getIdFromValueBytesWithoutCache(byte[] value, int offset, int len, int roundingFlag) {
        int seq = lookupSeqNoFromValue(headSize, value, offset, offset + len, roundingFlag);
        int id = calcIdFromSeqNo(seq);
        if (id < 0)
            logger.debug("Not a valid value: " + bytesConvert.convertFromBytes(value, offset, len));
        return id;
    }

    /**
     * returns a code point from [0, nValues), preserving order of value
     *
     * @param n            -- the offset of current node
     * @param inp          -- input value bytes to lookup
     * @param o            -- offset in the input value bytes matched so far
     * @param inpEnd       -- end of input
     * @param roundingFlag -- =0: return -1 if not found
     *                     -- <0: return closest smaller if not found, return -1
     *                     -- >0: return closest bigger if not found, return nValues
     */
    private int lookupSeqNoFromValue(int n, byte[] inp, int o, int inpEnd, int roundingFlag) {
        if (o == inpEnd) // special 'empty' value
            return checkFlag(headSize, BIT_IS_END_OF_VALUE) ? 0 : roundSeqNo(roundingFlag, -1, -1, 0);

        int seq = 0; // the sequence no under track

        while (true) {
            // match the current node, note [0] of node's value has been matched
            // when this node is selected by its parent
            int p = n + firstByteOffset; // start of node's value
            int end = p + BytesUtil.readUnsigned(trieBytes, p - 1, 1); // end of node's value
            for (p++; p < end && o < inpEnd; p++, o++) { // note matching start from [1]
                if (trieBytes[p] != inp[o]) {
                    int comp = BytesUtil.compareByteUnsigned(trieBytes[p], inp[o]);
                    if (comp < 0) {
                        seq += BytesUtil.readUnsigned(trieBytes, n + sizeChildOffset, sizeNoValuesBeneath);
                    }
                    return roundSeqNo(roundingFlag, seq - 1, -1, seq); // mismatch
                }
            }

            // node completely matched, is input all consumed?
            boolean isEndOfValue = checkFlag(n, BIT_IS_END_OF_VALUE);
            if (o == inpEnd) {
                return p == end && isEndOfValue ? seq : roundSeqNo(roundingFlag, seq - 1, -1, seq); // input all matched
            }
            if (isEndOfValue)
                seq++;

            // find a child to continue
            int c = getChildOffset(n);
            if (c == headSize) // has no children
                return roundSeqNo(roundingFlag, seq - 1, -1, seq); // input only partially matched
            byte inpByte = inp[o];
            int comp;
            while (true) {
                p = c + firstByteOffset;
                comp = BytesUtil.compareByteUnsigned(trieBytes[p], inpByte);
                if (comp == 0) { // continue in the matching child, reset n and loop again
                    n = c;
                    o++;
                    break;
                } else if (comp < 0) { // try next child
                    seq += BytesUtil.readUnsigned(trieBytes, c + sizeChildOffset, sizeNoValuesBeneath);
                    if (checkFlag(c, BIT_IS_LAST_CHILD))
                        return roundSeqNo(roundingFlag, seq - 1, -1, seq); // no child can match the next byte of input
                    c = p + BytesUtil.readUnsigned(trieBytes, p - 1, 1);
                } else { // children are ordered by their first value byte
                    return roundSeqNo(roundingFlag, seq - 1, -1, seq); // no child can match the next byte of input
                }
            }
        }
    }

    private int getChildOffset(int n) {
        long offset = headSize + (BytesUtil.readLong(trieBytes, n, sizeChildOffset) & childOffsetMask);
        assert offset < trieBytes.length;
        return (int) offset;
    }

    private int roundSeqNo(int roundingFlag, int i, int j, int k) {
        if (roundingFlag == 0)
            return j;
        else if (roundingFlag < 0)
            return i;
        else
            return k;
    }


    @Override
    protected byte[] getValueBytesFromIdWithoutCache(int id) {
        byte[] buf = new byte[maxValueLength];
        int len = getValueBytesFromIdImpl(id, buf, 0);

        if (len == buf.length) {
            return buf;
        } else {
            byte[] result = new byte[len];
            System.arraycopy(buf, 0, result, 0, len);
            return result;
        }
    }

    protected int getValueBytesFromIdImpl(int id, byte[] returnValue, int offset) {
        int seq = calcSeqNoFromId(id);
        return lookupValueFromSeqNo(headSize, seq, returnValue, offset);
    }

    /**
     * returns a code point from [0, nValues), preserving order of value, or -1
     * if not found
     *
     * @param n           -- the offset of current node
     * @param seq         -- the code point under track
     * @param returnValue -- where return value is written to
     */
    private int lookupValueFromSeqNo(int n, int seq, byte[] returnValue, int offset) {
        int o = offset;
        while (true) {
            // write current node value
            int p = n + firstByteOffset;
            int len = BytesUtil.readUnsigned(trieBytes, p - 1, 1);
            System.arraycopy(trieBytes, p, returnValue, o, len);
            o += len;

            // if the value is ended
            boolean isEndOfValue = checkFlag(n, BIT_IS_END_OF_VALUE);
            if (isEndOfValue) {
                seq--;
                if (seq < 0)
                    return o - offset;
            }

            // find a child to continue
            int c = getChildOffset(n);
            if (c == headSize) // has no children
                return -1; // no child? corrupted dictionary!
            int nValuesBeneath;
            while (true) {
                nValuesBeneath = BytesUtil.readUnsigned(trieBytes, c + sizeChildOffset, sizeNoValuesBeneath);
                if (seq - nValuesBeneath < 0) { // value is under this child, reset n and loop again
                    n = c;
                    break;
                } else { // go to next child
                    seq -= nValuesBeneath;
                    if (checkFlag(c, BIT_IS_LAST_CHILD))
                        return -1; // no more child? corrupted dictionary!
                    p = c + firstByteOffset;
                    c = p + BytesUtil.readUnsigned(trieBytes, p - 1, 1);
                }
            }
        }
    }

    private boolean checkFlag(int offset, int bit) {
        return (trieBytes[offset] & bit) > 0;
    }

    private int calcIdFromSeqNo(int seq) {
        if (seq < 0 || seq >= nValues)
            return -1;
        else
            return baseId + seq;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.write(trieBytes);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        byte[] headPartial = new byte[MAGIC.length + Short.SIZE + Integer.SIZE];
        in.readFully(headPartial);

        if (BytesUtil.compareBytes(MAGIC, 0, headPartial, 0, MAGIC.length) != 0)
            throw new IllegalArgumentException("Wrong file type (magic does not match)");

        DataInputStream headIn = new DataInputStream(//
                new ByteArrayInputStream(headPartial, MAGIC_SIZE_I, headPartial.length - MAGIC_SIZE_I));
        int headSize = headIn.readShort();
        int bodyLen = headIn.readInt();
        headIn.close();

        byte[] all = new byte[headSize + bodyLen];
        System.arraycopy(headPartial, 0, all, 0, headPartial.length);
        in.readFully(all, headPartial.length, all.length - headPartial.length);

        init(all);
    }

    private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
        stream.writeInt(trieBytes.length);
        stream.write(trieBytes);
    }

    private void readObject(java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
        int length = stream.readInt();
        byte[] trieBytes = new byte[length];
        int currentCount;
        int idx = 0;
        while ((currentCount = stream.read(trieBytes, idx, length - idx)) > 0) {
            idx += currentCount;
        }
        init(trieBytes);
    }

    @Override
    public List<T> enumeratorValues() {
        List<T> result = Lists.newArrayListWithExpectedSize(getSize());
        byte[] buf = new byte[maxValueLength];
        visitNode(headSize, buf, 0, result);
        return result;
    }

    @VisibleForTesting
    List<T> enumeratorValuesByParent() {
        return super.enumeratorValues();
    }

    /**
     * Visit the trie tree by pre-order
     * @param n           -- the offset of current node in trieBytes
     * @param returnValue -- where return value is written to
     */
    private void visitNode(int n, byte[] returnValue, int offset, List<T> result) {
        int o = offset;

        // write current node value
        int p = n + firstByteOffset;
        int len = BytesUtil.readUnsigned(trieBytes, p - 1, 1);
        System.arraycopy(trieBytes, p, returnValue, o, len);
        o += len;

        // if the value is ended
        boolean isEndOfValue = checkFlag(n, BIT_IS_END_OF_VALUE);
        if (isEndOfValue) {
            T curNodeValue = bytesConvert.convertFromBytes(returnValue, 0, o);
            result.add(curNodeValue);
        }

        // find a child to continue
        int c = getChildOffset(n);
        if (c == headSize) // has no children
            return;
        while (true) {
            visitNode(c, returnValue, o, result);
            if (checkFlag(c, BIT_IS_LAST_CHILD))
                return;
            // go to next child
            p = c + firstByteOffset;
            c = p + BytesUtil.readUnsigned(trieBytes, p - 1, 1);
        }
    }

    @Override
    public void dump(PrintStream out) {
        out.println("Total " + nValues + " values");
        for (int i = 0; i < nValues; i++) {
            int id = calcIdFromSeqNo(i);
            T value = getValueFromId(id);
            out.println(id + " (" + Integer.toHexString(id) + "): " + value);
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(trieBytes);
    }

    @Override
    public boolean equals(Object o) {
        if ((o instanceof TrieDictionary) == false) {
            logger.info("Equals return false because it's not TrieDictionary");
            return false;
        }
        TrieDictionary that = (TrieDictionary) o;
        return Arrays.equals(this.trieBytes, that.trieBytes);
    }

    @Override
    public boolean contains(Dictionary other) {
        if (other.getSize() > this.getSize()) {
            return false;
        }

        for (int i = other.getMinId(); i <= other.getMaxId(); ++i) {
            T v = (T) other.getValueFromId(i);
            if (!this.containsValue(v)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        TrieDictionaryBuilder<String> b = new TrieDictionaryBuilder<String>(new StringBytesConverter());
        b.addValue("part");
        b.print();
        b.addValue("part");
        b.print();
        b.addValue("par");
        b.print();
        b.addValue("partition");
        b.print();
        b.addValue("party");
        b.print();
        b.addValue("parties");
        b.print();
        b.addValue("paint");
        b.print();
        TrieDictionary<String> dict = b.build(0);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(dict);

        TrieDictionary<String> dict2 = (TrieDictionary<String>) new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject();
        Preconditions.checkArgument(dict.contains(dict2));
        Preconditions.checkArgument(dict2.contains(dict));
        Preconditions.checkArgument(dict.equals(dict2));

        //dict2.enableIdToValueBytesCache();
        for (int i = 0; i <= dict.getMaxId(); i++) {
            System.out.println(Bytes.toString(dict.getValueBytesFromIdWithoutCache(i)));
        }
    }
}
