package com.box.boxjavalibv2.httpentities;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;

import com.box.boxjavalibv2.interfaces.IFileTransferListener;

/**
 * This is a class wrappying MultipartEntity with a IFIleTransferListener so the writing progress of the entity can be monitored.
 */
public class MultipartEntityWithProgressListener extends MultipartEntity {

    /** Default minimum time between two progress messages being sent out. */
    public static final int ON_PROGRESS_UPDATE_THRESHOLD = 300;
    /**
     * The minimum time in milliseconds that must pass between each firing of progress listener. This is to avoid excessive calls which may lock up the device.
     */
    private static int onProgressUpdateThreshold = ON_PROGRESS_UPDATE_THRESHOLD;
    /**
     * progress listener.
     */
    private IFileTransferListener mListener;

    /**
     * Instance of CountingOutputStream.
     */
    private CountingOutputStream mCountingOutputStream;

    private final HashMap<String, ContentBody> parts = new HashMap<String, ContentBody>();

    /**
     * base constructor.
     * 
     * @param mode
     *            mode
     * @param listener
     *            listener monitoring the writing progress of the MultipartMime
     */
    public MultipartEntityWithProgressListener(final HttpMultipartMode mode) {
        super(mode);
    }

    @Override
    public void addPart(String name, ContentBody contentBody) {
        parts.put(name, contentBody);
    }

    public ContentBody getPart(String name) {
        return parts.get(name);
    }

    /**
     * Method to put all parts in to the multipart entity.
     * 
     * @return
     * @throws ParseException
     */
    public void prepareParts() {
        for (Map.Entry<String, ContentBody> entry : parts.entrySet()) {
            super.addPart(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Set upload listener.
     * 
     * @param listener
     *            upload listener
     */
    public void setListener(IFileTransferListener listener) {
        this.mListener = listener;
    }

    /**
     * Set the threshold time for progress updating. This is the minimum time between two progress messages being sent out. If this method is not called, the
     * default value is {@link #ON_PROGRESS_UPDATE_THRESHOLD}
     * 
     * @param threshold
     *            threshold
     */
    public static void setOnProgressUpdateThreshold(final int threshold) {
        onProgressUpdateThreshold = threshold;
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        if (mCountingOutputStream == null) {
            mCountingOutputStream = new CountingOutputStream(outstream, mListener);
        }
        super.writeTo(mCountingOutputStream);
        if (mListener != null) {
            mListener.onProgress(mCountingOutputStream.getBytesTransferred());
        }
    }

    /**
     * FilterOutputStream that fires progress callbacks so we can monitor upload progress.
     */
    private static class CountingOutputStream extends FilterOutputStream {

        /**
         * progress listener.
         */
        private final IFileTransferListener mProgresslistener;
        /**
         * number of bytes transferred so far.
         */
        private long bytesBransferred;
        /**
         * last timestamp of progress listener being fired.
         */
        private long lastOnProgressPost = 0;

        /**
         * constructor that also takes a progress listener.
         * 
         * @param out
         *            output stream
         * @param progressListener
         *            progress listener
         */
        public CountingOutputStream(final OutputStream out, final IFileTransferListener progressListener) {
            super(out);
            mProgresslistener = progressListener;
            bytesBransferred = 0;
        }

        @Override
        public void write(final byte[] buffer, final int offset, final int length) throws IOException {
            try {
                out.write(buffer, offset, length);
            }
            catch (IOException e) {
                if (mProgresslistener != null) {
                    mProgresslistener.onIOException(e);
                }
            }
            bytesBransferred += length;
            long currTime = (new Date()).getTime();
            if (mProgresslistener != null && currTime - lastOnProgressPost > onProgressUpdateThreshold) {
                lastOnProgressPost = currTime;
                mProgresslistener.onProgress(bytesBransferred);
            }
            // Allow canceling of downloads through thread interrupt.
            if (Thread.currentThread().isInterrupted() && mProgresslistener != null) {
                mProgresslistener.onCanceled();
                throw new InterruptedMultipartException();
            }
        }

        /**
         * Get the number of bytes transferred so far.
         * 
         * @return Number of bytes transferred so far.
         */
        public long getBytesTransferred() {
            return bytesBransferred;
        }
    }

    public static class InterruptedMultipartException extends IOException {

        private static final long serialVersionUID = 1L;
    }
}
