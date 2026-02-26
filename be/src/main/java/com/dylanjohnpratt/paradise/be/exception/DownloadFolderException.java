package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a download is attempted on a folder instead of a file.
 */
public class DownloadFolderException extends RuntimeException {

    public DownloadFolderException(String message) {
        super(message);
    }
}
