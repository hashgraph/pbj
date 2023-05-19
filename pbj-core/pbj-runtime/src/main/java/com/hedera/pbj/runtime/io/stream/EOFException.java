package com.hedera.pbj.runtime.io.stream;

import java.nio.BufferUnderflowException;

/**  This class is used as an exception to signal that the end of stream is reached when reading.  */
public class EOFException  extends BufferUnderflowException {
    private static final long serialVersionUID = 1799983599892333203L;
}
