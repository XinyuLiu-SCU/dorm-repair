package com.dormrepair.exception;

public class WorkflowConflictException extends RuntimeException {
    public WorkflowConflictException(String message) { super(message); }
    public WorkflowConflictException(String message, Throwable cause) { super(message, cause); }
}
