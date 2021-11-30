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

package org.apache.kylin.engine.mr;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Reader;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.kylin.common.util.Bytes;
import org.apache.kylin.common.util.HadoopUtil;
import org.apache.kylin.common.util.StringSplitter;
import org.apache.kylin.source.IReadableTable.TableReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tables are typically CSV or SEQ file.
 * 
 * @author yangli9
 */
public class DFSFileTableReader implements TableReader {

    private static final Logger logger = LoggerFactory.getLogger(DFSFileTableReader.class);
    private static final char CSV_QUOTE = '"';
    private static final String[] DETECT_DELIMS = new String[] { "\177", "|", "\t", "," };

    private String filePath;
    private String delim;
    private List<RowReader> readerList;

    private String curLine;
    private String[] curColumns;
    private int expectedColumnNumber = -1; // helps delimiter detection

    public DFSFileTableReader(String filePath, int expectedColumnNumber) throws IOException {
        this(filePath, DFSFileTable.DELIM_AUTO, expectedColumnNumber);
    }

    public DFSFileTableReader(String filePath, String delim, int expectedColumnNumber) throws IOException {
        filePath = HadoopUtil.fixWindowsPath(filePath);
        this.filePath = filePath;
        this.delim = delim;
        this.expectedColumnNumber = expectedColumnNumber;
        this.readerList = new ArrayList<RowReader>();

        FileSystem fs = HadoopUtil.getFileSystem(filePath);

        ArrayList<FileStatus> allFiles = new ArrayList<>();
        FileStatus status = fs.getFileStatus(new Path(filePath));
        if (status.isFile()) {
            allFiles.add(status);
        } else {
            FileStatus[] listStatus = fs.listStatus(new Path(filePath));
            allFiles.addAll(Arrays.asList(listStatus));
        }

        try {
            for (FileStatus f : allFiles) {
                RowReader rowReader = new SeqRowReader(HadoopUtil.getCurrentConfiguration(), f.getPath().toString());
                this.readerList.add(rowReader);
            }
        } catch (IOException e) {
            if (isExceptionSayingNotSeqFile(e) == false)
                throw e;

            this.readerList = new ArrayList<RowReader>();
            for (FileStatus f : allFiles) {
                RowReader rowReader = new CsvRowReader(fs, f.getPath().toString());
                this.readerList.add(rowReader);
            }
        }
    }

    private boolean isExceptionSayingNotSeqFile(IOException e) {
        if (e.getMessage() != null && e.getMessage().contains("not a SequenceFile"))
            return true;

        if (e instanceof EOFException) // in case the file is very very small
            return true;

        return false;
    }

    @Override
    public boolean next() throws IOException {
        int curReaderIndex = -1;
        RowReader curReader;

        while (++curReaderIndex < readerList.size()) {
            curReader = readerList.get(curReaderIndex);
            curLine = curReader.nextLine();
            curColumns = null;

            if (curLine != null) {
                return true;
            }
        }

        return false;
    }

    public String getLine() {
        return curLine;
    }

    @Override
    public String[] getRow() {
        if (curColumns == null) {
            if (DFSFileTable.DELIM_AUTO.equals(delim))
                delim = autoDetectDelim(curLine);

            if (delim == null)
                curColumns = new String[] { curLine };
            else
                curColumns = split(curLine, delim);
        }
        return curColumns;
    }

    private String[] split(String line, String delim) {
        // FIXME CVS line should be parsed considering escapes
        String[] str = StringSplitter.split(line, delim);

        // un-escape CSV
        if (DFSFileTable.DELIM_COMMA.equals(delim)) {
            for (int i = 0; i < str.length; i++) {
                str[i] = unescapeCsv(str[i]);
            }
        }

        return str;
    }

    private String unescapeCsv(String str) {
        if (str == null || str.length() < 2)
            return str;

        str = StringEscapeUtils.unescapeCsv(str);

        // unescapeCsv may not remove the outer most quotes
        if (str.charAt(0) == CSV_QUOTE && str.charAt(str.length() - 1) == CSV_QUOTE)
            str = str.substring(1, str.length() - 1);

        return str;
    }

    @Override
    public void close() {
        for (RowReader reader : readerList) {
            IOUtils.closeQuietly(reader);
        }
    }

    private String autoDetectDelim(String line) {
        if (expectedColumnNumber > 0) {
            for (String delim : DETECT_DELIMS) {
                if (StringSplitter.split(line, delim).length == expectedColumnNumber) {
                    logger.info("Auto detect delim to be '" + delim + "', split line to " + expectedColumnNumber + " columns -- " + line);
                    return delim;
                }
            }
        }

        logger.info("Auto detect delim to be null, will take THE-WHOLE-LINE as a single value, for " + filePath);
        return null;
    }

    // ============================================================================

    private interface RowReader extends Closeable {
        String nextLine() throws IOException; // return null on EOF
    }

    private class SeqRowReader implements RowReader {
        Reader reader;
        Writable key;
        Text value;

        SeqRowReader(Configuration hconf, String path) throws IOException {
            reader = new Reader(hconf, SequenceFile.Reader.file(new Path(path)));
            key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), hconf);
            value = new Text();
        }

        @Override
        public String nextLine() throws IOException {
            boolean hasNext = reader.next(key, value);
            if (hasNext)
                return Bytes.toString(value.getBytes(), 0, value.getLength());
            else
                return null;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    private class CsvRowReader implements RowReader {
        BufferedReader reader;

        CsvRowReader(FileSystem fs, String path) throws IOException {
            FSDataInputStream in = fs.open(new Path(path));
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        }

        @Override
        public String nextLine() throws IOException {
            return reader.readLine();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

    }

}
