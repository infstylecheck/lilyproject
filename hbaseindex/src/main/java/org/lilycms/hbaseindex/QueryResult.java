package org.lilycms.hbaseindex;

import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;

import java.io.IOException;

public class QueryResult {
    private ResultScanner scanner;
    private int indexKeyLength;

    public QueryResult(ResultScanner scanner, int indexKeyLength) {
        this.scanner = scanner;
        this.indexKeyLength = indexKeyLength;
    }

    public byte[] next() throws IOException {
        Result result = scanner.next();
        if (result == null)
            return null;

        byte[] rowKey = result.getRow();
        byte[] targetKey = new byte[rowKey.length - indexKeyLength];
        System.arraycopy(rowKey, indexKeyLength, targetKey, 0, targetKey.length);
        return targetKey;
    }
}
