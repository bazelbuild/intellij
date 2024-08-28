package com.google.idea.blaze.base.command.mod;

import com.google.idea.blaze.exception.BuildException;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class BlazeModException extends BuildException {
    public BlazeModException(String message) {
        super(message);
    }

    public BlazeModException(String message, Throwable cause) {
        super(message, cause);
    }
}
